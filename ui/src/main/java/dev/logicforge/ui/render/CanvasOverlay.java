package dev.logicforge.ui.render;

import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.PlacedElectricalEndpoint;
import dev.logicforge.circuit.document.PlacedPort;
import dev.logicforge.circuit.geometry.CircuitBounds;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.ui.wiring.WireRoute;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The transient things drawn on top of the circuit: what the mouse is over, the rubber
 * band, the wire being drawn, the component or chip being dropped.
 *
 * <p>None of this is part of the circuit, so none of it is ever saved.
 */
public record CanvasOverlay(
        UUID hoveredComponent,
        UUID hoveredChip,
        PlacedPort hoveredPort,
        PlacedElectricalEndpoint hoveredChipPin,
        CircuitBounds selectionRectangle,
        WireRoute previewWire,
        PlacedElectricalEndpoint wireOrigin,
        ComponentInstance ghost,
        ChipInstance chipGhost,
        Map<UUID, CircuitPoint> movingComponentPositions,
        Map<UUID, CircuitPoint> movingChipPositions) {

    public static final CanvasOverlay EMPTY = new CanvasOverlay(null, null, null, null, null, null, null,
            null, null, Map.of(), Map.of());

    public CanvasOverlay withHover(UUID component, PlacedPort port) {
        return new CanvasOverlay(component, hoveredChip, port, hoveredChipPin, selectionRectangle, previewWire,
                wireOrigin, ghost, chipGhost, movingComponentPositions, movingChipPositions);
    }

    public CanvasOverlay withChipHover(UUID chip, PlacedElectricalEndpoint chipPin) {
        return new CanvasOverlay(hoveredComponent, chip, hoveredPort, chipPin, selectionRectangle, previewWire,
                wireOrigin, ghost, chipGhost, movingComponentPositions, movingChipPositions);
    }

    public CanvasOverlay withSelectionRectangle(CircuitBounds rectangle) {
        return new CanvasOverlay(hoveredComponent, hoveredChip, hoveredPort, hoveredChipPin, rectangle,
                previewWire, wireOrigin, ghost, chipGhost, movingComponentPositions, movingChipPositions);
    }

    public CanvasOverlay withPreviewWire(PlacedElectricalEndpoint origin, WireRoute route) {
        return new CanvasOverlay(hoveredComponent, hoveredChip, hoveredPort, hoveredChipPin, selectionRectangle,
                route, origin, ghost, chipGhost, movingComponentPositions, movingChipPositions);
    }

    public CanvasOverlay withGhost(ComponentInstance instance) {
        return new CanvasOverlay(hoveredComponent, hoveredChip, hoveredPort, hoveredChipPin, selectionRectangle,
                previewWire, wireOrigin, instance, chipGhost, movingComponentPositions, movingChipPositions);
    }

    public CanvasOverlay withChipGhost(ChipInstance instance) {
        return new CanvasOverlay(hoveredComponent, hoveredChip, hoveredPort, hoveredChipPin, selectionRectangle,
                previewWire, wireOrigin, ghost, instance, movingComponentPositions, movingChipPositions);
    }

    public CanvasOverlay withMovingComponents(Map<UUID, CircuitPoint> positions) {
        return new CanvasOverlay(hoveredComponent, hoveredChip, hoveredPort, hoveredChipPin, selectionRectangle,
                previewWire, wireOrigin, ghost, chipGhost, positions, movingChipPositions);
    }

    public CanvasOverlay withMovingChips(Map<UUID, CircuitPoint> positions) {
        return new CanvasOverlay(hoveredComponent, hoveredChip, hoveredPort, hoveredChipPin, selectionRectangle,
                previewWire, wireOrigin, ghost, chipGhost, movingComponentPositions, positions);
    }

    public Optional<PlacedPort> hoveredPortOption() {
        return Optional.ofNullable(hoveredPort);
    }

    public Optional<PlacedElectricalEndpoint> hoveredChipPinOption() {
        return Optional.ofNullable(hoveredChipPin);
    }
}
