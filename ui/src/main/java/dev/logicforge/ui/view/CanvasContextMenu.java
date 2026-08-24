package dev.logicforge.ui.view;

import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

/**
 * The right-click menu of the canvas: actions on the component under the cursor, or view
 * actions when the click lands on empty canvas.
 */
final class CanvasContextMenu {

    private final ContextMenu menu = new ContextMenu();
    private final CircuitCanvasView canvas;

    CanvasContextMenu(CircuitCanvasView canvas) {
        this.canvas = canvas;
    }

    void showForComponent(Node owner, double screenX, double screenY) {
        menu.getItems().setAll(
                item("Rotate", canvas::rotateSelection),
                item("Duplicate", canvas::duplicateSelection),
                new SeparatorMenuItem(),
                item("Delete", canvas::deleteSelection));
        menu.show(owner, screenX, screenY);
    }

    void showForCanvas(Node owner, double screenX, double screenY) {
        menu.getItems().setAll(
                item("Paste", canvas::paste),
                new SeparatorMenuItem(),
                item("Zoom to fit", canvas::zoomToFit),
                item("Reset zoom", canvas::resetZoom));
        menu.show(owner, screenX, screenY);
    }

    void hide() {
        menu.hide();
    }

    private MenuItem item(String text, Runnable action) {
        MenuItem menuItem = new MenuItem(text);
        menuItem.setOnAction(event -> action.run());
        return menuItem;
    }
}
