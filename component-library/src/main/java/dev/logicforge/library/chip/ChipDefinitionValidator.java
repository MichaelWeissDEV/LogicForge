package dev.logicforge.library.chip;

import dev.logicforge.circuit.chip.ChipDefinition;
import dev.logicforge.circuit.chip.ChipLogicalUnit;
import dev.logicforge.circuit.chip.ElectricalPinType;
import dev.logicforge.circuit.component.ParameterSpec;
import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.library.ComponentRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/** Registry-aware electrical and component-contract validation for physical chips. */
public final class ChipDefinitionValidator {

    private final ComponentRegistry components;

    public ChipDefinitionValidator(ComponentRegistry components) {
        this.components = Objects.requireNonNull(components, "components");
    }

    public void validate(ChipDefinition chip) {
        Objects.requireNonNull(chip, "chip");
        Map<String, UnitContract> units = new HashMap<>();
        for (ChipLogicalUnit unit : chip.logicalUnits()) {
            var definition = components.require(unit.componentDefinitionId()).definition();
            ParameterValues effective = definition.defaultParameters();
            Map<String, ParameterSpec<?>> specs = new HashMap<>();
            for (ParameterSpec<?> spec : definition.parameters()) {
                specs.put(spec.key(), spec);
            }
            for (var entry : unit.parameters().asMap().entrySet()) {
                ParameterSpec<?> spec = specs.get(entry.getKey());
                if (spec == null) {
                    throw invalid(chip, "unit " + unit.name() + " has unknown parameter "
                            + entry.getKey());
                }
                Object coerced;
                try {
                    coerced = spec.coerce(entry.getValue());
                } catch (IllegalArgumentException failure) {
                    throw invalid(chip, "unit " + unit.name() + " has invalid parameter "
                            + entry.getKey() + ": " + failure.getMessage());
                }
                Object normalizedRaw = ParameterValues.of(Map.of("value", entry.getValue()))
                        .asMap().get("value");
                Object normalizedCoerced = ParameterValues.of(Map.of("value", coerced))
                        .asMap().get("value");
                if (!Objects.equals(normalizedRaw, normalizedCoerced)) {
                    throw invalid(chip, "unit " + unit.name() + " parameter "
                            + entry.getKey() + " is outside its declared contract");
                }
                effective = effective.with(entry.getKey(), coerced);
            }
            units.put(unit.name(), new UnitContract(definition, effective));
        }

        HashSet<String> logicalEndpoints = new HashSet<>();
        for (var mapping : chip.logicalPinMappings()) {
            var pin = chip.packageDefinition().pin(mapping.physicalPinNumber()).orElseThrow();
            if (pin.electricalType() != ElectricalPinType.SIGNAL) {
                throw invalid(chip, "power, ground and NC pins cannot map as signals: "
                        + pin.number());
            }
            UnitContract unit = units.get(mapping.unitName());
            if (unit == null) {
                throw invalid(chip, "mapping references unknown unit " + mapping.unitName());
            }
            var port = unit.definition().port(unit.parameters(), mapping.portName())
                    .orElseThrow(() -> invalid(chip, "unit " + mapping.unitName()
                            + " has no port " + mapping.portName()));
            int width = port.width().bits();
            if (width == 1) {
                if (mapping.bitIndex() > 0) {
                    throw invalid(chip, "scalar port " + mapping.portName()
                            + " cannot map bit " + mapping.bitIndex());
                }
            } else if (mapping.bitIndex() < 0 || mapping.bitIndex() >= width) {
                throw invalid(chip, "vector port " + mapping.portName()
                        + " requires a bit index in 0.." + (width - 1));
            }
            if (mapping.direction() != null && mapping.direction() != port.direction()) {
                throw invalid(chip, "pin " + pin.number() + " declares " + mapping.direction()
                        + " but logical port is " + port.direction());
            }
            String endpoint = mapping.unitName() + ":" + mapping.portName() + ":"
                    + Math.max(0, mapping.bitIndex());
            if (!logicalEndpoints.add(endpoint)) {
                throw invalid(chip, "logical port bit mapped more than once: " + endpoint);
            }
        }
    }

    private static IllegalArgumentException invalid(ChipDefinition chip, String message) {
        return new IllegalArgumentException(chip.metadata().partNumber() + ": " + message);
    }

    private record UnitContract(dev.logicforge.circuit.component.ComponentDefinition definition,
                                ParameterValues parameters) {
    }
}
