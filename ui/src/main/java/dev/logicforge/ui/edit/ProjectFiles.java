package dev.logicforge.ui.edit;

import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.format.AtomicFileWriter;
import dev.logicforge.format.ProjectFormat;
import dev.logicforge.format.ProjectFormatException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The file side of New, Open, Save and Save As: which file the edited project belongs to,
 * and the one way a file becomes the edited project.
 *
 * <p>File &rarr; Open, a file passed on the command line and a file opened from the desktop
 * all go through {@link #open(Path)}. Nothing here shows a dialog, so the behaviour is
 * tested headlessly; {@code ProjectController} adds the questions and error messages.
 */
public final class ProjectFiles {

    private final CircuitEditor editor;
    private Path currentFile;

    public ProjectFiles(CircuitEditor editor) {
        this.editor = editor;
    }

    /** The file the edited project was opened from or last saved to. */
    public Optional<Path> currentFile() {
        return Optional.ofNullable(currentFile);
    }

    /** Replaces the edited project with an empty, unsaved one. */
    public void newProject() {
        editor.setProject(CircuitProject.empty("untitled"), false);
        currentFile = null;
    }

    /**
     * Loads {@code file} and makes it the edited project.
     *
     * @throws ProjectFormatException if the file cannot be read or is not a valid project;
     *                                the edited project is then left exactly as it was
     */
    public void open(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        CircuitProject project = ProjectFormat.load(absolute);
        // Like saving, opening names the project after its file, so the window title always
        // shows the file the user is working on.
        project.setName(nameOf(absolute));
        install(project, absolute);
    }

    /** Opens a project that has no file of its own, such as a bundled example. */
    public void openUnsaved(String displayName, String json) {
        install(ProjectFormat.fromJson(json, displayName), null);
    }

    /**
     * Saves to the current file.
     *
     * @throws IllegalStateException  if the project has no file yet
     * @throws ProjectFormatException if writing fails; the file on disk is then unchanged
     */
    public Path save() {
        if (currentFile == null) {
            throw new IllegalStateException("The project has not been saved to a file yet");
        }
        writeTo(currentFile);
        return currentFile;
    }

    /**
     * Saves to the file the user chose, adding the {@code .logic} extension if it is missing.
     *
     * @return the file actually written
     * @throws ProjectFormatException if writing fails
     */
    public Path saveAs(Path chosen) {
        Path target = saveTarget(chosen).toAbsolutePath().normalize();
        writeTo(target);
        return target;
    }

    /** The file "Save As" writes for the name the user typed; see {@link ProjectFormat#withExtension}. */
    public static Path saveTarget(Path chosen) {
        return ProjectFormat.withExtension(chosen);
    }

    private void install(CircuitProject project, Path file) {
        CircuitProject previous = editor.project();
        boolean previousDirty = editor.isDirty();
        try {
            editor.setProject(project, false);
        } catch (RuntimeException failure) {
            // Never leave the workbench half switched to a project it could not take on.
            try {
                editor.setProject(previous, previousDirty);
            } catch (RuntimeException restoreFailure) {
                failure.addSuppressed(restoreFailure);
            }
            throw new ProjectFormatException("The project could not be opened: "
                    + failure.getMessage(), failure);
        }
        currentFile = file;
    }

    private void writeTo(Path file) {
        String previousName = editor.project().name();
        editor.project().setName(nameOf(file));
        try {
            ProjectFormat.save(editor.project(), file, AtomicFileWriter.Backup.KEEP_PREVIOUS);
        } catch (RuntimeException failure) {
            editor.project().setName(previousName);
            throw failure;
        }
        currentFile = file;
        editor.markSaved();
    }

    private static String nameOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
