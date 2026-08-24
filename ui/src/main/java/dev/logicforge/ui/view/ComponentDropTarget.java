package dev.logicforge.ui.view;

import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.ui.edit.CircuitEditor;
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
 * Accepts components dragged out of the palette.
 *
 * <p>While a component hovers over the canvas a translucent preview follows the cursor,
 * snapped to the grid; dropping it places it there. The drop position is computed with the
 * same viewport transform the renderer uses, so a component always lands where it is shown,
 * at any zoom or pan.
 */
final class ComponentDropTarget {

    /** Prefix identifying a palette drag on the system clipboard. */
    static final String COMPONENT_DRAG_PREFIX = "logicforge:component:";

    private final CircuitEditor editor;
    private final ViewportTransform viewport;
    private final Function<CircuitPoint, CircuitPoint> snap = Grid::snap;

    ComponentDropTarget(CircuitEditor editor, ViewportTransform viewport) {
        this.editor = editor;
        this.viewport = viewport;
    }

    static ClipboardContent contentFor(String definitionId) {
        ClipboardContent content = new ClipboardContent();
        content.putString(COMPONENT_DRAG_PREFIX + definitionId);
        return content;
    }

    /**
     * @param showGhost called with the preview component, or {@code null} to remove it
     * @param place     called with the component to add when the drop happens
     */
    void install(Region canvas, Consumer<ComponentInstance> showGhost,
                 Consumer<ComponentInstance> place) {
        canvas.setOnDragOver(event -> {
            definitionIdOf(event.getDragboard()).ifPresent(definitionId -> {
                event.acceptTransferModes(TransferMode.COPY);
                showGhost.accept(instanceAt(definitionId, event.getX(), event.getY()));
            });
            event.consume();
        });
        canvas.setOnDragExited(event -> showGhost.accept(null));
        canvas.setOnDragDropped(event -> {
            Optional<String> definitionId = definitionIdOf(event.getDragboard());
            definitionId.ifPresent(id -> place.accept(instanceAt(id, event.getX(), event.getY())));
            showGhost.accept(null);
            event.setDropCompleted(definitionId.isPresent());
            event.consume();
        });
    }

    /** A new, unplaced instance of {@code definitionId} at a screen position. */
    ComponentInstance instanceAt(String definitionId, double screenX, double screenY) {
        return instanceAt(definitionId, snap.apply(viewport.screenToWorld(screenX, screenY)));
    }

    ComponentInstance instanceAt(String definitionId, CircuitPoint world) {
        return ComponentInstance.create(definitionId, world,
                editor.definition(definitionId).orElseThrow().defaultParameters());
    }

    private Optional<String> definitionIdOf(Dragboard dragboard) {
        if (!dragboard.hasString() || !dragboard.getString().startsWith(COMPONENT_DRAG_PREFIX)) {
            return Optional.empty();
        }
        String id = dragboard.getString().substring(COMPONENT_DRAG_PREFIX.length());
        return editor.definition(id).isPresent() ? Optional.of(id) : Optional.empty();
    }
}
