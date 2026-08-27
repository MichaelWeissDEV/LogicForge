package dev.logicforge.circuit.chip;

import java.util.Objects;

/** Maps a physical signal pin to a port on one logical unit inside the chip. */
public record LogicalPinMapping(int physicalPinNumber, String unitName, String portName) {
    public LogicalPinMapping {
        if (physicalPinNumber < 1) {
            throw new IllegalArgumentException("Physical pin number must be positive");
        }
        unitName = requireName(unitName, "unitName");
        portName = requireName(portName, "portName");
    }

    private static String requireName(String value, String field) {
        String result = Objects.requireNonNull(value, field).strip();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return result;
    }
}
