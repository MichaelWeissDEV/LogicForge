package dev.logicforge.ui.wiring;

import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.RoutablePoint;
import java.util.List;

/**
 * Computes the path a wire takes between two connection points.
 *
 * <p>Version 0.1 ships one deterministic orthogonal router. The interface exists so a
 * better one can replace it without touching the renderer or the editor. It routes against
 * {@link RoutablePoint} rather than a component port specifically, so the same router wires
 * up a physical chip pin exactly like an ordinary port — a component port ({@code PlacedPort})
 * and a chip pin ({@code PlacedElectricalEndpoint}) are interchangeable here.
 */
public interface WireRouter {

    /**
     * @param waypoints points the user placed by hand; empty for automatic routing
     */
    WireRoute route(RoutablePoint from, RoutablePoint to, List<CircuitPoint> waypoints);

    /** Routes to a free point, used for the preview while a wire is being drawn. */
    WireRoute routeToPoint(RoutablePoint from, CircuitPoint target);
}
