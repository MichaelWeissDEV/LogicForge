package dev.logicforge.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.document.CircuitProject;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/** Saving and loading project files on disk, and files that are not what they claim to be. */
class ProjectFileHandlingTest {

    private static final String A = "11111111-1111-1111-1111-111111111111";
    private static final String B = "22222222-2222-2222-2222-222222222222";

    @TempDir
    Path directory;

    @ParameterizedTest(name = "\"{0}\" is saved as \"{1}\"")
    @CsvSource({
            "my-project, my-project.logic",
            "my-project.logic, my-project.logic",
            "My-Project.LOGIC, My-Project.LOGIC",
            "my.project, my.project.logic",
            "adder.v2, adder.v2.logic",
            "trailing., trailing.logic",
            "notes.logic.txt, notes.logic.txt.logic",
            ".logic, .logic.logic"
    })
    void saveAsAddsTheExtensionOnlyWhenItIsMissing(String typed, String expected) {
        Path folder = Path.of("/home/someone/projects");

        Path result = ProjectFormat.withExtension(folder.resolve(typed));

        assertEquals(folder.resolve(expected), result);
        assertEquals(result, ProjectFormat.withExtension(result), "normalising twice changes nothing");
    }

    @Test
    void savingGoesThroughTheAtomicWriterAndReadsBack() throws IOException {
        CircuitProject project = ProjectFormat.fromJson(minimalProject("logic.and"), "p");
        Path file = directory.resolve("p.logic");
        Files.writeString(file, "previous content");

        ProjectFormat.save(project, file, AtomicFileWriter.Backup.KEEP_PREVIOUS);

        assertEquals(ProjectFormat.toJson(project), Files.readString(file));
        assertEquals("previous content", Files.readString(directory.resolve("p.logic.bak")));
        assertTrue(ProjectFormat.load(file).mainCircuit().structurallyEquals(project.mainCircuit()));
    }

    @Test
    void savingIntoAMissingFolderFailsReadablyAndCreatesNothing() {
        CircuitProject project = CircuitProject.empty("p");
        Path file = directory.resolve("no-such-folder").resolve("p.logic");

        ProjectFormatException failure = assertThrows(ProjectFormatException.class,
                () -> ProjectFormat.save(project, file));

        assertTrue(failure.getMessage().startsWith("Could not write"), failure.getMessage());
        assertFalse(Files.exists(file.getParent()));
    }

    @Test
    void aMissingFileIsReportedByName() {
        Path file = directory.resolve("nothing-here.logic");

        ProjectFormatException failure = assertThrows(ProjectFormatException.class,
                () -> ProjectFormat.load(file));

        assertTrue(failure.getMessage().contains("does not exist"), failure.getMessage());
        assertTrue(failure.getMessage().contains("nothing-here.logic"), failure.getMessage());
    }

    @Test
    void aFolderIsNotAProject() throws IOException {
        Path folder = Files.createDirectory(directory.resolve("folder.logic"));

        ProjectFormatException failure = assertThrows(ProjectFormatException.class,
                () -> ProjectFormat.load(folder));

        assertTrue(failure.getMessage().contains("is not a file"), failure.getMessage());
    }

    @Test
    void aFileThatIsNotTextIsRejected() throws IOException {
        Path file = directory.resolve("binary.logic");
        Files.write(file, new byte[]{(byte) 0xff, (byte) 0xfe, 0x00, (byte) 0xc3, 0x28});

        ProjectFormatException failure = assertThrows(ProjectFormatException.class,
                () -> ProjectFormat.load(file));

        assertTrue(failure.getMessage().contains("not UTF-8"), failure.getMessage());
    }

    @Test
    void aHugeFileIsRejectedBeforeItIsRead() throws IOException {
        Path file = directory.resolve("huge.logic");
        try (RandomAccessFile sparse = new RandomAccessFile(file.toFile(), "rw")) {
            sparse.setLength(ProjectFormat.MAX_FILE_BYTES + 1);
        }

        ProjectFormatException failure = assertThrows(ProjectFormatException.class,
                () -> ProjectFormat.load(file));

        assertTrue(failure.getMessage().contains("too large"), failure.getMessage());
    }

    static Stream<Arguments> damagedProjects() {
        return Stream.of(
                Arguments.of("rotation that is not a multiple of 90°",
                        wrap("{\"id\":\"" + A + "\",\"type\":\"logic.and\",\"rotation\":45}", "")),
                Arguments.of("two components with the same id",
                        wrap("{\"id\":\"" + A + "\",\"type\":\"logic.and\"},"
                                + "{\"id\":\"" + A + "\",\"type\":\"logic.or\"}", "")),
                Arguments.of("negative bit index",
                        wrap(twoComponents(), "{\"from\":{\"component\":\"" + A + "\",\"port\":\"OUT\","
                                + "\"bit\":-1},\"to\":{\"component\":\"" + B + "\",\"port\":\"IN\"}}")),
                Arguments.of("a wire from a port to itself",
                        wrap("{\"id\":\"" + A + "\",\"type\":\"logic.and\"}",
                                "{\"from\":{\"component\":\"" + A + "\",\"port\":\"OUT\"},"
                                        + "\"to\":{\"component\":\"" + A + "\",\"port\":\"OUT\"}}")),
                Arguments.of("a chip without reference designator",
                        "{\"formatVersion\":5,\"circuits\":[{\"name\":\"main\","
                                + "\"chips\":[{\"id\":\"" + A + "\",\"type\":\"74HC00\"}]}]}"),
                Arguments.of("an invalid id", wrap("{\"id\":\"not-a-uuid\",\"type\":\"logic.and\"}", "")),
                Arguments.of("a negative format version", "{\"formatVersion\":-3,\"circuits\":[]}"),
                Arguments.of("deeply nested arrays", "{\"formatVersion\":5,\"x\":" + "[".repeat(100_000)),
                Arguments.of("a truncated unicode escape", "{\"formatVersion\":5,\"name\":\"\\u12"),
                Arguments.of("an invalid unicode escape", "{\"formatVersion\":5,\"name\":\"\\uZZZZ\"}"),
                Arguments.of("an escape at the end of the file", "{\"formatVersion\":5,\"name\":\"\\"),
                Arguments.of("an empty file", ""),
                Arguments.of("not JSON at all", "PK\u0003\u0004 this is a zip file"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedProjects")
    void damagedFilesAlwaysFailAsAProjectFormatException(String description, String content)
            throws IOException {
        Path file = directory.resolve("damaged.logic");
        Files.writeString(file, content, StandardCharsets.UTF_8);

        ProjectFormatException failure = assertThrows(ProjectFormatException.class,
                () -> ProjectFormat.load(file), description);

        assertFalse(failure.getMessage().isBlank());
    }

    private static String twoComponents() {
        return "{\"id\":\"" + A + "\",\"type\":\"logic.and\"},{\"id\":\"" + B + "\",\"type\":\"output.led\"}";
    }

    private static String minimalProject(String type) {
        return wrap("{\"id\":\"" + A + "\",\"type\":\"" + type + "\",\"x\":10,\"y\":20}", "");
    }

    private static String wrap(String components, String connections) {
        return "{\"formatVersion\":5,\"name\":\"x\",\"circuits\":[{\"name\":\"main\",\"components\":["
                + components + "],\"connections\":[" + connections + "]}]}";
    }
}
