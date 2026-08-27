package dev.logicforge.circuit.chip;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Headless definition of a physical chip.
 *
 * <p>{@code logicalUnits} maps unit names such as {@code 1} to ordinary LogicForge
 * component definition ids. It deliberately contains no rendering or simulation state.
 */
public record ChipDefinition(ChipMetadata metadata, PackageDefinition packageDefinition,
                             Map<String, String> logicalUnits,
                             List<LogicalPinMapping> logicalPinMappings) {
    public ChipDefinition {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(packageDefinition, "packageDefinition");
        logicalUnits = Map.copyOf(Objects.requireNonNull(logicalUnits, "logicalUnits"));
        logicalPinMappings = List.copyOf(Objects.requireNonNull(
                logicalPinMappings, "logicalPinMappings"));
        if (logicalUnits.isEmpty()) {
            throw new IllegalArgumentException("A chip requires at least one logical unit");
        }
        HashSet<Integer> mappedPins = new HashSet<>();
        for (LogicalPinMapping mapping : logicalPinMappings) {
            PackagePin pin = packageDefinition.pin(mapping.physicalPinNumber())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Mapping references absent physical pin " + mapping.physicalPinNumber()));
            if (pin.electricalType() != ElectricalPinType.SIGNAL) {
                throw new IllegalArgumentException("Only signal pins can map to logical ports: "
                        + mapping.physicalPinNumber());
            }
            if (!logicalUnits.containsKey(mapping.unitName())) {
                throw new IllegalArgumentException("Unknown logical unit " + mapping.unitName());
            }
            if (!mappedPins.add(mapping.physicalPinNumber())) {
                throw new IllegalArgumentException("Physical signal pin mapped twice: "
                        + mapping.physicalPinNumber());
            }
        }
        for (PackagePin pin : packageDefinition.pins()) {
            if (pin.electricalType() == ElectricalPinType.SIGNAL
                    && !mappedPins.contains(pin.number())) {
                throw new IllegalArgumentException("Unmapped signal pin " + pin.number());
            }
        }
    }

    public Optional<LogicalPinMapping> logicalMapping(int physicalPinNumber) {
        return logicalPinMappings.stream()
                .filter(mapping -> mapping.physicalPinNumber() == physicalPinNumber)
                .findFirst();
    }
}
