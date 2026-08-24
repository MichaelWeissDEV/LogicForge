package dev.logicforge.format;

/** Thrown when a project file cannot be read or written. */
public class ProjectFormatException extends RuntimeException {

    public ProjectFormatException(String message) {
        super(message);
    }

    public ProjectFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
