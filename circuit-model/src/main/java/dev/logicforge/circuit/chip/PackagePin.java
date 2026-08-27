package dev.logicforge.circuit.chip;

import java.util.Objects;

/** One numbered physical pin and its electrical role. */
public record PackagePin(int number, String name, ElectricalPinType electricalType) {
    public PackagePin {
        if (number < 1) {
            throw new IllegalArgumentException("Pin number must be positive");
        }
        name = Objects.requireNonNull(name, "name").strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Pin name must not be blank");
        }
        Objects.requireNonNull(electricalType, "electricalType");
    }
}
