package dev.logicforge.ui.view;

import dev.logicforge.circuit.chip.ChipDefinition;
import dev.logicforge.circuit.component.ComponentCategory;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.ComponentType;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.SubcircuitSupport;
import dev.logicforge.ui.edit.CircuitEditor;
import dev.logicforge.ui.edit.PlacementRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The component and chip library: components grouped by category, physical ICs grouped by
 * family, both searchable and both draggable/double-clickable onto the canvas.
 *
 * <p>Everything shown here comes from the {@link ComponentRegistry} and the editor's
 * {@code ChipRegistry}; the palette has no list of its own, so a newly registered component
 * or chip appears without a line of UI code. Categories/families that contain nothing are
 * not shown at all.
 */
public final class PaletteView extends VBox {

    /** A section heading, a component, or a physical chip; the list shows all three. */
    private sealed interface Entry {

        record Category(String label) implements Entry {
        }

        record Component(ComponentType type) implements Entry {
        }

        record Chip(ChipDefinition definition) implements Entry {
        }
    }

    private final ComponentRegistry registry;
    private final CircuitEditor editor;
    private final ListView<Entry> list = new ListView<>();
    private final TextField search = new TextField();

    public PaletteView(CircuitEditor editor, CircuitCanvasView canvas) {
        this.editor = editor;
        this.registry = editor.registry();
        getStyleClass().add("side-panel");

        Label header = new Label("LIBRARY");
        header.getStyleClass().add("panel-header");

        search.setPromptText("Search components and ICs...");
        search.getStyleClass().add("search-field");
        search.textProperty().addListener((observable, old, text) -> refresh(text));
        VBox.setMargin(search, new javafx.geometry.Insets(0, 10, 8, 10));

        list.getStyleClass().add("palette-list");
        list.setCellFactory(view -> new EntryCell(canvas));
        VBox.setVgrow(list, Priority.ALWAYS);

        list.setOnMouseClicked(event -> {
            if (event.getClickCount() != 2) {
                return;
            }
            switch (list.getSelectionModel().getSelectedItem()) {
                case Entry.Component component -> canvas.setPendingPlacement(
                        new PlacementRequest.Component(component.type().id()));
                case Entry.Chip chip -> canvas.setPendingPlacement(
                        new PlacementRequest.Chip(chip.definition().metadata().partNumber()));
                default -> {
                }
            }
        });

        getChildren().addAll(header, search, list);
        editor.addChangeListener(() -> refresh(search.getText()));
        refresh("");
    }

    /** Puts the caret into the search field, for the keyboard shortcut. */
    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    private void refresh(String query) {
        List<Entry> entries = new ArrayList<>();
        List<ComponentType> availableComponents = availableTypes();
        List<ChipDefinition> availableChips = List.copyOf(editor.chipRegistry().definitions());
        if (query == null || query.isBlank()) {
            entries.add(new Entry.Category("COMPONENTS"));
            for (ComponentCategory category : ComponentCategory.values()) {
                List<ComponentType> categoryTypes = availableComponents.stream()
                        .filter(type -> type.definition().category() == category).toList();
                if (categoryTypes.isEmpty()) continue;
                entries.add(new Entry.Category(category.displayName().toUpperCase(Locale.ROOT)));
                categoryTypes.forEach(type -> entries.add(new Entry.Component(type)));
            }
            if (!availableChips.isEmpty()) {
                entries.add(new Entry.Category("ICS"));
                Map<String, List<ChipDefinition>> byFamily = new LinkedHashMap<>();
                for (ChipDefinition chip : availableChips) {
                    byFamily.computeIfAbsent(chip.metadata().family(), ignored -> new ArrayList<>()).add(chip);
                }
                byFamily.forEach((family, chips) -> {
                    entries.add(new Entry.Category(family.toUpperCase(Locale.ROOT)));
                    chips.forEach(chip -> entries.add(new Entry.Chip(chip)));
                });
            }
        } else {
            String[] tokens = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
            availableComponents.stream()
                    .filter(type -> matchesAllTokens(componentHaystack(type), tokens))
                    .forEach(type -> entries.add(new Entry.Component(type)));
            availableChips.stream()
                    .filter(chip -> matchesAllTokens(chipHaystack(chip), tokens))
                    .forEach(chip -> entries.add(new Entry.Chip(chip)));
        }
        list.getItems().setAll(entries);
    }

    private static boolean matchesAllTokens(String haystack, String[] tokens) {
        for (String token : tokens) {
            if (!token.isBlank() && !haystack.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private static String componentHaystack(ComponentType type) {
        return (type.displayName() + " " + type.id() + " "
                + String.join(" ", type.definition().searchKeywords())).toLowerCase(Locale.ROOT);
    }

    private static String chipHaystack(ChipDefinition chip) {
        return (chip.metadata().partNumber() + " " + chip.metadata().family() + " "
                + chip.metadata().summary() + " " + String.join(" ", chip.metadata().tags()) + " "
                + chip.packageDefinition().type().name()).toLowerCase(Locale.ROOT);
    }

    private List<ComponentType> availableTypes() {
        List<ComponentType> types = new ArrayList<>(registry.all());
        for (var circuit : editor.project().circuits()) {
            if (circuit.metadata().name().equals(CircuitProject.MAIN_CIRCUIT)) continue;
            var definition = SubcircuitSupport.definitionFor(circuit.metadata().name(), circuit);
            types.add(ComponentType.of(definition, context -> { }));
        }
        return types;
    }

    /** One row: a quiet section heading, a draggable component, or a draggable chip. */
    private static final class EntryCell extends ListCell<Entry> {

        private final CircuitCanvasView canvas;

        EntryCell(CircuitCanvasView canvas) {
            this.canvas = canvas;
            setOnDragDetected(event -> {
                switch (getItem()) {
                    case Entry.Component component -> {
                        startDragAndDrop(TransferMode.COPY).setContent(CircuitCanvasView.dragContentFor(
                                new PlacementRequest.Component(component.type().id())));
                        event.consume();
                    }
                    case Entry.Chip chip -> {
                        startDragAndDrop(TransferMode.COPY).setContent(CircuitCanvasView.dragContentFor(
                                new PlacementRequest.Chip(chip.definition().metadata().partNumber())));
                        event.consume();
                    }
                    default -> {
                    }
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
                    setText(category.label());
                    getStyleClass().add("palette-category");
                    setDisable(true);
                }
                case Entry.Component component -> {
                    setText(null);
                    setDisable(false);
                    setGraphic(componentRow(component.type().displayName()));
                    setTooltip(tooltipFor(component.type().definition().description()
                            + "\n\nDrag onto the canvas, or double-click to place."));
                }
                case Entry.Chip chip -> {
                    setText(null);
                    setDisable(false);
                    setGraphic(chipRow(chip.definition()));
                    setTooltip(tooltipFor(chip.definition().metadata().summary()
                            + "\n" + chip.definition().packageDefinition().type()
                            + " · " + chip.definition().packageDefinition().pins().size() + " pins"
                            + "\n\nDrag onto the canvas, or double-click to place."));
                }
            }
        }

        private javafx.scene.Node componentRow(String displayName) {
            Label name = new Label(displayName);
            name.getStyleClass().add("palette-item-name");
            javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(name);
            row.setAlignment(Pos.CENTER_LEFT);
            return row;
        }

        private javafx.scene.Node chipRow(ChipDefinition definition) {
            Label name = new Label(definition.metadata().partNumber());
            name.getStyleClass().add("palette-item-name");
            Label meta = new Label(definition.packageDefinition().type().name());
            meta.getStyleClass().add("palette-item-meta");
            javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
            javafx.scene.layout.HBox.setHgrow(spacer, Priority.ALWAYS);
            javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(name, spacer, meta);
            row.setAlignment(Pos.CENTER_LEFT);
            return row;
        }

        private javafx.scene.control.Tooltip tooltipFor(String text) {
            javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(text);
            tooltip.setShowDelay(javafx.util.Duration.millis(400));
            return tooltip;
        }
    }
}
