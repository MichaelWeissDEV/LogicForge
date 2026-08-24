package dev.logicforge.circuit.document;

import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.component.PortSpec;
import dev.logicforge.circuit.geometry.CircuitBounds;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.CircuitSize;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns a placed component plus its definition into world-space geometry.
 *
 * <p>This is the single place where rotation is applied. The renderer, the hit testing and
 * the wire router all call it, so a rotated gate can never end up drawn in one place and
 * wired in another.
 */
public final class ComponentGeometry {

    private ComponentGeometry() {
    }

    /** The component's body rectangle in world coordinates, rotation included. */
    public static CircuitBounds bodyBounds(ComponentInstance instance, ComponentDefinition definition) {
        CircuitSize size = instance.rotation().apply(definition.bodySize(instance.parameters()));
        return CircuitBounds.around(instance.position(), size);
    }

    /** The world position of one port. */
    public static CircuitPoint portPosition(ComponentInstance instance, PortSpec port) {
        return instance.position().plus(instance.rotation().apply(port.anchor()));
    }

    /** All ports of the component, resolved into world coordinates. */
    public static List<PlacedPort> ports(ComponentInstance instance, ComponentDefinition definition) {
        List<PortSpec> specs = definition.ports(instance.parameters());
        List<PlacedPort> placed = new ArrayList<>(specs.size());
        for (PortSpec spec : specs) {
            placed.add(new PlacedPort(
                    new PortReference(instance.id(), spec.name()),
                    spec,
                    portPosition(instance, spec),
                    spec.side().rotatedBy(instance.rotation())));
        }
        return placed;
    }

    public static Optional<PlacedPort> port(ComponentInstance instance, ComponentDefinition definition,
                                            String portName) {
        return ports(instance, definition).stream()
                .filter(port -> port.spec().name().equals(portName))
                .findFirst();
    }

    /**
     * The bounds a component occupies including its port stubs — used for selection
     * rectangles and for keeping wires out of component bodies.
     */
    public static CircuitBounds outerBounds(ComponentInstance instance, ComponentDefinition definition) {
        CircuitBounds bounds = bodyBounds(instance, definition);
        for (PlacedPort port : ports(instance, definition)) {
            bounds = bounds.union(new CircuitBounds(port.position().x(), port.position().y(), 0, 0));
        }
        return bounds;
    }
}
