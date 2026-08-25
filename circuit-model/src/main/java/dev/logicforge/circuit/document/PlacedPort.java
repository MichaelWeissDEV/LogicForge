package dev.logicforge.circuit.document;

import dev.logicforge.circuit.component.PortSpec;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.PortSide;

/** A presentation-aware electrical endpoint resolved into world coordinates. */
public record PlacedPort(PortEndpoint endpoint, PortSpec spec, CircuitPoint position, PortSide side,
                         boolean connectable) {

    public PortReference reference() {
        return endpoint.port();
    }

    public String displayName() {
        return endpoint.slice() instanceof PortSlice.Bit bit
                ? spec.name() + "[" + bit.index() + "]"
                : spec.name();
    }

    /** A short point away from the body, where a wire should leave this port. */
    public CircuitPoint stubEnd(double length) {
        CircuitPoint direction = side.outwards();
        return position.plus(direction.x() * length, direction.y() * length);
    }
}
