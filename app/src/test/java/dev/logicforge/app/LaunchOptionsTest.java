package dev.logicforge.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LaunchOptionsTest {

    private static final Path WORKING_DIRECTORY = Path.of("/home/someone/work");

    @Test
    void noArgumentsStartsAnEmptyWorkbench() {
        LaunchOptions options = parse();

        assertEquals(LaunchOptions.Mode.GUI, options.mode());
        assertEquals(Optional.empty(), options.file());
        assertEquals(List.of(), options.ignoredFiles());
    }

    @Test
    void aRelativeFileIsResolvedAgainstTheWorkingDirectory() {
        LaunchOptions options = parse("foo.logic");

        assertEquals(Optional.of(Path.of("/home/someone/work/foo.logic")), options.file());
        assertEquals(LaunchOptions.Mode.GUI, options.mode());
    }

    @Test
    void pathsAreNormalised() {
        assertEquals(Optional.of(Path.of("/home/someone/other/foo.logic")),
                parse("../other/./foo.logic").file());
    }

    @Test
    void anAbsoluteFileIsKept() {
        assertEquals(Optional.of(Path.of("/tmp/circuits/adder.logic")), parse("/tmp/circuits/adder.logic").file());
    }

    @Test
    void aFileUriFromAFileManagerIsAccepted() {
        assertEquals(Optional.of(Path.of("/home/someone/My Circuits/adder.logic")),
                parse("file:///home/someone/My%20Circuits/adder.logic").file());
    }

    @Test
    void aFileNamedLikeAnOptionCanFollowTheSeparator() {
        LaunchOptions options = parse("--", "-strange.logic");

        assertEquals(Optional.of(WORKING_DIRECTORY.resolve("-strange.logic")), options.file());
    }

    @Test
    void onlyTheFirstFileIsOpenedAndTheRestAreReported() {
        LaunchOptions options = parse("a.logic", "b.logic", "c.logic");

        assertEquals(Optional.of(WORKING_DIRECTORY.resolve("a.logic")), options.file());
        assertEquals(List.of("/home/someone/work/b.logic", "/home/someone/work/c.logic"), options.ignoredFiles());
    }

    @Test
    void versionAndHelpNeedNoFile() {
        assertEquals(LaunchOptions.Mode.VERSION, parse("--version").mode());
        assertEquals(LaunchOptions.Mode.VERSION, parse("-V").mode());
        assertEquals(LaunchOptions.Mode.HELP, parse("--help").mode());
        assertEquals(LaunchOptions.Mode.HELP, parse("-h", "--version").mode(), "help wins");
    }

    @Test
    void theSmokeTestTakesAnOptionalFile() {
        assertEquals(LaunchOptions.Mode.SMOKE_TEST, parse("--smoke-test").mode());
        LaunchOptions withFile = parse("--smoke-test", "x.logic");
        assertEquals(LaunchOptions.Mode.SMOKE_TEST, withFile.mode());
        assertEquals(Optional.of(WORKING_DIRECTORY.resolve("x.logic")), withFile.file());
    }

    @Test
    void unknownOptionsAreRejectedWithTheirName() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> parse("--bogus"));

        assertTrue(failure.getMessage().contains("--bogus"), failure.getMessage());
    }

    @Test
    void malformedFileNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> parse(""));
        assertThrows(IllegalArgumentException.class, () -> parse("bad\u0000name.logic"));
        assertThrows(IllegalArgumentException.class, () -> parse("file:relative.logic"));
    }

    @Test
    void theUsageTextNamesTheCommand() {
        assertTrue(LaunchOptions.USAGE.startsWith("Usage: logicforge"));
        assertTrue(!LaunchOptions.USAGE.contains("smoke"), "the smoke test is not a user feature");
    }

    private static LaunchOptions parse(String... arguments) {
        return LaunchOptions.parse(List.of(arguments), WORKING_DIRECTORY);
    }
}
