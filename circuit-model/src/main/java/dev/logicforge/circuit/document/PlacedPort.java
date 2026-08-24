package dev.logicforge.circuit.document;

import dev.logicforge.circuit.component.PortSpec;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.PortSide;

/** A port of a placed component, resolved into world coordinates. */
public record PlacedPort(PortReference reference, PortSpec spec, CircuitPoint position, PortSide side) {

    /** A short point away from the body, where a wire should leave this port. */
    public CircuitPoint stubEnd(double length) {
        CircuitPoint direction = side.outwards();
        return position.plus(direction.x() * length, direction.y() * length);
    }
}
