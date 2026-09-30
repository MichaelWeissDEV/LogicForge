package dev.logicforge.ui.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.format.ProjectFormat;
import dev.logicforge.format.ProjectFormatException;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.ui.command.AddComponentCommand;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Opening and saving project files the way File &rarr; Open, the command line and the
 * desktop file association do — without any dialogs.
 */
class ProjectFilesTest {

    @TempDir
    Path directory;

    private final CircuitEditor editor = new CircuitEditor(ComponentRegistry.standard());
    private final ProjectFiles files = new ProjectFiles(editor);

    @Test
    void openingAFileMakesItTheEditedProject() throws IOException {
        Path file = writeProjectWithOneGate("adder.logic");

        files.open(file);

        assertEquals(Optional.of(file), files.currentFile());
        assertEquals(1, editor.document().componentCount());
        assertFalse(editor.isDirty(), "a freshly opened project has no unsaved changes");
        assertEquals("adder", editor.project().name(), "named after the file, not the stored name");
    }

    @Test
    void aRelativePathIsResolvedToAnAbsoluteOne() throws IOException {
        Path file = writeProjectWithOneGate("relative.logic");
        Path relative = Path.of("").toAbsolutePath().relativize(file);

        files.open(relative);

        assertEquals(Optional.of(file), files.currentFile());
    }

    @Test
    void anInvalidFileLeavesTheEditedProjectUntouched() throws IOException {
        addGate();
        CircuitProject before = editor.project();
        Path broken = directory.resolve("broken.logic");
        Files.writeString(broken, "{ this is not a project");

        ProjectFormatException failure = assertThrows(ProjectFormatException.class, () -> files.open(broken));

        assertTrue(failure.getMessage().contains("not a valid LogicForge project"), failure.getMessage());
        assertSame(before, editor.project());
        assertTrue(editor.isDirty(), "the unsaved change is still there");
        assertEquals(Optional.empty(), files.currentFile());
    }

    @Test
    void aMissingFileIsReportedAndChangesNothing() {
        Path missing = directory.resolve("missing.logic");

        ProjectFormatException failure = assertThrows(ProjectFormatException.class, () -> files.open(missing));

        assertTrue(failure.getMessage().contains("does not exist"), failure.getMessage());
        assertEquals(Optional.empty(), files.currentFile());
    }

    @Test
    void saveAsAddsTheExtensionAndRemembersTheFile() throws IOException {
        addGate();

        Path written = files.saveAs(directory.resolve("my-project"));

        assertEquals(directory.resolve("my-project.logic"), written);
        assertEquals(Optional.of(written), files.currentFile());
        assertFalse(editor.isDirty());
        assertEquals("my-project", editor.project().name(), "the project is named after its file");
        assertFalse(Files.exists(directory.resolve("my-project")), "no file without extension");
    }

    @Test
    void saveAsKeepsAnExistingExtension() {
        Path written = files.saveAs(directory.resolve("my-project.logic"));

        assertEquals(directory.resolve("my-project.logic"), written);
        assertFalse(Files.exists(directory.resolve("my-project.logic.logic")));
    }

    @Test
    void savingAndReopeningGivesBackTheSameProject() throws IOException {
        addGate();
        Path file = files.saveAs(directory.resolve("roundtrip.logic"));
        String saved = ProjectFormat.toJson(editor.project());

        files.newProject();
        files.open(file);

        assertEquals(saved, ProjectFormat.toJson(editor.project()));
        assertEquals(saved, Files.readString(file));
    }

    @Test
    void saveReplacesTheFileAndKeepsTheOldVersionAsBackup() throws IOException {
        Path file = writeProjectWithOneGate("project.logic");
        String original = Files.readString(file);
        files.open(file);
        addGate();

        files.save();

        assertEquals(2, ProjectFormat.load(file).mainCircuit().componentCount());
        assertEquals(original, Files.readString(directory.resolve("project.logic.bak")));
    }

    @Test
    void aFailedSaveKeepsTheChangesUnsavedAndTheNameUnchanged() {
        addGate();
        String nameBefore = editor.project().name();
        Path impossible = directory.resolve("no-such-folder").resolve("x.logic");

        assertThrows(ProjectFormatException.class, () -> files.saveAs(impossible));

        assertTrue(editor.isDirty());
        assertEquals(nameBefore, editor.project().name());
        assertEquals(Optional.empty(), files.currentFile());
    }

    @Test
    void saveWithoutAFileIsAProgrammingError() {
        assertThrows(IllegalStateException.class, files::save);
    }

    @Test
    void aBundledProjectHasNoFile() throws IOException {
        Path file = writeProjectWithOneGate("first.logic");
        files.open(file);

        files.openUnsaved("example", ProjectFormat.toJson(CircuitProject.empty("example")));

        assertEquals(Optional.empty(), files.currentFile());
        assertEquals("example", editor.project().name());
    }

    private Path writeProjectWithOneGate(String name) throws IOException {
        CircuitProject project = CircuitProject.empty("fixture");
        project.mainCircuit().addComponent(ComponentInstance.create("logic.and", new CircuitPoint(0, 0),
                ComponentRegistry.standard().definition("logic.and").orElseThrow().defaultParameters()));
        Path file = directory.resolve(name);
        Files.writeString(file, ProjectFormat.toJson(project));
        return file;
    }

    private void addGate() {
        editor.execute(new AddComponentCommand(editor.document(), ComponentInstance.create("logic.or",
                new CircuitPoint(40, 40),
                ComponentRegistry.standard().definition("logic.or").orElseThrow().defaultParameters())));
    }
}
