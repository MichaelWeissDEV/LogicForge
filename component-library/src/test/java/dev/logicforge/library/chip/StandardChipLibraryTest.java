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
        assertEquals(Set.of("74HC00", "74HC02", "74HC04", "74HC08", "74HC32", "74HC86",
                        "74HC283", "6116", "6264", "62256", "27C64", "27C256"),
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
        assertEquals("logic.nand", nand.logicalUnit("1").orElseThrow().componentDefinitionId());
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

    @Test
    void modelsTi74hc283Dip16AndParameterizedAdderUnit() {
        var adder = StandardChipLibrary.create().require("74HC283");
        assertEquals("S2", adder.packageDefinition().pin(1).orElseThrow().name());
        assertEquals("CIN", adder.packageDefinition().pin(7).orElseThrow().name());
        assertEquals(ElectricalPinType.GROUND,
                adder.packageDefinition().pin(8).orElseThrow().electricalType());
        assertEquals("COUT", adder.packageDefinition().pin(9).orElseThrow().name());
        assertEquals(ElectricalPinType.POWER,
                adder.packageDefinition().pin(16).orElseThrow().electricalType());
        var unit = adder.logicalUnit("ADD").orElseThrow();
        assertEquals("arithmetic.adder", unit.componentDefinitionId());
        assertEquals(4, unit.parameters().asMap().get("width"));
        assertEquals(1, adder.logicalMapping(1).orElseThrow().bitIndex());
    }

    @Test
    void exposesParameterizedSramAndEpromPresetsWithPhysicalPinouts() {
        var registry = StandardChipLibrary.create();
        var sram = registry.require("62256");
        assertEquals("A14", sram.packageDefinition().pin(1).orElseThrow().name());
        assertEquals("CS_N", sram.packageDefinition().pin(20).orElseThrow().name());
        assertEquals("WE_N", sram.packageDefinition().pin(27).orElseThrow().name());
        assertEquals(15, sram.logicalUnit("MEMORY").orElseThrow().parameters()
                .asMap().get("addressWidth"));

        var eprom = registry.require("27C256");
        assertEquals(ElectricalPinType.POWER,
                eprom.packageDefinition().pin(1).orElseThrow().electricalType());
        assertEquals("CE_N", eprom.packageDefinition().pin(20).orElseThrow().name());
        assertEquals("A14", eprom.packageDefinition().pin(27).orElseThrow().name());
        assertEquals("memory.packaged_rom",
                eprom.logicalUnit("MEMORY").orElseThrow().componentDefinitionId());
    }
}
