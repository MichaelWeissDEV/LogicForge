package dev.logicforge.tools.lf8;

/** A syntax or semantic error found while assembling LF-8 source, with source location. */
public final class Lf8AssemblyException extends RuntimeException {

    private final int line;
    private final String sourceText;

    public Lf8AssemblyException(int line, String sourceText, String message) {
        super(formatMessage(line, sourceText, message));
        this.line = line;
        this.sourceText = sourceText == null ? "" : sourceText;
    }

    /** 1-based source line the error was found on. */
    public int line() {
        return line;
    }

    /** The raw (unmodified) source line the error was found on. */
    public String sourceText() {
        return sourceText;
    }

    private static String formatMessage(int line, String sourceText, String message) {
        String trimmed = sourceText == null ? "" : sourceText.trim();
        return "line " + line + ": " + message + (trimmed.isEmpty() ? "" : " (\"" + trimmed + "\")");
    }
}
