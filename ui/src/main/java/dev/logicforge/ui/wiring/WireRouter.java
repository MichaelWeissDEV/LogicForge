package dev.logicforge.ui.wiring;

import dev.logicforge.circuit.document.PlacedPort;
import dev.logicforge.circuit.geometry.CircuitPoint;
import java.util.List;

/**
 * Computes the path a wire takes between two ports.
 *
 * <p>Version 0.1 ships one deterministic orthogonal router. The interface exists so a
 * better one can replace it without touching the renderer or the editor.
 */
public interface WireRouter {

    /**
     * @param waypoints points the user placed by hand; empty for automatic routing
     */
    WireRoute route(PlacedPort from, PlacedPort to, List<CircuitPoint> waypoints);

    /** Routes to a free point, used for the preview while a wire is being drawn. */
    WireRoute routeToPoint(PlacedPort from, CircuitPoint target);
}
