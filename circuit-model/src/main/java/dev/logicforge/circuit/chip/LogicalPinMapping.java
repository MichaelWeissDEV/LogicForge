package dev.logicforge.circuit.chip;

import java.util.Objects;
import dev.logicforge.circuit.component.PortDirection;

/** Maps a physical signal pin to a port on one logical unit inside the chip. */
public record LogicalPinMapping(int physicalPinNumber, String unitName, String portName,
                                int bitIndex, PortDirection direction) {
    public LogicalPinMapping {
        if (physicalPinNumber < 1) {
            throw new IllegalArgumentException("Physical pin number must be positive");
        }
        unitName = requireName(unitName, "unitName");
        portName = requireName(portName, "portName");
        if (bitIndex < -1) {
            throw new IllegalArgumentException("Bit index must be -1 or non-negative");
        }
    }

    /** Maps an entire scalar port; direction is checked from the component definition. */
    public LogicalPinMapping(int physicalPinNumber, String unitName, String portName) {
        this(physicalPinNumber, unitName, portName, -1, null);
    }

    /** Maps one bit of a vector port. */
    public LogicalPinMapping(int physicalPinNumber, String unitName, String portName,
                             int bitIndex) {
        this(physicalPinNumber, unitName, portName, bitIndex, null);
    }

    private static String requireName(String value, String field) {
        String result = Objects.requireNonNull(value, field).strip();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return result;
    }
}
