package dev.logicforge.circuit.document;

import dev.logicforge.circuit.component.PortSpec;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.PortSide;
import dev.logicforge.circuit.geometry.RoutablePoint;

/** A presentation-aware electrical endpoint resolved into world coordinates. */
public record PlacedPort(PortEndpoint endpoint, PortSpec spec, CircuitPoint position, PortSide side,
                         boolean connectable) implements RoutablePoint {

    public PortReference reference() {
        return endpoint.port();
    }

    public String displayName() {
        return switch (endpoint.slice()) {
            case PortSlice.Whole ignored -> spec.name();
            case PortSlice.Bit bit -> spec.name() + "[" + bit.index() + "]";
            case PortSlice.Range range -> spec.name() + "[" + range.msb() + ":" + range.lsb() + "]";
        };
    }

    /** A short point away from the body, where a wire should leave this port. */
    public CircuitPoint stubEnd(double length) {
        CircuitPoint direction = side.outwards();
        return position.plus(direction.x() * length, direction.y() * length);
    }
}
