package dev.logicforge.ui.view;

import dev.logicforge.circuit.component.ComponentCategory;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.ComponentType;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The component library, grouped by category and searchable.
 *
 * <p>Everything shown here comes from the {@link ComponentRegistry}; the palette has no
 * list of its own, so a newly registered component appears without a line of UI code.
 * Categories that contain nothing are not shown at all.
 */
public final class PaletteView extends VBox {

    /** Either a category heading or a component; the list shows both. */
    private sealed interface Entry {

        record Category(ComponentCategory category) implements Entry {
        }

        record Component(ComponentType type) implements Entry {
        }
    }

    private final ComponentRegistry registry;
    private final ListView<Entry> list = new ListView<>();
    private final TextField search = new TextField();

    public PaletteView(ComponentRegistry registry, CircuitCanvasView canvas) {
        this.registry = registry;
        getStyleClass().add("side-panel");

        Label header = new Label("COMPONENTS");
        header.getStyleClass().add("panel-header");

        search.setPromptText("Search components...");
        search.getStyleClass().add("search-field");
        search.textProperty().addListener((observable, old, text) -> refresh(text));
        VBox.setMargin(search, new javafx.geometry.Insets(0, 10, 8, 10));

        list.getStyleClass().add("palette-list");
        list.setCellFactory(view -> new EntryCell(canvas));
        VBox.setVgrow(list, Priority.ALWAYS);

        list.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && list.getSelectionModel().getSelectedItem()
                    instanceof Entry.Component component) {
                canvas.setPendingPlacement(component.type().id());
            }
        });

        getChildren().addAll(header, search, list);
        refresh("");
    }

    /** Puts the caret into the search field, for the keyboard shortcut. */
    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    private void refresh(String query) {
        List<Entry> entries = new ArrayList<>();
        if (query == null || query.isBlank()) {
            for (ComponentCategory category : registry.populatedCategories()) {
                entries.add(new Entry.Category(category));
                registry.byCategory(category).forEach(type -> entries.add(new Entry.Component(type)));
            }
        } else {
            registry.search(query).forEach(type -> entries.add(new Entry.Component(type)));
        }
        list.getItems().setAll(entries);
    }

    /** One row: a quiet category heading, or a draggable component. */
    private static final class EntryCell extends ListCell<Entry> {

        private final CircuitCanvasView canvas;

        EntryCell(CircuitCanvasView canvas) {
            this.canvas = canvas;
            setOnDragDetected(event -> {
                if (getItem() instanceof Entry.Component component) {
                    startDragAndDrop(TransferMode.COPY)
                            .setContent(CircuitCanvasView.dragContentFor(component.type().id()));
                    event.consume();
                }
            });
        }

        @Override
        protected void updateItem(Entry entry, boolean empty) {
            super.updateItem(entry, empty);
            getStyleClass().removeAll("palette-category");
            if (empty || entry == null) {
                setGraphic(null);
                setText(null);
                setDisable(false);
                return;
            }
            switch (entry) {
                case Entry.Category category -> {
                    setGraphic(null);
                    setText(category.category().displayName().toUpperCase(java.util.Locale.ROOT));
                    getStyleClass().add("palette-category");
                    setDisable(true);
                }
                case Entry.Component component -> {
                    setText(null);
                    setDisable(false);
                    setGraphic(componentRow(component.type()));
                    setTooltip(tooltipFor(component.type()));
                }
            }
        }

        private javafx.scene.Node componentRow(ComponentType type) {
            Label name = new Label(type.displayName());
            name.getStyleClass().add("palette-item-name");
            javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(name);
            row.setAlignment(Pos.CENTER_LEFT);
            return row;
        }

        private javafx.scene.control.Tooltip tooltipFor(ComponentType type) {
            javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(
                    type.definition().description() + "\n\nDrag onto the canvas, or double-click to place.");
            tooltip.setShowDelay(javafx.util.Duration.millis(400));
            return tooltip;
        }
    }
}
