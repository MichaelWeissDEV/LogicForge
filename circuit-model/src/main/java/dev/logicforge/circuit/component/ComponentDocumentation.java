package dev.logicforge.circuit.component;

import java.util.Objects;
import java.util.Optional;

/** Optional educational material attached to a component definition. */
public record ComponentDocumentation(String summary, String operation,
                                     Optional<TruthTableDefinition> truthTable,
                                     String timingNotes, String invalidStates,
                                     String implementationNotes) {
    public static final ComponentDocumentation EMPTY = new ComponentDocumentation(
            "", "", Optional.empty(), "", "", "");

    public ComponentDocumentation {
        summary = text(summary);
        operation = text(operation);
        truthTable = truthTable == null ? Optional.empty() : truthTable;
        timingNotes = text(timingNotes);
        invalidStates = text(invalidStates);
        implementationNotes = text(implementationNotes);
    }

    public boolean isEmpty() {
        return summary.isEmpty() && operation.isEmpty() && truthTable.isEmpty()
                && timingNotes.isEmpty() && invalidStates.isEmpty()
                && implementationNotes.isEmpty();
    }

    private static String text(String value) {
        return Objects.requireNonNullElse(value, "").strip();
    }
}
