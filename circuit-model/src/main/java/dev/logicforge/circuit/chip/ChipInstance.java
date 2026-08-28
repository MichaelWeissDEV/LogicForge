package dev.logicforge.circuit.chip;

import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.Rotation;
import java.util.Objects;
import java.util.UUID;

/** One editor-visible physical package shared by all of its logical units. */
public record ChipInstance(UUID id, String chipDefinitionId, CircuitPoint position,
                           Rotation rotation, String referenceDesignator,
                           ChipDisplayMode displayMode) {
    public ChipInstance {
        Objects.requireNonNull(id, "id");
        chipDefinitionId = requireText(chipDefinitionId, "chipDefinitionId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rotation, "rotation");
        referenceDesignator = requireText(referenceDesignator, "referenceDesignator");
        Objects.requireNonNull(displayMode, "displayMode");
    }

    public static ChipInstance create(String chipDefinitionId, CircuitPoint position,
                                      String referenceDesignator) {
        return new ChipInstance(UUID.randomUUID(), chipDefinitionId, position, Rotation.DEG_0,
                referenceDesignator, ChipDisplayMode.PACKAGE);
    }

    public ChipInstance withPosition(CircuitPoint newPosition) {
        return new ChipInstance(id, chipDefinitionId, newPosition, rotation, referenceDesignator,
                displayMode);
    }

    public ChipInstance withRotation(Rotation newRotation) {
        return new ChipInstance(id, chipDefinitionId, position, newRotation, referenceDesignator,
                displayMode);
    }

    public ChipInstance withDisplayMode(ChipDisplayMode newMode) {
        return new ChipInstance(id, chipDefinitionId, position, rotation, referenceDesignator,
                newMode);
    }

    public ChipInstance withReferenceDesignator(String newDesignator) {
        return new ChipInstance(id, chipDefinitionId, position, rotation, newDesignator, displayMode);
    }

    private static String requireText(String value, String field) {
        String result = Objects.requireNonNull(value, field).strip();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return result;
    }
}
