package dev.logicforge.compiler;

import java.util.UUID;

/**
 * Something the compiler noticed about a circuit, attached to the element it concerns so
 * the editor can point at it.
 */
public record ValidationIssue(
        Severity severity,
        String message,
        UUID componentId,
        String portName,
        UUID connectionId) {

    public enum Severity {
        /** Worth knowing, e.g. a net nobody drives. */
        INFO,
        /** Probably a mistake, but the circuit still runs. */
        WARNING,
        /** The circuit cannot be compiled. */
        ERROR
    }

    public static ValidationIssue error(String message, UUID componentId, String portName) {
        return new ValidationIssue(Severity.ERROR, message, componentId, portName, null);
    }

    public static ValidationIssue warning(String message, UUID componentId, String portName) {
        return new ValidationIssue(Severity.WARNING, message, componentId, portName, null);
    }

    public static ValidationIssue info(String message, UUID componentId, String portName) {
        return new ValidationIssue(Severity.INFO, message, componentId, portName, null);
    }

    public static ValidationIssue forConnection(Severity severity, String message, UUID connectionId) {
        return new ValidationIssue(severity, message, null, null, connectionId);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    @Override
    public String toString() {
        return severity + ": " + message;
    }
}
