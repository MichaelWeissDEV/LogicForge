package dev.logicforge.circuit.chip;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Headless definition of a physical chip.
 *
 * <p>Logical units carry their complete ordinary-component configuration. The definition
 * deliberately contains no rendering or simulation state.
 */
public record ChipDefinition(ChipMetadata metadata, PackageDefinition packageDefinition,
                             List<ChipLogicalUnit> logicalUnits,
                             List<LogicalPinMapping> logicalPinMappings) {
    public ChipDefinition {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(packageDefinition, "packageDefinition");
        logicalUnits = List.copyOf(Objects.requireNonNull(logicalUnits, "logicalUnits"));
        logicalPinMappings = List.copyOf(Objects.requireNonNull(
                logicalPinMappings, "logicalPinMappings"));
        if (logicalUnits.isEmpty()) {
            throw new IllegalArgumentException("A chip requires at least one logical unit");
        }
        HashSet<String> unitNames = new HashSet<>();
        for (ChipLogicalUnit unit : logicalUnits) {
            if (!unitNames.add(unit.name())) {
                throw new IllegalArgumentException("Duplicate logical unit " + unit.name());
            }
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
            if (!unitNames.contains(mapping.unitName())) {
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

    public Optional<ChipLogicalUnit> logicalUnit(String name) {
        return logicalUnits.stream().filter(unit -> unit.name().equals(name)).findFirst();
    }
}
