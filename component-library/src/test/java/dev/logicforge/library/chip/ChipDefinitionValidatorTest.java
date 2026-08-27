package dev.logicforge.library.chip;

import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.logicforge.circuit.chip.ChipDefinition;
import dev.logicforge.circuit.chip.ChipLogicalUnit;
import dev.logicforge.circuit.chip.LogicalPinMapping;
import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.library.ComponentRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChipDefinitionValidatorTest {

    private final ChipDefinitionValidator validator =
            new ChipDefinitionValidator(ComponentRegistry.standard());

    @Test
    void rejectsUnknownLogicalPort() {
        ChipDefinition valid = StandardChipLibrary.create().require("74HC00");
        ArrayList<LogicalPinMapping> mappings = new ArrayList<>(valid.logicalPinMappings());
        LogicalPinMapping old = mappings.getFirst();
        mappings.set(0, new LogicalPinMapping(old.physicalPinNumber(), old.unitName(),
                "MISSING", old.bitIndex(), old.direction()));
        ChipDefinition invalid = new ChipDefinition(valid.metadata(), valid.packageDefinition(),
                valid.logicalUnits(), mappings);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(invalid));
    }

    @Test
    void rejectsUnknownUnitParameter() {
        ChipDefinition valid = StandardChipLibrary.create().require("74HC283");
        ChipLogicalUnit invalidUnit = new ChipLogicalUnit("ADD", "arithmetic.adder",
                ParameterValues.of(java.util.Map.of("not-a-parameter", 4)));
        ChipDefinition invalid = new ChipDefinition(valid.metadata(), valid.packageDefinition(),
                List.of(invalidUnit), valid.logicalPinMappings());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(invalid));
    }
}
