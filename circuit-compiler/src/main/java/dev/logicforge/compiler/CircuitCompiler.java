package dev.logicforge.compiler;

import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.component.PortDirection;
import dev.logicforge.circuit.component.PortSpec;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.PortSlice;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.ComponentType;
import dev.logicforge.logic.BitWidth;
import dev.logicforge.simulation.CompiledCircuit;
import dev.logicforge.simulation.CompiledInputBinding;
import dev.logicforge.simulation.CompiledOutputBinding;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Compiles an editable document into the flat, endpoint-accurate simulation model. */
public final class CircuitCompiler {

    private final ComponentRegistry registry;

    public CircuitCompiler(ComponentRegistry registry) {
        this.registry = registry;
    }

    public List<ValidationIssue> validate(CircuitDocument document) {
        Compilation compilation = new Compilation(document);
        compilation.resolveComponents();
        if (!compilation.hasErrors()) {
            compilation.resolvePorts();
            compilation.resolveConnections();
        }
        if (!compilation.hasErrors()) {
            compilation.formNets();
            compilation.validateNetWidths();
            compilation.validateNetTopology();
        }
        return List.copyOf(compilation.issues);
    }

    public CompilationResult compile(CircuitDocument document) {
        Compilation compilation = new Compilation(document);
        compilation.resolveComponents();
        if (compilation.hasErrors()) {
            throw new CircuitCompileException(compilation.issues);
        }
        compilation.resolvePorts();
        compilation.resolveConnections();
        if (compilation.hasErrors()) {
            throw new CircuitCompileException(compilation.issues);
        }
        compilation.formNets();
        compilation.validateNetWidths();
        if (compilation.hasErrors()) {
            throw new CircuitCompileException(compilation.issues);
        }
        compilation.validateNetTopology();
        return compilation.emit();
    }

    /** Flattens and compiles one circuit from a multi-document project. */
    public CompilationResult compile(CircuitProject project, String circuitName) {
        try {
            ComponentRegistry projectRegistry = new ComponentRegistry();
            registry.all().forEach(projectRegistry::register);
            for (CircuitDocument child : project.circuits()) {
                var definition = dev.logicforge.circuit.document.SubcircuitSupport.definitionFor(
                        child.metadata().name(), child);
                projectRegistry.register(ComponentType.of(definition, context -> { }));
            }
            CircuitCompiler projectValidator = new CircuitCompiler(projectRegistry);
            List<ValidationIssue> projectIssues = new ArrayList<>();
            for (CircuitDocument child : project.circuits()) {
                projectIssues.addAll(projectValidator.validate(child));
            }
            if (projectIssues.stream().anyMatch(ValidationIssue::isError)) {
                throw new CircuitCompileException(projectIssues);
            }
            CircuitFlattener.FlatteningResult flattened =
                    CircuitFlattener.flattenWithMap(project, circuitName);
            CompilationResult result = compile(flattened.document());
            Map<String, Integer> components = new LinkedHashMap<>();
            flattened.flattenedComponentUuidByPath().forEach((path, uuid) ->
                    result.sourceMap().componentId(uuid).ifPresent(id -> components.put(path, id)));
            Map<String, Integer> nets = new LinkedHashMap<>();
            flattened.flattenedEndpointByPath().forEach((path, endpoint) ->
                    result.sourceMap().netOf(endpoint).ifPresent(id -> nets.put(path, id)));
            return new CompilationResult(result.circuit(), result.sourceMap(), result.issues(),
                    new HierarchySourceMap(components, nets));
        } catch (CircuitCompileException failure) {
            throw failure;
        } catch (IllegalArgumentException failure) {
            throw new CircuitCompileException(List.of(
                    ValidationIssue.error(failure.getMessage(), null, null)));
        }
    }

    /** One canonical compilation run. A DSU element is an effective electrical endpoint. */
    private final class Compilation {

        private final CircuitDocument document;
        private final List<ValidationIssue> issues = new ArrayList<>();
        private final List<ComponentInstance> instances = new ArrayList<>();
        private final List<ComponentType> types = new ArrayList<>();
        private final Map<PortReference, Integer> portIndex = new LinkedHashMap<>();
        private final List<PortReference> portsByIndex = new ArrayList<>();
        private final List<PortSpec> portSpecs = new ArrayList<>();
        private final Map<PortReference, Boolean> bitMode = new HashMap<>();
        private final List<Connection> mergedConnections = new ArrayList<>();
        private final Map<PortEndpoint, Integer> atomByEndpoint = new LinkedHashMap<>();
        private final List<PortEndpoint> endpointByAtom = new ArrayList<>();
        private final List<Integer> portByAtom = new ArrayList<>();
        private int[] parent = new int[0];

        Compilation(CircuitDocument document) {
            this.document = document;
        }

        private boolean hasErrors() {
            return issues.stream().anyMatch(ValidationIssue::isError);
        }

        private void resolveComponents() {
            for (ComponentInstance instance : document.components()) {
                Optional<ComponentType> type = registry.find(instance.definitionId());
                if (type.isEmpty()) {
                    issues.add(ValidationIssue.error(
                            "Unknown component type '" + instance.definitionId() + "'",
                            instance.id(), null));
                } else {
                    instances.add(instance);
                    types.add(type.get());
                }
            }
        }

        private void resolvePorts() {
            for (int i = 0; i < instances.size(); i++) {
                ComponentInstance instance = instances.get(i);
                for (PortSpec port : types.get(i).definition().ports(instance.parameters())) {
                    PortReference reference = new PortReference(instance.id(), port.name());
                    int index = portIndex.size();
                    if (portIndex.putIfAbsent(reference, index) != null) {
                        issues.add(ValidationIssue.error("Duplicate port name '" + port.name() + "'",
                                instance.id(), port.name()));
                    } else {
                        portsByIndex.add(reference);
                        portSpecs.add(port);
                    }
                }
            }
        }

        private void resolveConnections() {
            Set<PortReference> mixedReported = new HashSet<>();
            for (Connection connection : document.connections()) {
                Integer from = portIndex.get(connection.fromPort());
                Integer to = portIndex.get(connection.toPort());
                if (from == null || to == null) {
                    issues.add(ValidationIssue.forConnection(ValidationIssue.Severity.WARNING,
                            "Wire refers to a port that no longer exists and is ignored",
                            connection.id()));
                    continue;
                }

                boolean endpointsValid = validateEndpoint(connection.from(), from, mixedReported)
                        & validateEndpoint(connection.to(), to, mixedReported);
                if (!endpointsValid) {
                    continue;
                }

                int fromWidth = effectiveWidth(connection.from(), portSpecs.get(from));
                int toWidth = effectiveWidth(connection.to(), portSpecs.get(to));
                if (fromWidth != toWidth) {
                    issues.add(ValidationIssue.forConnection(ValidationIssue.Severity.ERROR,
                            "Connection endpoints are " + fromWidth + " and " + toWidth
                                    + " bits wide", connection.id()));
                    continue;
                }

                PortDirection fromDirection = portSpecs.get(from).direction();
                PortDirection toDirection = portSpecs.get(to).direction();
                if (fromDirection == PortDirection.OUTPUT && toDirection == PortDirection.OUTPUT) {
                    issues.add(ValidationIssue.forConnection(ValidationIssue.Severity.WARNING,
                            "Two outputs drive the same net; this is only meaningful with tri-state drivers",
                            connection.id()));
                }
                mergedConnections.add(connection);
            }
        }

        private boolean validateEndpoint(PortEndpoint endpoint, int index,
                                         Set<PortReference> mixedReported) {
            PortSpec spec = portSpecs.get(index);
            if (endpoint.slice() instanceof PortSlice.Bit bit && bit.index() >= spec.width().bits()) {
                issues.add(ValidationIssue.error(
                        "Bit " + bit.index() + " is outside " + endpoint.portName()
                                + "[" + (spec.width().bits() - 1) + ":0]",
                        endpoint.componentId(), endpoint.portName()));
                return false;
            }

            if (endpoint.slice() instanceof PortSlice.Range range
                    && range.msb() >= spec.width().bits()) {
                issues.add(ValidationIssue.error(
                        "Range " + range.msb() + ":" + range.lsb() + " is outside "
                                + endpoint.portName() + "[" + (spec.width().bits() - 1) + ":0]",
                        endpoint.componentId(), endpoint.portName()));
                return false;
            }

            boolean endpointPartialMode = !endpoint.isWhole();
            Boolean previous = bitMode.putIfAbsent(endpoint.port(), endpointPartialMode);
            if (previous != null && previous != endpointPartialMode) {
                if (mixedReported.add(endpoint.port())) {
                    issues.add(ValidationIssue.error(
                            "Port " + endpoint.portName()
                                    + " has both whole-port and bit-level/range connections",
                            endpoint.componentId(), endpoint.portName()));
                }
                return false;
            }
            return true;
        }

        private int effectiveWidth(PortEndpoint endpoint, PortSpec spec) {
            return endpoint.selectedWidth(spec.width().bits());
        }

        private void formNets() {
            for (int port = 0; port < portSpecs.size(); port++) {
                PortReference reference = portsByIndex.get(port);
                int width = portSpecs.get(port).width().bits();
                if (bitMode.getOrDefault(reference, false)) {
                    for (int bit = 0; bit < width; bit++) {
                        addAtom(PortEndpoint.bit(reference, bit), port);
                    }
                } else {
                    addAtom(PortEndpoint.whole(reference), port);
                }
            }
            parent = new int[endpointByAtom.size()];
            for (int atom = 0; atom < parent.length; atom++) {
                parent[atom] = atom;
            }
            for (Connection connection : mergedConnections) {
                int width = selectedWidth(connection.from());
                for (int offset = 0; offset < width; offset++) {
                    union(atomFor(connection.from(), offset), atomFor(connection.to(), offset));
                }
            }
        }

        private int selectedWidth(PortEndpoint endpoint) {
            return endpoint.selectedWidth(portSpecs.get(portIndex.get(endpoint.port())).width().bits());
        }

        /** Returns the atom for one LSB-relative bit of a whole, bit or range endpoint. */
        private int atomFor(PortEndpoint endpoint, int offset) {
            if (endpoint.isWhole()) {
                return atomByEndpoint.get(endpoint);
            }
            int bit = switch (endpoint.slice()) {
                case PortSlice.Bit selected -> selected.index();
                case PortSlice.Range range -> range.lsb() + offset;
                case PortSlice.Whole ignored -> throw new IllegalStateException("handled above");
            };
            return atomByEndpoint.get(PortEndpoint.bit(endpoint.port(), bit));
        }

        private void addAtom(PortEndpoint endpoint, int port) {
            int atom = endpointByAtom.size();
            atomByEndpoint.put(endpoint, atom);
            endpointByAtom.add(endpoint);
            portByAtom.add(port);
        }

        private Map<Integer, List<Integer>> groups() {
            Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
            for (int atom = 0; atom < parent.length; atom++) {
                groups.computeIfAbsent(find(atom), ignored -> new ArrayList<>()).add(atom);
            }
            return groups;
        }

        private BitWidth atomWidth(int atom) {
            PortEndpoint endpoint = endpointByAtom.get(atom);
            return endpoint.isBit() ? BitWidth.ONE : portSpecs.get(portByAtom.get(atom)).width();
        }

        private void validateNetWidths() {
            for (List<Integer> group : groups().values()) {
                BitWidth expected = atomWidth(group.get(0));
                for (int atom : group) {
                    BitWidth actual = atomWidth(atom);
                    if (!actual.equals(expected)) {
                        PortEndpoint endpoint = endpointByAtom.get(atom);
                        issues.add(ValidationIssue.error(
                                "Port endpoint " + endpoint + " is " + actual
                                        + " wide but shares a net with a " + expected + " wide endpoint",
                                endpoint.componentId(), endpoint.portName()));
                    }
                }
            }
        }

        private void validateNetTopology() {
            for (List<Integer> group : groups().values()) {
                int drivers = 0;
                int consumers = 0;
                for (int atom : group) {
                    PortDirection direction = portSpecs.get(portByAtom.get(atom)).direction();
                    drivers += direction.canDrive() ? 1 : 0;
                    consumers += direction.canRead() ? 1 : 0;
                }
                if (drivers == 0 && consumers > 0) {
                    PortEndpoint endpoint = endpointByAtom.get(group.get(0));
                    issues.add(ValidationIssue.info("Net has no driver and reads as X",
                            endpoint.componentId(), endpoint.portName()));
                }
            }
        }

        private CompilationResult emit() {
            Map<Integer, List<Integer>> groups = groups();
            CompiledCircuit.Builder builder = CompiledCircuit.builder();
            int[] netByAtom = new int[parent.length];
            List<List<PortEndpoint>> endpointsByNet = new ArrayList<>();
            List<List<PortReference>> portsByNet = new ArrayList<>();

            for (List<Integer> group : groups.values()) {
                int netId = builder.addNet(atomWidth(group.get(0)));
                List<PortEndpoint> endpoints = new ArrayList<>();
                List<PortReference> ports = new ArrayList<>();
                for (int atom : group) {
                    netByAtom[atom] = netId;
                    PortEndpoint endpoint = endpointByAtom.get(atom);
                    endpoints.add(endpoint);
                    ports.add(endpoint.port());
                }
                endpointsByNet.add(List.copyOf(endpoints));
                portsByNet.add(List.copyOf(ports));
            }

            Map<UUID, Integer> componentIdByUuid = new LinkedHashMap<>();
            List<UUID> uuidByComponentId = new ArrayList<>();
            for (int i = 0; i < instances.size(); i++) {
                ComponentInstance instance = instances.get(i);
                ComponentType type = types.get(i);
                ComponentDefinition definition = type.definition();
                List<CompiledInputBinding> inputs = new ArrayList<>();
                List<CompiledOutputBinding> outputs = new ArrayList<>();
                for (PortSpec port : definition.ports(instance.parameters())) {
                    PortReference reference = new PortReference(instance.id(), port.name());
                    if (port.direction().canRead()) {
                        inputs.add(inputBinding(reference, port, netByAtom));
                    }
                    if (port.direction().canDrive()) {
                        outputs.add(outputBinding(reference, port, netByAtom));
                    }
                }
                int componentId = builder.addComponent(instance.definitionId(), instance.label(),
                        type.behaviorFor(instance.parameters()),
                        inputs.toArray(CompiledInputBinding[]::new),
                        outputs.toArray(CompiledOutputBinding[]::new));
                componentIdByUuid.put(instance.id(), componentId);
                uuidByComponentId.add(instance.id());
            }

            Map<PortEndpoint, Integer> netByEndpoint = new LinkedHashMap<>();
            Map<PortReference, Integer> netByPort = new LinkedHashMap<>();
            for (int atom = 0; atom < endpointByAtom.size(); atom++) {
                PortEndpoint endpoint = endpointByAtom.get(atom);
                int netId = netByAtom[atom];
                netByEndpoint.put(endpoint, netId);
                if (endpoint.isWhole()) {
                    netByPort.put(endpoint.port(), netId);
                    int width = portSpecs.get(portByAtom.get(atom)).width().bits();
                    for (int bit = 0; bit < width; bit++) {
                        netByEndpoint.put(PortEndpoint.bit(endpoint.port(), bit), netId);
                    }
                }
            }
            Map<UUID, Integer> netByConnection = new LinkedHashMap<>();
            for (Connection connection : mergedConnections) {
                int firstAtom = atomFor(connection.from(), 0);
                int net = netByAtom[firstAtom];
                netByConnection.put(connection.id(), net);
                netByEndpoint.put(connection.from(), net);
                netByEndpoint.put(connection.to(), netByAtom[atomFor(connection.to(), 0)]);
            }

            CircuitSourceMap sourceMap = new CircuitSourceMap(componentIdByUuid, uuidByComponentId,
                    netByPort, portsByNet, netByConnection, netByEndpoint, endpointsByNet);
            return new CompilationResult(builder.build(), sourceMap, issues);
        }

        private CompiledInputBinding inputBinding(PortReference reference, PortSpec spec,
                                                  int[] netByAtom) {
            if (!bitMode.getOrDefault(reference, false)) {
                int net = netByAtom[atomByEndpoint.get(PortEndpoint.whole(reference))];
                return new CompiledInputBinding.Whole(net, spec.width());
            }
            return new CompiledInputBinding.Bits(bitNets(reference, spec.width().bits(), netByAtom));
        }

        private CompiledOutputBinding outputBinding(PortReference reference, PortSpec spec,
                                                    int[] netByAtom) {
            if (!bitMode.getOrDefault(reference, false)) {
                int net = netByAtom[atomByEndpoint.get(PortEndpoint.whole(reference))];
                return new CompiledOutputBinding.Whole(net, -1, spec.width().bits());
            }
            int[] nets = bitNets(reference, spec.width().bits(), netByAtom);
            int[] unassignedDrivers = new int[nets.length];
            java.util.Arrays.fill(unassignedDrivers, -1);
            return new CompiledOutputBinding.Bits(nets, unassignedDrivers);
        }

        private int[] bitNets(PortReference reference, int width, int[] netByAtom) {
            int[] nets = new int[width];
            for (int bit = 0; bit < width; bit++) {
                nets[bit] = netByAtom[atomByEndpoint.get(PortEndpoint.bit(reference, bit))];
            }
            return nets;
        }

        private int find(int element) {
            while (parent[element] != element) {
                parent[element] = parent[parent[element]];
                element = parent[element];
            }
            return element;
        }

        private void union(int a, int b) {
            int rootA = find(a);
            int rootB = find(b);
            if (rootA != rootB) {
                if (rootA < rootB) {
                    parent[rootB] = rootA;
                } else {
                    parent[rootA] = rootB;
                }
            }
        }
    }
}
