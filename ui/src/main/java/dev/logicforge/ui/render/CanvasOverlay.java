package dev.logicforge.ui.render;

import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.PlacedPort;
import dev.logicforge.circuit.geometry.CircuitBounds;
import dev.logicforge.ui.wiring.WireRoute;
import java.util.Optional;
import java.util.UUID;

/**
 * The transient things drawn on top of the circuit: what the mouse is over, the rubber
 * band, the wire being drawn, the component being dropped.
 *
 * <p>None of this is part of the circuit, so none of it is ever saved.
 */
public record CanvasOverlay(
        UUID hoveredComponent,
        PlacedPort hoveredPort,
        CircuitBounds selectionRectangle,
        WireRoute previewWire,
        PlacedPort wireOrigin,
        ComponentInstance ghost) {

    public static final CanvasOverlay EMPTY =
            new CanvasOverlay(null, null, null, null, null, null);

    public CanvasOverlay withHover(UUID component, PlacedPort port) {
        return new CanvasOverlay(component, port, selectionRectangle, previewWire, wireOrigin, ghost);
    }

    public CanvasOverlay withSelectionRectangle(CircuitBounds rectangle) {
        return new CanvasOverlay(hoveredComponent, hoveredPort, rectangle, previewWire, wireOrigin, ghost);
    }

    public CanvasOverlay withPreviewWire(PlacedPort origin, WireRoute route) {
        return new CanvasOverlay(hoveredComponent, hoveredPort, selectionRectangle, route, origin, ghost);
    }

    public CanvasOverlay withGhost(ComponentInstance instance) {
        return new CanvasOverlay(hoveredComponent, hoveredPort, selectionRectangle, previewWire,
                wireOrigin, instance);
    }

    public Optional<PlacedPort> hoveredPortOption() {
        return Optional.ofNullable(hoveredPort);
    }
}
