package dev.logicforge.compiler;

import java.util.List;

/** Thrown when a circuit cannot be turned into a runnable form. */
public class CircuitCompileException extends RuntimeException {

    private final List<ValidationIssue> issues;

    public CircuitCompileException(List<ValidationIssue> issues) {
        super(issues.stream().filter(ValidationIssue::isError)
                .map(ValidationIssue::message)
                .reduce((a, b) -> a + "; " + b)
                .orElse("Circuit could not be compiled"));
        this.issues = List.copyOf(issues);
    }

    /** Every issue found, including the warnings that accompany the errors. */
    public List<ValidationIssue> issues() {
        return issues;
    }

    public List<ValidationIssue> errors() {
        return issues.stream().filter(ValidationIssue::isError).toList();
    }
}
