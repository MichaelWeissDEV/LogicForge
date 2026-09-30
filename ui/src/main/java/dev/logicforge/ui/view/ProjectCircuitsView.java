package dev.logicforge.ui.view;

import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.ui.edit.CircuitEditor;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Compact project tree and hierarchy breadcrumb shown above the component palette. */
public final class ProjectCircuitsView extends VBox {

    private final CircuitEditor editor;
    private final ListView<String> circuits = new ListView<>();
    private final Label breadcrumb = new Label();
    private final Button back = new Button("Back");

    public ProjectCircuitsView(CircuitEditor editor) {
        this.editor = editor;
        getStyleClass().add("side-panel");
        setPrefHeight(220);
        setMinHeight(150);

        Label header = new Label("PROJECT");
        header.getStyleClass().add("panel-header");
        back.getStyleClass().add("tool-button");
        back.setOnAction(event -> editor.navigateBack());
        breadcrumb.getStyleClass().add("inspector-subtitle");
        breadcrumb.setWrapText(true);
        HBox navigation = new HBox(6, back, breadcrumb);
        navigation.setPadding(new Insets(4, 8, 4, 8));

        circuits.setPrefHeight(110);
        circuits.getStyleClass().add("palette-list");
        circuits.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                setText(empty ? null : name + (name != null && name.equals(editor.activeCircuitName())
                        ? "  •" : ""));
            }
        });
        circuits.setOnMouseClicked(event -> {
            String selected = circuits.getSelectionModel().getSelectedItem();
            if (selected != null && event.getClickCount() == 2) {
                editor.openCircuit(selected);
            }
        });

        Button create = new Button("New");
        Button rename = new Button("Rename");
        Button delete = new Button("Delete");
        create.setOnAction(event -> promptNew());
        rename.setOnAction(event -> promptRename());
        delete.setOnAction(event -> deleteSelected());
        for (Button action : new Button[]{create, rename, delete}) {
            action.setMinWidth(Region.USE_PREF_SIZE);
        }
        create.setTooltip(new javafx.scene.control.Tooltip("Create a new reusable circuit in this project"));
        rename.setTooltip(new javafx.scene.control.Tooltip("Rename the selected circuit"));
        delete.setTooltip(new javafx.scene.control.Tooltip("Delete the selected circuit"));
        HBox actions = new HBox(6, create, rename, delete);
        actions.setPadding(new Insets(4, 8, 8, 8));

        VBox.setVgrow(circuits, Priority.ALWAYS);
        getChildren().addAll(header, navigation, circuits, actions);
        editor.addChangeListener(this::refresh);
        refresh();
    }

    private void promptNew() {
        TextInputDialog dialog = new TextInputDialog("Circuit" + editor.project().circuits().size());
        dialog.setTitle("New Circuit");
        dialog.setHeaderText("Create a reusable circuit");
        dialog.setContentText("Name:");
        dialog.showAndWait().ifPresent(name -> runSafely(() -> editor.addCircuit(name)));
    }

    private void promptRename() {
        String selected = selectedCircuit();
        if (selected == null || CircuitProject.MAIN_CIRCUIT.equals(selected)) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog(selected);
        dialog.setTitle("Rename Circuit");
        dialog.setHeaderText("Rename " + selected + " and update all instances");
        dialog.setContentText("Name:");
        dialog.showAndWait().ifPresent(name -> runSafely(() -> editor.renameCircuit(selected, name)));
    }

    private void deleteSelected() {
        String selected = selectedCircuit();
        if (selected == null || CircuitProject.MAIN_CIRCUIT.equals(selected)) {
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete circuit '" + selected + "'? Existing instances will be reported as invalid.");
        confirmation.setHeaderText("Delete reusable circuit");
        Optional<javafx.scene.control.ButtonType> answer = confirmation.showAndWait();
        if (answer.isPresent() && answer.get() == javafx.scene.control.ButtonType.OK) {
            runSafely(() -> editor.deleteCircuit(selected));
        }
    }

    private String selectedCircuit() {
        String selected = circuits.getSelectionModel().getSelectedItem();
        return selected == null ? editor.activeCircuitName() : selected;
    }

    private void runSafely(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException failure) {
            Alert error = new Alert(Alert.AlertType.ERROR, failure.getMessage());
            error.setHeaderText("Circuit operation failed");
            error.showAndWait();
        }
    }

    private void refresh() {
        circuits.getItems().setAll(editor.project().circuitNames());
        circuits.getSelectionModel().select(editor.activeCircuitName());
        breadcrumb.setText(String.join("  >  ", editor.navigationLabels()));
        back.setDisable(!editor.canNavigateBack());
        circuits.refresh();
    }
}
