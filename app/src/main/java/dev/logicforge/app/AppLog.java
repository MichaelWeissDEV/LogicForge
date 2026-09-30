package dev.logicforge.app;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A small local log for problems the user may want to report.
 *
 * <p>Entries go to a file in the state directory and to standard error. The file and its
 * directory are only created when there is something to write, the file is rotated once it
 * grows past {@link #MAX_BYTES}, and nothing ever leaves the machine.
 */
public final class AppLog {

    static final long MAX_BYTES = 1024 * 1024;

    private final Path file;

    public AppLog(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public void info(String message) {
        write("INFO", message, null);
    }

    public void error(String message, Throwable failure) {
        write("ERROR", message, failure);
    }

    /** The full stack trace of {@code failure}, including causes and suppressed exceptions. */
    public static String stackTrace(Throwable failure) {
        StringWriter text = new StringWriter();
        failure.printStackTrace(new PrintWriter(text));
        return text.toString();
    }

    private synchronized void write(String level, String message, Throwable failure) {
        StringBuilder entry = new StringBuilder()
                .append(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .append(' ').append(level)
                .append(" [").append(Thread.currentThread().getName()).append("] ")
                .append(message).append(System.lineSeparator());
        if (failure != null) {
            entry.append(stackTrace(failure));
        }
        System.err.print(entry);
        try {
            Files.createDirectories(file.getParent());
            rotateIfNeeded();
            Files.writeString(file, entry, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException unwritable) {
            // Logging must never become the next failure; standard error already has it.
            System.err.println("LogicForge could not write its log file " + file + ": " + unwritable);
        }
    }

    private void rotateIfNeeded() throws IOException {
        if (Files.isRegularFile(file) && Files.size(file) > MAX_BYTES) {
            Files.move(file, file.resolveSibling(file.getFileName() + ".1"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
