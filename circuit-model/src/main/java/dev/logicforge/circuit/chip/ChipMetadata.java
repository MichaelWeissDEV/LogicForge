package dev.logicforge.circuit.chip;

import java.util.List;
import java.util.Objects;

/** Human-facing identity of a packaged integrated circuit. */
public record ChipMetadata(String partNumber, String family, String summary, List<String> tags) {
    public ChipMetadata {
        partNumber = requireText(partNumber, "partNumber");
        family = requireText(family, "family");
        summary = Objects.requireNonNull(summary, "summary").strip();
        tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
    }

    private static String requireText(String value, String field) {
        String result = Objects.requireNonNull(value, field).strip();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return result;
    }
}
