package dev.logicforge.circuit.component;

import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.PortSide;
import dev.logicforge.logic.BitWidth;

/**
 * One port of a component, as declared by its {@link ComponentDefinition}.
 *
 * <p>The port carries its own geometry: {@code anchor} is the position of the connection
 * point in component-local coordinates (origin at the body centre, unrotated). Keeping
 * that here rather than in the renderer means the wire router, hit testing and the
 * renderer all agree on where a port is, in every rotation.
 */
public record PortSpec(
        String name,
        PortDirection direction,
        BitWidth width,
        CircuitPoint anchor,
        PortSide side,
        String description) {

    public PortSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A port needs a name");
        }
        description = description == null ? "" : description;
    }

    public static PortSpec of(String name, PortDirection direction, CircuitPoint anchor, PortSide side) {
        return new PortSpec(name, direction, BitWidth.ONE, anchor, side, "");
    }
}
