package dev.logicforge.library.chip;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.logicforge.circuit.chip.ElectricalPinType;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StandardChipLibraryTest {
    @Test
    void exposesInitialAccurateDip14GateFamily() {
        var registry = StandardChipLibrary.create();
        assertEquals(Set.of("74HC00", "74HC02", "74HC04", "74HC08", "74HC32", "74HC86"),
                registry.definitions().stream().map(chip -> chip.metadata().partNumber())
                        .collect(Collectors.toSet()));

        var nand = registry.require("74HC00");
        assertEquals("1A", nand.packageDefinition().pin(1).orElseThrow().name());
        assertEquals("1Y", nand.packageDefinition().pin(3).orElseThrow().name());
        assertEquals(ElectricalPinType.GROUND,
                nand.packageDefinition().pin(7).orElseThrow().electricalType());
        assertEquals("3Y", nand.packageDefinition().pin(8).orElseThrow().name());
        assertEquals(ElectricalPinType.POWER,
                nand.packageDefinition().pin(14).orElseThrow().electricalType());
        assertEquals("logic.nand", nand.logicalUnits().get("1"));
        assertEquals("OUT", nand.logicalMapping(3).orElseThrow().portName());
    }

    @Test
    void modelsTheDifferentNorAndInverterPinouts() {
        var registry = StandardChipLibrary.create();
        assertEquals("1Y", registry.require("74HC02").packageDefinition()
                .pin(1).orElseThrow().name());
        assertEquals("1Y", registry.require("74HC04").packageDefinition()
                .pin(2).orElseThrow().name());
        assertEquals(6, registry.require("74HC04").logicalUnits().size());
    }
}
