package dev.logicforge.ui.view;

import dev.logicforge.format.ProjectFormat;
import dev.logicforge.format.ProjectFormatException;
import dev.logicforge.ui.edit.CircuitEditor;
import dev.logicforge.ui.edit.ProjectFiles;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * views stay free of dialog code. The file work itself happens in {@link ProjectFiles};
 * this class only asks the questions and reports what went wrong.
 */
public final class ProjectController {

    /** Presents a failed file operation to the user. */
    @FunctionalInterface
    public interface ErrorPresenter {
        void showError(String title, String header, String detail);
    }

    private static final String EXTENSION_DESCRIPTION = "LogicForge project";

    private final CircuitEditor editor;
    private final ProjectFiles files;
    private final Stage stage;
    private final Consumer<String> statusMessage;

    private ErrorPresenter errors = this::showErrorDialog;
    private Runnable afterDialog = () -> { };
    private Path defaultDirectory;

    public ProjectController(CircuitEditor editor, Stage stage, Consumer<String> statusMessage) {
        this.editor = editor;
        this.files = new ProjectFiles(editor);
        this.stage = stage;
        this.statusMessage = statusMessage;
        editor.addChangeListener(this::updateTitle);
        updateTitle();
    }

    /** Replaces the error dialogs, e.g. by a non-interactive reporter for automated checks. */
    public void setErrorPresenter(ErrorPresenter presenter) {
        this.errors = presenter;
    }

    /** Called after a file dialog or message closes, to give the keyboard focus back. */
    public void setAfterDialog(Runnable action) {
        this.afterDialog = action;
    }

    /** Where file choosers start when the project has no file yet. */
    public void setDefaultDirectory(Path directory) {
        this.defaultDirectory = directory;
    }

    /** The file the edited project belongs to, if it has one. */
    public Optional<Path> currentFile() {
        return files.currentFile();
    }

    public void newProject() {
        if (!confirmDiscardingChanges()) {
            return;
        }
        files.newProject();
        statusMessage.accept("New circuit");
        updateTitle();
    }

    /** File &rarr; Open: asks for a file, then opens it like {@link #open(Path)}. */
    public void open() {
        if (!confirmDiscardingChanges()) {
            return;
        }
        FileChooser chooser = chooser("Open Project");
        File file = chooser.showOpenDialog(stage);
        afterDialog.run();
        if (file != null) {
            load(file.toPath());
        }
    }

    /**
     * Opens {@code file} directly — from the command line or the desktop's file association.
     * Asks about unsaved changes first and reports a file that cannot be opened.
     *
     * @return {@code true} if the file is now the edited project
     */
    public boolean open(Path file) {
        if (!confirmDiscardingChanges()) {
            return false;
        }
        return load(file);
    }

    private boolean load(Path file) {
        try {
            files.open(file);
            statusMessage.accept("Opened " + file.getFileName());
            return true;
        } catch (ProjectFormatException failure) {
            report("Open Project", "Could not open " + file.getFileName(), failure.getMessage());
            return false;
        } finally {
            updateTitle();
        }
    }

    /** Opens a newly parsed copy of a read-only bundled example resource. */
    public void openExample(String displayName, String resourcePath) {
        if (!confirmDiscardingChanges()) {
            return;
        }
        try (InputStream stream = ProjectController.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Missing bundled resource " + resourcePath);
            }
            files.openUnsaved(displayName, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            statusMessage.accept("Opened example " + displayName);
        } catch (IOException | ProjectFormatException failure) {
            report("Open Example", "Could not open the example " + displayName, failure.getMessage());
        }
        updateTitle();
    }

    /** Saves to the current file, or asks for one. {@code true} if the project is saved. */
    public boolean save() {
        if (files.currentFile().isEmpty()) {
            return saveAs();
        }
        return write(files::save);
    }

    /** Asks for a file and saves to it. {@code true} if the project is saved. */
    public boolean saveAs() {
        FileChooser chooser = chooser("Save Project As");
        chooser.setInitialFileName(
                ProjectFiles.saveTarget(Path.of(safeFileName(editor.project().name()))).toString());
        File file = chooser.showSaveDialog(stage);
        afterDialog.run();
        if (file == null) {
            return false;
        }
        Path chosen = file.toPath();
        Path target = ProjectFiles.saveTarget(chosen);
        // The file dialog only asked about overwriting the name as typed, not the one with
        // the extension added, so ask here before replacing an existing project silently.
        if (!target.equals(chosen) && Files.exists(target) && !confirmReplace(target)) {
            return false;
        }
        return write(() -> files.saveAs(target));
    }

    private boolean write(java.util.function.Supplier<Path> save) {
        try {
            Path written = save.get();
            statusMessage.accept("Saved " + written.getFileName());
            return true;
        } catch (ProjectFormatException failure) {
            report("Save Project", "Could not save the project", failure.getMessage());
            return false;
        } finally {
            updateTitle();
        }
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
        Alert alert = Dialogs.alert(Alert.AlertType.CONFIRMATION, stage, "Unsaved Changes",
                "Save the changes to " + editor.project().name() + "?",
                "Your changes will be lost if you don't save them.");
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Don't Save", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(save, discard, cancel);

        Optional<ButtonType> answer = alert.showAndWait();
        afterDialog.run();
        if (answer.isEmpty() || answer.get() == cancel) {
            return false;
        }
        if (answer.get() == save) {
            return save() && !editor.isDirty();
        }
        return true;
    }

    private boolean confirmReplace(Path target) {
        boolean replace = Dialogs.confirm(stage, "Replace File",
                target.getFileName() + " already exists.",
                "Do you want to replace it? Its current content will be kept as "
                        + target.getFileName() + ".bak.", "Replace");
        afterDialog.run();
        return replace;
    }

    private FileChooser chooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(EXTENSION_DESCRIPTION, "*." + ProjectFormat.EXTENSION),
                new FileChooser.ExtensionFilter("All files", "*"));
        Path directory = files.currentFile().map(Path::getParent).orElse(defaultDirectory);
        if (directory != null && Files.isDirectory(directory)) {
            chooser.setInitialDirectory(directory.toFile());
        }
        return chooser;
    }

    private void report(String title, String header, String detail) {
        errors.showError(title, header, detail);
    }

    private void showErrorDialog(String title, String header, String detail) {
        Dialogs.error(stage, title, header, detail);
        afterDialog.run();
    }

    private void updateTitle() {
        stage.setTitle(editor.project().name() + (editor.isDirty() ? " *" : "") + " — LogicForge");
    }

    /** A project name as a file name: path separators would make the chooser misread it. */
    private static String safeFileName(String name) {
        String cleaned = name.replace('/', '-').replace('\\', '-').strip();
        return cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..") ? "untitled" : cleaned;
    }
}
