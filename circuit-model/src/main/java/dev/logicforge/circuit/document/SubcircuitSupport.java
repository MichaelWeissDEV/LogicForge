package dev.logicforge.circuit.document;

import dev.logicforge.circuit.component.ComponentCategory;
import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.component.ParameterSpec;
import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.component.PortDirection;
import dev.logicforge.circuit.component.PortSpec;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.CircuitSize;
import dev.logicforge.circuit.geometry.PortSide;
import dev.logicforge.logic.BitWidth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Shared document conventions for child-circuit interfaces and parent instances. */
public final class SubcircuitSupport {

    public static final String INPUT_DEFINITION_ID = "hierarchy.input";
    public static final String OUTPUT_DEFINITION_ID = "hierarchy.output";
    public static final String INOUT_DEFINITION_ID = "hierarchy.inout";
    public static final String INSTANCE_PREFIX = "subcircuit:";
    public static final ParameterSpec.StringParameter INTERFACE_NAME =
            new ParameterSpec.StringParameter("name", "Port Name", "SIGNAL");
    public static final ParameterSpec.IntegerParameter INTERFACE_WIDTH =
            new ParameterSpec.IntegerParameter("width", "Width", 1, 1, BitWidth.MAX_BITS);

    private SubcircuitSupport() {
    }

    public static String definitionId(String circuitName) {
        if (circuitName == null || circuitName.isBlank()) {
            throw new IllegalArgumentException("A subcircuit instance needs a circuit name");
        }
        return INSTANCE_PREFIX + circuitName;
    }

    public static boolean isInstanceDefinition(String definitionId) {
        return definitionId != null && definitionId.startsWith(INSTANCE_PREFIX)
                && definitionId.length() > INSTANCE_PREFIX.length();
    }

    public static String circuitName(String definitionId) {
        if (!isInstanceDefinition(definitionId)) {
            throw new IllegalArgumentException("Not a subcircuit definition: " + definitionId);
        }
        return definitionId.substring(INSTANCE_PREFIX.length());
    }

    public static ComponentInstance instantiate(String circuitName, CircuitPoint position) {
        return ComponentInstance.create(definitionId(circuitName), position, ParameterValues.empty());
    }

    /** External ports declared by a child document, in document order. */
    public static List<InterfacePort> interfacePorts(CircuitDocument child) {
        Map<String, InterfacePort> ports = new LinkedHashMap<>();
        for (ComponentInstance component : child.components()) {
            PortDirection direction;
            String internalPort;
            if (component.definitionId().equals(INPUT_DEFINITION_ID)) {
                direction = PortDirection.INPUT;
                internalPort = "OUT";
            } else if (component.definitionId().equals(OUTPUT_DEFINITION_ID)) {
                direction = PortDirection.OUTPUT;
                internalPort = "IN";
            } else if (component.definitionId().equals(INOUT_DEFINITION_ID)) {
                direction = PortDirection.INOUT;
                internalPort = "BUS";
            } else {
                continue;
            }
            String name = component.parameters().get(INTERFACE_NAME).trim();
            if (name.isBlank()) {
                throw new IllegalArgumentException("Subcircuit interface names cannot be blank");
            }
            InterfacePort port = new InterfacePort(name,
                    BitWidth.of(component.parameters().getInt(INTERFACE_WIDTH)), direction,
                    component.id(), internalPort);
            if (ports.putIfAbsent(name, port) != null) {
                throw new IllegalArgumentException("Duplicate subcircuit interface port '" + name + "'");
            }
        }
        return List.copyOf(ports.values());
    }

    public static Optional<ComponentDefinition> definition(CircuitProject project, String definitionId) {
        if (!isInstanceDefinition(definitionId)) {
            return Optional.empty();
        }
        String name = circuitName(definitionId);
        return project.circuit(name).map(child -> definitionFor(name, child));
    }

    public static ComponentDefinition definitionFor(String circuitName, CircuitDocument child) {
        List<InterfacePort> interfaces = interfacePorts(child);
        return new ComponentDefinition() {
            @Override public String id() { return definitionId(circuitName); }
            @Override public String displayName() { return circuitName; }
            @Override public ComponentCategory category() { return ComponentCategory.HIERARCHY; }
            @Override public String description() { return "Reusable circuit " + circuitName; }
            @Override public List<ParameterSpec<?>> parameters() { return List.of(); }

            @Override
            public List<PortSpec> ports(ParameterValues values) {
                List<InterfacePort> inputs = interfaces.stream()
                        .filter(port -> port.direction() == PortDirection.INPUT).toList();
                List<InterfacePort> outputs = interfaces.stream()
                        .filter(port -> port.direction() == PortDirection.OUTPUT).toList();
                List<InterfacePort> inouts = interfaces.stream()
                        .filter(port -> port.direction() == PortDirection.INOUT).toList();
                CircuitSize size = bodySize(values);
                List<PortSpec> result = new ArrayList<>(interfaces.size());
                addPorts(result, inputs, size, PortSide.LEFT);
                List<InterfacePort> right = new ArrayList<>(inouts);
                right.addAll(outputs);
                addPorts(result, right, size, PortSide.RIGHT);
                return result;
            }

            @Override
            public CircuitSize bodySize(ParameterValues values) {
                long inputs = interfaces.stream().filter(p -> p.direction() == PortDirection.INPUT).count();
                long outputs = interfaces.size() - inputs;
                return new CircuitSize(88, Math.max(48, Math.max(inputs, outputs) * 20 + 20));
            }

            @Override public List<String> searchKeywords() {
                return List.of("subcircuit", "hierarchy", "custom chip", circuitName);
            }
        };
    }

    private static void addPorts(List<PortSpec> result, List<InterfacePort> ports,
                                 CircuitSize size, PortSide side) {
        for (int i = 0; i < ports.size(); i++) {
            InterfacePort port = ports.get(i);
            double y = (i - (ports.size() - 1) / 2.0) * 20;
            double x = side == PortSide.LEFT ? -size.halfWidth() - 8 : size.halfWidth() + 8;
            result.add(new PortSpec(port.name(), port.direction(), port.width(),
                    new CircuitPoint(x, y), side, switch (port.direction()) {
                        case INPUT -> "Signal enters child from parent";
                        case OUTPUT -> "Signal leaves child to parent";
                        case INOUT -> "Bidirectional signal shared with parent";
                    }));
        }
    }

    public record InterfacePort(String name, BitWidth width, PortDirection direction,
                                UUID componentId, String internalPortName) {
    }
}
