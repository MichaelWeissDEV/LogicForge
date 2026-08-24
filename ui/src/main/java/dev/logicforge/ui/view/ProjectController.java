package dev.logicforge.ui.view;

import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.format.ProjectFormat;
import dev.logicforge.format.ProjectFormatException;
import dev.logicforge.ui.edit.CircuitEditor;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * New, Open, Save, Save As — plus the unsaved-changes question.
 *
 * <p>Kept apart from the workbench layout so that file handling stays in one place and the
 * views stay free of dialog code.
 */
public final class ProjectController {

    private static final String EXTENSION_DESCRIPTION = "LogicForge circuit";

    private final CircuitEditor editor;
    private final Stage stage;
    private final Consumer<String> statusMessage;

    private Path currentFile;

    public ProjectController(CircuitEditor editor, Stage stage, Consumer<String> statusMessage) {
        this.editor = editor;
        this.stage = stage;
        this.statusMessage = statusMessage;
        editor.addChangeListener(this::updateTitle);
        updateTitle();
    }

    public void newProject() {
        if (!confirmDiscardingChanges()) {
            return;
        }
        currentFile = null;
        editor.setProject(CircuitProject.empty("untitled"), false);
        statusMessage.accept("New circuit");
        updateTitle();
    }

    public void open() {
        if (!confirmDiscardingChanges()) {
            return;
        }
        FileChooser chooser = chooser("Open circuit");
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            CircuitProject project = ProjectFormat.load(file.toPath());
            currentFile = file.toPath();
            editor.setProject(project, false);
            statusMessage.accept("Opened " + file.getName());
        } catch (ProjectFormatException failure) {
            showError("Could not open the project", failure.getMessage());
        }
        updateTitle();
    }

    public void save() {
        if (currentFile == null) {
            saveAs();
            return;
        }
        writeTo(currentFile);
    }

    public void saveAs() {
        FileChooser chooser = chooser("Save circuit");
        chooser.setInitialFileName(editor.project().name() + "." + ProjectFormat.EXTENSION);
        File file = chooser.showSaveDialog(stage);
        if (file != null) {
            writeTo(file.toPath());
        }
    }

    private void writeTo(Path file) {
        try {
            editor.project().setName(nameOf(file));
            ProjectFormat.save(editor.project(), file);
            currentFile = file;
            editor.markSaved();
            statusMessage.accept("Saved " + file.getFileName());
        } catch (ProjectFormatException failure) {
            showError("Could not save the project", failure.getMessage());
        }
        updateTitle();
    }

    /**
     * Asks about unsaved changes before something would discard them.
     *
     * @return {@code false} if the user cancelled
     */
    public boolean confirmDiscardingChanges() {
        if (!editor.isDirty()) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(stage);
        alert.setTitle("Unsaved changes");
        alert.setHeaderText("Save the changes to " + editor.project().name() + "?");
        alert.setContentText("Your changes will be lost if you don't save them.");

        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Don't Save", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(save, discard, cancel);
        alert.getDialogPane().getStylesheets().setAll(stage.getScene().getStylesheets());

        Optional<ButtonType> answer = alert.showAndWait();
        if (answer.isEmpty() || answer.get() == cancel) {
            return false;
        }
        if (answer.get() == save) {
            save();
            return !editor.isDirty();
        }
        return true;
    }

    private FileChooser chooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                EXTENSION_DESCRIPTION, "*." + ProjectFormat.EXTENSION));
        if (currentFile != null && currentFile.getParent() != null) {
            chooser.setInitialDirectory(currentFile.getParent().toFile());
        }
        return chooser;
    }

    private void showError(String header, String detail) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle("LogicForge");
        alert.setHeaderText(header);
        alert.setContentText(detail);
        alert.getDialogPane().getStylesheets().setAll(stage.getScene().getStylesheets());
        alert.showAndWait();
    }

    private void updateTitle() {
        stage.setTitle("LogicForge — " + editor.project().name() + (editor.isDirty() ? " *" : ""));
    }

    private static String nameOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
