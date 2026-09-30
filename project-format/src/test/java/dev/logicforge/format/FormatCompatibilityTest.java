package dev.logicforge.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.format.json.JsonParser;
import dev.logicforge.format.json.JsonValue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every project file that ships — old format versions included — loads, is written in the
 * current format through the real (atomic) save path, and reads back as the same project.
 */
class FormatCompatibilityTest {

    private static final Path EXAMPLES =
            Path.of(System.getProperty("logicforge.examples", "../examples"));

    @TempDir
    Path directory;

    static Stream<Path> everyExample() throws IOException {
        try (Stream<Path> files = Files.walk(EXAMPLES)) {
            return files.filter(file -> file.toString().endsWith("." + ProjectFormat.EXTENSION))
                    .sorted()
                    .toList()
                    .stream();
        }
    }

    @Test
    void theExamplesCoverAnOldAndTheCurrentFormatVersion() throws IOException {
        var versions = everyExample().map(FormatCompatibilityTest::formatVersionOf).distinct().toList();

        assertTrue(versions.contains(1), "an original format-1 project is kept as a fixture: " + versions);
        assertTrue(versions.contains(ProjectFormat.FORMAT_VERSION), versions.toString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyExample")
    void loadSaveReloadKeepsTheProject(Path example) {
        CircuitProject original = ProjectFormat.load(example);
        Path copy = directory.resolve(example.getFileName());

        ProjectFormat.save(original, copy);
        CircuitProject reloaded = ProjectFormat.load(copy);

        assertEquals(ProjectFormat.FORMAT_VERSION, formatVersionOf(copy), "saved in the current format");
        assertEquals(original.name(), reloaded.name());
        assertEquals(original.circuitNames(), reloaded.circuitNames());
        for (CircuitDocument circuit : original.circuits()) {
            CircuitDocument again = reloaded.circuit(circuit.metadata().name()).orElseThrow();
            assertTrue(circuit.structurallyEquals(again),
                    circuit.metadata().name() + " in " + example + " changed on a save/reload cycle");
        }
        assertEquals(ProjectFormat.toJson(original), ProjectFormat.toJson(reloaded),
                "a second save must produce the same file");
    }

    private static int formatVersionOf(Path file) {
        try {
            JsonValue root = JsonParser.parse(Files.readString(file));
            return ((JsonValue.JsonObject) root).integer("formatVersion", 0);
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }
}
