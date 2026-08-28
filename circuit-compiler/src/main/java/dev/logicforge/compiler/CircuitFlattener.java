package dev.logicforge.compiler;

import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitMetadata;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.PortSlice;
import dev.logicforge.circuit.document.SubcircuitSupport;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Recursively expands project-backed subcircuit instances into one flat document. */
public final class CircuitFlattener {

    private CircuitFlattener() {
    }

    public static CircuitDocument flatten(CircuitProject project, String circuitName) {
        return flattenWithMap(project, circuitName).document();
    }

    public static FlatteningResult flattenWithMap(CircuitProject project, String circuitName) {
        return new Run(project, circuitName).run();
    }

    public static String endpointPath(String circuitName, PortEndpoint endpoint) {
        return circuitName + "/" + endpoint.componentId() + "." + endpointSuffix(endpoint);
    }

    private static final class Run {

        private final CircuitProject project;
        private final String rootCircuit;
        private final CircuitDocument flat;
        private final Map<Node, Node> parent = new LinkedHashMap<>();
        private final Map<ComponentKey, ComponentInstance> primitiveByKey = new HashMap<>();
        private final Map<ComponentKey, Map<String, InterfaceBinding>> subcircuitPorts = new HashMap<>();
        private final Set<ComponentKey> interfaceComponents = new java.util.HashSet<>();
        private final Map<ComponentKey, InterfaceBinding> interfaceByComponent = new HashMap<>();
        private final Map<InterfaceBinding, List<EndpointTarget>> interfaceAdapters = new HashMap<>();
        private final Map<Node, dev.logicforge.circuit.document.ElectricalEndpoint> flatEndpointByNode = new LinkedHashMap<>();
        private final Map<UUID, Node> rootConnectionNode = new LinkedHashMap<>();
        private final Map<String, UUID> componentUuidByPath = new LinkedHashMap<>();
        private final Map<String, PortEndpoint> flatEndpointByPath = new LinkedHashMap<>();
        private final Deque<String> circuitStack = new ArrayDeque<>();

        Run(CircuitProject project, String rootCircuit) {
            this.project = project;
            this.rootCircuit = rootCircuit;
            CircuitDocument source = project.circuit(rootCircuit).orElseThrow(() ->
                    new IllegalArgumentException("Project has no circuit '" + rootCircuit + "'"));
            this.flat = new CircuitDocument(new CircuitMetadata(
                    source.metadata().name(), source.metadata().description()));
        }

        FlatteningResult run() {
            visit(rootCircuit, rootCircuit, true);
            emitConnections();
            return new FlatteningResult(flat, componentUuidByPath, flatEndpointByPath);
        }

        private Map<String, InterfaceBinding> visit(String circuitName, String path, boolean root) {
            if (circuitStack.contains(circuitName)) {
                throw new IllegalArgumentException("Recursive subcircuit cycle: "
                        + String.join(" -> ", circuitStack) + " -> " + circuitName);
            }
            CircuitDocument document = project.circuit(circuitName).orElseThrow(() ->
                    new IllegalArgumentException("Unknown child circuit '" + circuitName + "'"));
            circuitStack.addLast(circuitName);

            Map<String, InterfaceBinding> interfaces = new LinkedHashMap<>();
            for (SubcircuitSupport.InterfacePort port : SubcircuitSupport.interfacePorts(document)) {
                ComponentKey key = new ComponentKey(path, port.componentId());
                interfaceComponents.add(key);
                InterfaceBinding binding = new InterfaceBinding(path, port.componentId(),
                        port.internalPortName(), port.width().bits());
                interfaces.put(port.name(), binding);
                interfaceByComponent.put(key, binding);
                interfaceNode(binding, PortSlice.Whole.INSTANCE);
            }

            for (ComponentInstance component : document.components()) {
                ComponentKey key = new ComponentKey(path, component.id());
                if (interfaceComponents.contains(key)) {
                    continue;
                }
                if (SubcircuitSupport.isInstanceDefinition(component.definitionId())) {
                    String childName = SubcircuitSupport.circuitName(component.definitionId());
                    String childPath = path + "/" + component.id();
                    Map<String, InterfaceBinding> childPorts = visit(childName, childPath, false);
                    subcircuitPorts.put(key, childPorts);
                    for (Map.Entry<String, InterfaceBinding> port : childPorts.entrySet()) {
                        union(node(path, component.id(), port.getKey(), PortSlice.Whole.INSTANCE),
                                interfaceNode(port.getValue(), PortSlice.Whole.INSTANCE));
                    }
                } else {
                    UUID flatId = root ? component.id() : deterministicId(path, component.id());
                    ComponentInstance clone = component.withId(flatId);
                    primitiveByKey.put(key, clone);
                    componentUuidByPath.put(path + "/" + component.id(), flatId);
                    flat.addComponent(clone);
                }
            }
            
            for (dev.logicforge.circuit.chip.ChipInstance chip : document.chips()) {
                UUID flatId = root ? chip.id() : deterministicId(path, chip.id());
                dev.logicforge.circuit.chip.ChipInstance clone = new dev.logicforge.circuit.chip.ChipInstance(
                        flatId, chip.chipDefinitionId(), chip.position(), chip.rotation(),
                        chip.referenceDesignator(), chip.displayMode()
                );
                // We should also store it in a map if we want to trace back, but the compiler does not trace back chips yet.
                // Wait, componentUuidByPath is used for source mapping. Let's add it.
                componentUuidByPath.put(path + "/" + chip.id(), flatId);
                flat.addChip(clone);
            }

            for (Connection connection : document.connections()) {
                dev.logicforge.circuit.document.ElectricalEndpoint fromEE = connection.from();
                dev.logicforge.circuit.document.ElectricalEndpoint toEE = connection.to();
                
                if (fromEE instanceof dev.logicforge.circuit.document.ElectricalEndpoint.ComponentEndpoint fromComp &&
                    toEE instanceof dev.logicforge.circuit.document.ElectricalEndpoint.ComponentEndpoint toComp) {
                    registerInterfaceAdapter(path, fromComp.port(), toComp.port());
                    registerInterfaceAdapter(path, toComp.port(), fromComp.port());
                }
                
                Node from = endpointNode(path, fromEE, root);
                Node to = endpointNode(path, toEE, root);
                union(from, to);
                if (root) {
                    rootConnectionNode.put(connection.id(), from);
                }
            }
            circuitStack.removeLast();
            return interfaces;
        }

        private Node endpointNode(String path, dev.logicforge.circuit.document.ElectricalEndpoint ee, boolean root) {
            if (ee instanceof dev.logicforge.circuit.document.ElectricalEndpoint.ChipPinEndpoint chipPin) {
                Node node = new Node(path, chipPin.chipInstanceId(), "pin_" + chipPin.physicalPinNumber(), PortSlice.Whole.INSTANCE);
                parent.putIfAbsent(node, node);
                UUID flatId = root ? chipPin.chipInstanceId() : deterministicId(path, chipPin.chipInstanceId());
                flatEndpointByNode.putIfAbsent(node, new dev.logicforge.circuit.document.ElectricalEndpoint.ChipPinEndpoint(flatId, chipPin.physicalPinNumber()));
                return node;
            }
            
            PortEndpoint endpoint = ((dev.logicforge.circuit.document.ElectricalEndpoint.ComponentEndpoint) ee).port();
            ComponentKey key = new ComponentKey(path, endpoint.componentId());
            if (interfaceComponents.contains(key)) {
                return interfaceNode(interfaceByComponent.get(key), endpoint.slice());
            }
            Map<String, InterfaceBinding> childPorts = subcircuitPorts.get(key);
            if (childPorts != null) {
                InterfaceBinding port = childPorts.get(endpoint.portName());
                if (port == null) {
                    throw new IllegalArgumentException("Subcircuit has no interface port '"
                            + endpoint.portName() + "'");
                }
                validateSlice(endpoint.slice(), port.width(), endpoint.toString());
                Node parentNode = node(path, endpoint.componentId(), endpoint.portName(), endpoint.slice());
                union(parentNode, interfaceNode(port, endpoint.slice()));
                return parentNode;
            }

            ComponentInstance primitive = primitiveByKey.get(key);
            Node node = node(path, endpoint.componentId(), endpoint.portName(), endpoint.slice());
            if (primitive != null) {
                flatEndpointByNode.putIfAbsent(node, new dev.logicforge.circuit.document.ElectricalEndpoint.ComponentEndpoint(new PortEndpoint(
                        new PortReference(primitive.id(), endpoint.portName()), endpoint.slice())));
            }
            return node;
        }

        /**
         * Remembers the common interface-to-internal-bus pattern. If a parent later binds
         * only a bit/range, the same slice is projected onto that internal endpoint.
         */
        private void registerInterfaceAdapter(String path, PortEndpoint possibleInterface,
                                              PortEndpoint other) {
            InterfaceBinding binding = interfaceByComponent.get(
                    new ComponentKey(path, possibleInterface.componentId()));
            if (binding == null || !possibleInterface.isWhole()) {
                return;
            }
            interfaceAdapters.computeIfAbsent(binding, ignored -> new ArrayList<>())
                    .add(new EndpointTarget(path, other));
        }

        private Node interfaceNode(InterfaceBinding binding, PortSlice slice) {
            validateSlice(slice, binding.width(), binding.internalPortName());
            Node result = node(binding.path(), binding.componentId(),
                    binding.internalPortName(), slice);
            if (!(slice instanceof PortSlice.Whole)) {
                for (EndpointTarget target : interfaceAdapters.getOrDefault(binding, List.of())) {
                    PortEndpoint projected = new PortEndpoint(target.endpoint().port(), slice);
                    union(result, endpointNode(target.path(), new dev.logicforge.circuit.document.ElectricalEndpoint.ComponentEndpoint(projected), false));
                }
            }
            return result;
        }

        private static void validateSlice(PortSlice slice, int width, String endpoint) {
            if (slice instanceof PortSlice.Bit bit && bit.index() >= width) {
                throw new IllegalArgumentException("Bit " + bit.index()
                        + " is outside hierarchy interface " + endpoint);
            }
            if (slice instanceof PortSlice.Range range && range.msb() >= width) {
                throw new IllegalArgumentException("Range " + range.msb() + ":" + range.lsb()
                        + " is outside hierarchy interface " + endpoint);
            }
        }

        private void emitConnections() {
            Map<Node, LinkedHashSet<PortEndpoint>> endpointsByGroup = new LinkedHashMap<>();
            for (Map.Entry<Node, dev.logicforge.circuit.document.ElectricalEndpoint> entry : flatEndpointByNode.entrySet()) {
                endpointsByGroup.computeIfAbsent(find(entry.getKey()), ignored -> new LinkedHashSet<>())
                        .add(entry.getValue());
            }
            Map<Node, List<UUID>> rootIdsByGroup = new LinkedHashMap<>();
            rootConnectionNode.forEach((id, node) -> rootIdsByGroup
                    .computeIfAbsent(find(node), ignored -> new ArrayList<>()).add(id));

            int groupIndex = 0;
            for (Map.Entry<Node, LinkedHashSet<PortEndpoint>> entry : endpointsByGroup.entrySet()) {
                List<PortEndpoint> endpoints = List.copyOf(entry.getValue());
                PortEndpoint representative = endpoints.get(0);
                for (Node node : parent.keySet()) {
                    if (find(node).equals(entry.getKey())) {
                        flatEndpointByPath.put(nodePath(node), representative);
                    }
                }
                if (endpoints.size() < 2) {
                    groupIndex++;
                    continue;
                }
                List<UUID> rootIds = rootIdsByGroup.getOrDefault(entry.getKey(), List.of());
                UUID firstId = rootIds.isEmpty()
                        ? generatedConnectionId(groupIndex, 1) : rootIds.get(0);
                dev.logicforge.circuit.document.ElectricalEndpoint p0 = new dev.logicforge.circuit.document.ElectricalEndpoint.ComponentEndpoint(endpoints.get(0));
                dev.logicforge.circuit.document.ElectricalEndpoint p1 = new dev.logicforge.circuit.document.ElectricalEndpoint.ComponentEndpoint(endpoints.get(1));
                flat.addConnection(new Connection(firstId, p0, p1, List.of()));
                for (int i = 1; i < rootIds.size(); i++) {
                    flat.addConnection(new Connection(rootIds.get(i), p0, p1, List.of()));
                }
                for (int i = 2; i < endpoints.size(); i++) {
                    flat.addConnection(new Connection(generatedConnectionId(groupIndex, i),
                            p0, new dev.logicforge.circuit.document.ElectricalEndpoint.ComponentEndpoint(endpoints.get(i)), List.of()));
                }
                groupIndex++;
            }
        }

        private Node node(String path, UUID componentId, String portName, PortSlice slice) {
            Node node = new Node(path, componentId, portName, slice);
            parent.putIfAbsent(node, node);
            return node;
        }

        private Node find(Node node) {
            parent.putIfAbsent(node, node);
            Node current = node;
            while (!parent.get(current).equals(current)) {
                current = parent.get(current);
            }
            Node root = current;
            current = node;
            while (!parent.get(current).equals(current)) {
                Node next = parent.get(current);
                parent.put(current, root);
                current = next;
            }
            return root;
        }

        private void union(Node left, Node right) {
            Node a = find(left);
            Node b = find(right);
            if (!a.equals(b)) {
                parent.put(b, a);
            }
        }

        private UUID generatedConnectionId(int group, int index) {
            return UUID.nameUUIDFromBytes((rootCircuit + ":net:" + group + ":" + index)
                    .getBytes(StandardCharsets.UTF_8));
        }

        private static UUID deterministicId(String path, UUID original) {
            return UUID.nameUUIDFromBytes((path + ":" + original)
                    .getBytes(StandardCharsets.UTF_8));
        }
    }

    private record ComponentKey(String path, UUID componentId) {
    }

    private record InterfaceBinding(String path, UUID componentId, String internalPortName,
                                    int width) {
    }

    private record EndpointTarget(String path, PortEndpoint endpoint) {
    }

    private record Node(String path, UUID componentId, String portName, PortSlice slice) {
    }

    public record FlatteningResult(
            CircuitDocument document,
            Map<String, UUID> flattenedComponentUuidByPath,
            Map<String, PortEndpoint> flattenedEndpointByPath) {
        public FlatteningResult {
            flattenedComponentUuidByPath = Map.copyOf(flattenedComponentUuidByPath);
            flattenedEndpointByPath = Map.copyOf(flattenedEndpointByPath);
        }
    }

    private static String nodePath(Node node) {
        return node.path() + "/" + node.componentId() + "."
                + endpointSuffix(new PortEndpoint(
                new PortReference(node.componentId(), node.portName()), node.slice()));
    }

    private static String endpointSuffix(PortEndpoint endpoint) {
        return switch (endpoint.slice()) {
            case PortSlice.Whole ignored -> endpoint.portName();
            case PortSlice.Bit bit -> endpoint.portName() + "[" + bit.index() + "]";
            case PortSlice.Range range -> endpoint.portName() + "[" + range.msb()
                    + ":" + range.lsb() + "]";
        };
    }
}
