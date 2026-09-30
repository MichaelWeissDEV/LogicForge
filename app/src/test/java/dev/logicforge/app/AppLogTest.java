package dev.logicforge.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppLogTest {

    @TempDir
    Path directory;

    @Test
    void theLogFileAndItsFolderAppearOnlyWithTheFirstEntry() throws IOException {
        Path file = directory.resolve("state/logicforge/logicforge.log");
        AppLog log = new AppLog(file);
        assertFalse(Files.exists(file.getParent()));

        log.error("Something failed", new IllegalStateException("broken invariant"));

        String content = Files.readString(file);
        assertTrue(content.contains("ERROR"), content);
        assertTrue(content.contains("Something failed"), content);
        assertTrue(content.contains("java.lang.IllegalStateException: broken invariant"), content);
        assertTrue(content.contains("\tat "), "the stack trace is kept for bug reports");
    }

    @Test
    void aLargeLogIsRotated() throws IOException {
        Path file = directory.resolve("logicforge.log");
        Files.writeString(file, "x".repeat((int) AppLog.MAX_BYTES + 1));

        new AppLog(file).info("fresh start");

        assertTrue(Files.readString(file).contains("fresh start"));
        assertTrue(Files.size(file) < 1024);
        assertEquals(AppLog.MAX_BYTES + 1, Files.size(directory.resolve("logicforge.log.1")));
    }

    @Test
    void anUnwritableLogNeverThrows() throws IOException {
        Path blocker = Files.writeString(directory.resolve("not-a-folder"), "");

        new AppLog(blocker.resolve("logicforge.log")).error("still fine", new RuntimeException("x"));
    }
}
