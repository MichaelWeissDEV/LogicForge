package dev.logicforge.circuit.chip;

import dev.logicforge.circuit.component.ParameterValues;
import java.util.Objects;

/** One independently functional logical unit contained in a physical chip package. */
public record ChipLogicalUnit(String name, String componentDefinitionId,
                              ParameterValues parameters) {
    public ChipLogicalUnit {
        name = requireText(name, "name");
        componentDefinitionId = requireText(componentDefinitionId, "componentDefinitionId");
        parameters = parameters == null ? ParameterValues.empty() : parameters;
    }

    private static String requireText(String value, String field) {
        String result = Objects.requireNonNull(value, field).strip();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return result;
    }
}
