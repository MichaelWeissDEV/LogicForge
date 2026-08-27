package dev.logicforge.circuit.chip;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Physical package outline and its complete, numbered pin list. */
public record PackageDefinition(PackageType type, List<PackagePin> pins) {
    public PackageDefinition {
        Objects.requireNonNull(type, "type");
        pins = List.copyOf(Objects.requireNonNull(pins, "pins"));
        if (pins.size() != type.pinCount()) {
            throw new IllegalArgumentException(type + " requires exactly "
                    + type.pinCount() + " pins");
        }
        HashSet<Integer> numbers = new HashSet<>();
        for (PackagePin pin : pins) {
            if (pin.number() > type.pinCount() || !numbers.add(pin.number())) {
                throw new IllegalArgumentException("Invalid or duplicate package pin " + pin.number());
            }
        }
        for (int number = 1; number <= type.pinCount(); number++) {
            if (!numbers.contains(number)) {
                throw new IllegalArgumentException("Missing package pin " + number);
            }
        }
        ArrayList<PackagePin> ordered = new ArrayList<>(pins);
        ordered.sort(Comparator.comparingInt(PackagePin::number));
        pins = List.copyOf(ordered);
    }

    public Optional<PackagePin> pin(int number) {
        return number >= 1 && number <= pins.size()
                ? Optional.of(pins.get(number - 1)) : Optional.empty();
    }
}
