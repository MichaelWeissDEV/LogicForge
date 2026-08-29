package dev.logicforge.circuit.geometry;

/**
 * The minimal shape a wire router needs from either end of a connection: a world position
 * and the side of a body it leaves from. Both an ordinary component port ({@code PlacedPort})
 * and a physical chip pin ({@code PlacedElectricalEndpoint}) implement this, so one router
 * can route between any combination of the two without knowing which it has.
 */
public interface RoutablePoint {

    CircuitPoint position();

    PortSide side();

    /** A point {@code length} away from the body, in the direction the pin points. */
    default CircuitPoint stubEnd(double length) {
        CircuitPoint direction = side().outwards();
        return position().plus(direction.x() * length, direction.y() * length);
    }
}
