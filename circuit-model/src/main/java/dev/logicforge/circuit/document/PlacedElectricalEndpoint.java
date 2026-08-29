package dev.logicforge.circuit.document;

import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.PortSide;
import dev.logicforge.circuit.geometry.RoutablePoint;

/**
 * An {@link ElectricalEndpoint} resolved into world coordinates — the chip-pin/component-port
 * agnostic counterpart of {@link PlacedPort}.
 *
 * <p>Hit testing, the wire router and the renderer use this so a physical chip pin is a
 * first-class connection point everywhere a component port is, instead of a second,
 * parallel code path.
 */
public record PlacedElectricalEndpoint(ElectricalEndpoint endpoint, CircuitPoint position, PortSide side,
                                       boolean connectable, String displayName) implements RoutablePoint {
}
