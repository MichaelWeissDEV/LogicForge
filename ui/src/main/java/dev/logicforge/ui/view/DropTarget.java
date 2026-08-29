package dev.logicforge.ui.view;

import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.ui.edit.ChipDesignators;
import dev.logicforge.ui.edit.CircuitEditor;
import dev.logicforge.ui.edit.PlacementRequest;
import dev.logicforge.ui.viewport.Grid;
import dev.logicforge.ui.viewport.ViewportTransform;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;

/**
 * Accepts components and physical chips dragged out of the palette.
 *
 * <p>While something hovers over the canvas a translucent preview follows the cursor,
 * snapped to the grid; dropping it places it there. The drop position is computed with the
 * same viewport transform the renderer uses, so a placement always lands where it is shown,
 * at any zoom or pan.
 */
final class DropTarget {

    private final CircuitEditor editor;
    private final ViewportTransform viewport;
    private final Function<CircuitPoint, CircuitPoint> snap = Grid::snap;

    DropTarget(CircuitEditor editor, ViewportTransform viewport) {
        this.editor = editor;
        this.viewport = viewport;
    }

    static ClipboardContent contentFor(PlacementRequest request) {
        ClipboardContent content = new ClipboardContent();
        content.putString(request.toClipboardString());
        return content;
    }

    /**
     * @param showComponentGhost called with the preview component, or {@code null} to remove it
     * @param showChipGhost      called with the preview chip, or {@code null} to remove it
     * @param placeComponent     called with the component to add when the drop happens
     * @param placeChip          called with the chip to add when the drop happens
     */
    void install(Region canvas, Consumer<ComponentInstance> showComponentGhost,
                Consumer<ChipInstance> showChipGhost, Consumer<ComponentInstance> placeComponent,
                Consumer<ChipInstance> placeChip) {
        canvas.setOnDragOver(event -> {
            requestOf(event.getDragboard()).ifPresent(request -> {
                event.acceptTransferModes(TransferMode.COPY);
                CircuitPoint world = snap.apply(viewport.screenToWorld(event.getX(), event.getY()));
                switch (request) {
                    case PlacementRequest.Component component -> {
                        showChipGhost.accept(null);
                        showComponentGhost.accept(componentAt(component.definitionId(), world));
                    }
                    case PlacementRequest.Chip chip -> {
                        showComponentGhost.accept(null);
                        showChipGhost.accept(chipAt(chip.chipDefinitionId(), world));
                    }
                }
            });
            event.consume();
        });
        canvas.setOnDragExited(event -> {
            showComponentGhost.accept(null);
            showChipGhost.accept(null);
        });
        canvas.setOnDragDropped(event -> {
            Optional<PlacementRequest> request = requestOf(event.getDragboard());
            request.ifPresent(value -> {
                CircuitPoint world = snap.apply(viewport.screenToWorld(event.getX(), event.getY()));
                switch (value) {
                    case PlacementRequest.Component component ->
                            placeComponent.accept(componentAt(component.definitionId(), world));
                    case PlacementRequest.Chip chip -> placeChip.accept(chipAt(chip.chipDefinitionId(), world));
                }
            });
            showComponentGhost.accept(null);
            showChipGhost.accept(null);
            event.setDropCompleted(request.isPresent());
            event.consume();
        });
    }

    /** A new, unplaced instance of {@code definitionId} at a screen position. */
    ComponentInstance componentAt(String definitionId, double screenX, double screenY) {
        return componentAt(definitionId, snap.apply(viewport.screenToWorld(screenX, screenY)));
    }

    ComponentInstance componentAt(String definitionId, CircuitPoint world) {
        return ComponentInstance.create(definitionId, world,
                editor.definition(definitionId).orElseThrow().defaultParameters());
    }

    /** A new, unplaced physical chip of {@code chipDefinitionId} at a screen position. */
    ChipInstance chipAt(String chipDefinitionId, double screenX, double screenY) {
        return chipAt(chipDefinitionId, snap.apply(viewport.screenToWorld(screenX, screenY)));
    }

    ChipInstance chipAt(String chipDefinitionId, CircuitPoint world) {
        return ChipInstance.create(chipDefinitionId, world, ChipDesignators.next(editor.document()));
    }

    private Optional<PlacementRequest> requestOf(Dragboard dragboard) {
        if (!dragboard.hasString()) {
            return Optional.empty();
        }
        return PlacementRequest.fromClipboardString(dragboard.getString()).filter(this::isKnown);
    }

    private boolean isKnown(PlacementRequest request) {
        return switch (request) {
            case PlacementRequest.Component component -> editor.definition(component.definitionId()).isPresent();
            case PlacementRequest.Chip chip -> editor.chipRegistry().find(chip.chipDefinitionId()).isPresent();
        };
    }
}
