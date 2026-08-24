package dev.logicforge.compiler;

import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.component.PortDirection;
import dev.logicforge.circuit.component.PortSpec;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.ComponentType;
import dev.logicforge.logic.BitWidth;
import dev.logicforge.simulation.CompiledCircuit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns the circuit the user edited into the flat structure the simulator runs.
 *
 * <p>What it does, in order:
 *
 * <ol>
 *   <li>resolves every component against the registry and collects its ports
 *   <li>checks that every wire refers to ports that exist
 *   <li>merges ports connected by wires into nets, giving every unconnected port a net of
 *       its own so a floating input is a real (undriven) net rather than a special case
 *   <li>checks that all ports of a net agree on their width
 *   <li>records which ports drive a net and which read it
 *   <li>assigns compact runtime ids and builds the {@link CompiledCircuit}
 *   <li>keeps a {@link CircuitSourceMap} so the editor can find its way back
 * </ol>
 *
 * <p>Ids are handed out in document order, so compiling the same circuit twice produces
 * exactly the same runtime structure.
 */
public final class CircuitCompiler {

    private final ComponentRegistry registry;

    public CircuitCompiler(ComponentRegistry registry) {
        this.registry = registry;
    }

    /**
     * Reports everything the compiler would notice, without handing out a runnable
     * circuit. Errors short-circuit the analysis, since net forming needs valid ports.
     */
    public List<ValidationIssue> validate(CircuitDocument document) {
        Compilation compilation = new Compilation(document).analyse();
        if (compilation.issues.stream().noneMatch(ValidationIssue::isError)) {
            compilation.build();
        }
        return List.copyOf(compilation.issues);
    }

    /**
     * Compiles a circuit.
     *
     * @throws CircuitCompileException if the circuit contains errors
     */
    public CompilationResult compile(CircuitDocument document) {
        Compilation compilation = new Compilation(document).analyse();
        if (compilation.issues.stream().anyMatch(ValidationIssue::isError)) {
            throw new CircuitCompileException(compilation.issues);
        }
        return compilation.build();
    }

    /** One compilation run. Everything it needs is local, so the compiler is reusable. */
    private final class Compilation {

        private final CircuitDocument document;
        private final List<ValidationIssue> issues = new ArrayList<>();

        /** Ports of all valid components, in document order; the index is the DSU element. */
        private final Map<PortReference, Integer> portIndex = new LinkedHashMap<>();
        private final List<PortSpec> portSpecs = new ArrayList<>();
        private final List<ComponentInstance> instances = new ArrayList<>();
        private final List<ComponentType> types = new ArrayList<>();
        private final List<Connection> mergedConnections = new ArrayList<>();
        private int[] parent = new int[0];

        Compilation(CircuitDocument document) {
            this.document = document;
        }

        Compilation analyse() {
            collectComponents();
            parent = new int[portIndex.size()];
            for (int i = 0; i < parent.length; i++) {
                parent[i] = i;
            }
            mergeConnectedPorts();
            return this;
        }

        private void collectComponents() {
            for (ComponentInstance instance : document.components()) {
                Optional<ComponentType> type = registry.find(instance.definitionId());
                if (type.isEmpty()) {
                    issues.add(ValidationIssue.error(
                            "Unknown component type '" + instance.definitionId() + "'", instance.id(), null));
                    continue;
                }
                instances.add(instance);
                types.add(type.get());
                for (PortSpec port : type.get().definition().ports(instance.parameters())) {
                    PortReference reference = new PortReference(instance.id(), port.name());
                    if (portIndex.putIfAbsent(reference, portIndex.size()) == null) {
                        portSpecs.add(port);
                    } else {
                        issues.add(ValidationIssue.error("Duplicate port name '" + port.name() + "'",
                                instance.id(), port.name()));
                    }
                }
            }
        }

        private void mergeConnectedPorts() {
            for (Connection connection : document.connections()) {
                Integer from = portIndex.get(connection.from());
                Integer to = portIndex.get(connection.to());
                if (from == null || to == null) {
                    issues.add(ValidationIssue.forConnection(ValidationIssue.Severity.WARNING,
                            "Wire refers to a port that no longer exists and is ignored", connection.id()));
                    continue;
                }
                PortDirection fromDirection = portSpecs.get(from).direction();
                PortDirection toDirection = portSpecs.get(to).direction();
                if (fromDirection == PortDirection.OUTPUT && toDirection == PortDirection.OUTPUT) {
                    issues.add(ValidationIssue.forConnection(ValidationIssue.Severity.WARNING,
                            "Two outputs drive the same net; this is only meaningful with tri-state drivers",
                            connection.id()));
                }
                union(from, to);
                mergedConnections.add(connection);
            }
        }

        CompilationResult build() {
            // Group ports into nets, keeping the order in which they first appear.
            Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
            for (int port = 0; port < parent.length; port++) {
                groups.computeIfAbsent(find(port), key -> new ArrayList<>()).add(port);
            }

            CompiledCircuit.Builder builder = CompiledCircuit.builder();
            int[] netOfPort = new int[parent.length];
            List<List<PortReference>> portsByNet = new ArrayList<>();
            List<PortReference> portsByIndex = List.copyOf(portIndex.keySet());

            for (List<Integer> group : groups.values()) {
                BitWidth width = widthOf(group, portsByIndex);
                int netId = builder.addNet(width);
                List<PortReference> netPorts = new ArrayList<>(group.size());
                for (int port : group) {
                    netOfPort[port] = netId;
                    netPorts.add(portsByIndex.get(port));
                }
                portsByNet.add(netPorts);
                reportNetShape(netId, group);
            }

            Map<UUID, Integer> componentIdByUuid = new LinkedHashMap<>();
            List<UUID> uuidByComponentId = new ArrayList<>();
            for (int i = 0; i < instances.size(); i++) {
                ComponentInstance instance = instances.get(i);
                ComponentType type = types.get(i);
                ComponentDefinition definition = type.definition();
                List<PortSpec> ports = definition.ports(instance.parameters());

                List<Integer> inputs = new ArrayList<>();
                List<Integer> outputs = new ArrayList<>();
                for (PortSpec port : ports) {
                    int net = netOfPort[portIndex.get(new PortReference(instance.id(), port.name()))];
                    if (port.direction().canRead()) {
                        inputs.add(net);
                    }
                    if (port.direction().canDrive()) {
                        outputs.add(net);
                    }
                }
                int componentId = builder.addComponent(instance.definitionId(), instance.label(),
                        type.behaviorFor(instance.parameters()), toArray(inputs), toArray(outputs));
                componentIdByUuid.put(instance.id(), componentId);
                uuidByComponentId.add(instance.id());
            }

            Map<PortReference, Integer> netByPort = new LinkedHashMap<>();
            for (int port = 0; port < netOfPort.length; port++) {
                netByPort.put(portsByIndex.get(port), netOfPort[port]);
            }
            Map<UUID, Integer> netByConnection = new LinkedHashMap<>();
            for (Connection connection : mergedConnections) {
                netByConnection.put(connection.id(), netOfPort[portIndex.get(connection.from())]);
            }

            CircuitSourceMap sourceMap = new CircuitSourceMap(componentIdByUuid, uuidByComponentId,
                    netByPort, portsByNet, netByConnection);
            return new CompilationResult(builder.build(), sourceMap, issues);
        }

        /** All ports of a net must agree on their width; LogicForge never truncates silently. */
        private BitWidth widthOf(List<Integer> group, List<PortReference> portsByIndex) {
            BitWidth width = portSpecs.get(group.get(0)).width();
            for (int port : group) {
                BitWidth other = portSpecs.get(port).width();
                if (!other.equals(width)) {
                    PortReference reference = portsByIndex.get(port);
                    issues.add(ValidationIssue.error("Port " + reference.portName() + " is " + other
                                    + " wide but shares a net with a " + width + " wide port",
                            reference.componentId(), reference.portName()));
                }
            }
            return width;
        }

        private void reportNetShape(int netId, List<Integer> group) {
            int drivers = 0;
            int consumers = 0;
            for (int port : group) {
                PortDirection direction = portSpecs.get(port).direction();
                if (direction.canDrive()) {
                    drivers++;
                }
                if (direction.canRead()) {
                    consumers++;
                }
            }
            if (drivers == 0 && consumers > 0) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.INFO,
                        "Net " + netId + " has no driver and reads as X", null, null, null));
            }
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
                // Keep the smaller index as the root so net numbering follows document order.
                if (rootA < rootB) {
                    parent[rootB] = rootA;
                } else {
                    parent[rootA] = rootB;
                }
            }
        }

        private int[] toArray(List<Integer> values) {
            int[] array = new int[values.size()];
            for (int i = 0; i < array.length; i++) {
                array[i] = values.get(i);
            }
            return array;
        }
    }
}
