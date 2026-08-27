package dev.logicforge.library.chip;

import dev.logicforge.circuit.chip.ChipDefinition;
import dev.logicforge.circuit.chip.ChipMetadata;
import dev.logicforge.circuit.chip.ChipLogicalUnit;
import dev.logicforge.circuit.chip.ChipRegistry;
import dev.logicforge.circuit.chip.ElectricalPinType;
import dev.logicforge.circuit.chip.LogicalPinMapping;
import dev.logicforge.circuit.chip.PackageDefinition;
import dev.logicforge.circuit.chip.PackagePin;
import dev.logicforge.circuit.chip.PackageType;
import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.component.PortDirection;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.LibraryParameters;
import java.util.ArrayList;
import java.util.List;

/** Standard physical-chip catalog backed by ordinary LogicForge component definitions. */
public final class StandardChipLibrary {
    private StandardChipLibrary() {
    }

    public static ChipRegistry create() {
        ChipRegistry registry = new ChipRegistry();
        ComponentRegistry components = ComponentRegistry.standard();
        ChipDefinitionValidator validator = new ChipDefinitionValidator(components);
        for (ChipDefinition definition : List.of(
                quadGate("74HC00", "Quad 2-input NAND gate", "logic.nand", false),
                quadGate("74HC02", "Quad 2-input NOR gate", "logic.nor", true),
                hexInverter(),
                quadGate("74HC08", "Quad 2-input AND gate", "logic.and", false),
                quadGate("74HC32", "Quad 2-input OR gate", "logic.or", false),
                quadGate("74HC86", "Quad 2-input XOR gate", "logic.xor", false),
                adder74hc283(),
                sram6116(), sram6264(), sram62256(), eprom27c64(), eprom27c256())) {
            validator.validate(definition);
            registry.register(definition);
        }
        return registry;
    }

    private static ChipDefinition quadGate(String partNumber, String summary,
                                           String componentDefinitionId, boolean norPinout) {
        String[] names = norPinout
                ? new String[] {"1Y", "1A", "1B", "2Y", "2A", "2B", "GND",
                        "3A", "3B", "3Y", "4A", "4B", "4Y", "VCC"}
                : new String[] {"1A", "1B", "1Y", "2A", "2B", "2Y", "GND",
                        "3Y", "3A", "3B", "4Y", "4A", "4B", "VCC"};
        PackageDefinition packageDefinition = dip14(names);
        ArrayList<ChipLogicalUnit> units = new ArrayList<>();
        ArrayList<LogicalPinMapping> mappings = new ArrayList<>();
        for (int unit = 1; unit <= 4; unit++) {
            String unitName = Integer.toString(unit);
            units.add(new ChipLogicalUnit(unitName, componentDefinitionId,
                    ParameterValues.empty().with(LibraryParameters.INPUT_COUNT, 2)));
            mapNamedPin(packageDefinition, mappings, unitName, unit + "A", "IN0",
                    PortDirection.INPUT);
            mapNamedPin(packageDefinition, mappings, unitName, unit + "B", "IN1",
                    PortDirection.INPUT);
            mapNamedPin(packageDefinition, mappings, unitName, unit + "Y", "OUT",
                    PortDirection.OUTPUT);
        }
        return chip(partNumber, summary, packageDefinition, units, mappings,
                List.of("74hc", "dip14", "quad gate"));
    }

    private static ChipDefinition hexInverter() {
        PackageDefinition packageDefinition = dip14(new String[] {
                "1A", "1Y", "2A", "2Y", "3A", "3Y", "GND",
                "4Y", "4A", "5Y", "5A", "6Y", "6A", "VCC"});
        ArrayList<ChipLogicalUnit> units = new ArrayList<>();
        ArrayList<LogicalPinMapping> mappings = new ArrayList<>();
        for (int unit = 1; unit <= 6; unit++) {
            String unitName = Integer.toString(unit);
            units.add(new ChipLogicalUnit(unitName, "logic.not", ParameterValues.empty()));
            mapNamedPin(packageDefinition, mappings, unitName, unit + "A", "A",
                    PortDirection.INPUT);
            mapNamedPin(packageDefinition, mappings, unitName, unit + "Y", "Y",
                    PortDirection.OUTPUT);
        }
        return chip("74HC04", "Hex inverter", packageDefinition, units, mappings,
                List.of("74hc", "dip14", "hex inverter"));
    }

    private static ChipDefinition adder74hc283() {
        // TI CD74HC283, N package, top view (SCHS176E, July 2022).
        PackageDefinition packageDefinition = dip(PackageType.DIP16, new String[] {
                "S2", "B2", "A2", "S1", "A1", "B1", "CIN", "GND",
                "COUT", "S4", "B4", "A4", "S3", "A3", "B3", "VCC"});
        List<ChipLogicalUnit> units = List.of(new ChipLogicalUnit("ADD",
                "arithmetic.adder", ParameterValues.empty().with(LibraryParameters.WIDTH, 4)));
        ArrayList<LogicalPinMapping> mappings = new ArrayList<>();
        for (int bit = 0; bit < 4; bit++) {
            mapNamedPin(packageDefinition, mappings, "ADD", "A" + (bit + 1), "A", bit,
                    PortDirection.INPUT);
            mapNamedPin(packageDefinition, mappings, "ADD", "B" + (bit + 1), "B", bit,
                    PortDirection.INPUT);
            mapNamedPin(packageDefinition, mappings, "ADD", "S" + (bit + 1), "SUM", bit,
                    PortDirection.OUTPUT);
        }
        mapNamedPin(packageDefinition, mappings, "ADD", "CIN", "CIN", -1,
                PortDirection.INPUT);
        mapNamedPin(packageDefinition, mappings, "ADD", "COUT", "COUT", -1,
                PortDirection.OUTPUT);
        return chip("74HC283", "4-bit binary full adder", packageDefinition, units, mappings,
                List.of("74hc", "dip16", "adder", "structural study"));
    }

    private static ChipDefinition sram6116() {
        return sram("6116", PackageType.DIP24, 11, false, new String[] {
                "A7", "A6", "A5", "A4", "A3", "A2", "A1", "A0",
                "D0", "D1", "D2", "GND", "D3", "D4", "D5", "D6",
                "D7", "CS_N", "OE_N", "WE_N", "A10", "A9", "A8", "VCC"});
    }

    private static ChipDefinition sram6264() {
        return sram("6264", PackageType.DIP28, 13, true, new String[] {
                "NC", "A12", "A7", "A6", "A5", "A4", "A3", "A2",
                "A1", "A0", "D0", "D1", "D2", "GND", "D3", "D4",
                "D5", "D6", "D7", "CS_N", "A10", "OE_N", "A11", "A9",
                "A8", "CS2", "WE_N", "VCC"});
    }

    private static ChipDefinition sram62256() {
        return sram("62256", PackageType.DIP28, 15, false, new String[] {
                "A14", "A12", "A7", "A6", "A5", "A4", "A3", "A2",
                "A1", "A0", "D0", "D1", "D2", "GND", "D3", "D4",
                "D5", "D6", "D7", "CS_N", "A10", "OE_N", "A11", "A9",
                "A8", "A13", "WE_N", "VCC"});
    }

    private static ChipDefinition eprom27c64() {
        return eprom("27C64", 13, new String[] {
                "VPP", "A12", "A7", "A6", "A5", "A4", "A3", "A2",
                "A1", "A0", "D0", "D1", "D2", "GND", "D3", "D4",
                "D5", "D6", "D7", "CE_N", "A10", "OE_N", "A11", "A9",
                "A8", "NC", "PGM_N", "VCC"});
    }

    private static ChipDefinition eprom27c256() {
        return eprom("27C256", 15, new String[] {
                "VPP", "A12", "A7", "A6", "A5", "A4", "A3", "A2",
                "A1", "A0", "D0", "D1", "D2", "GND", "D3", "D4",
                "D5", "D6", "D7", "CE_N", "A10", "OE_N", "A11", "A9",
                "A8", "A13", "A14", "VCC"});
    }

    private static ChipDefinition sram(String part, PackageType packageType, int addressWidth,
                                       boolean dualSelect, String[] pins) {
        PackageDefinition packageDefinition = dip(packageType, pins);
        List<ChipLogicalUnit> units = List.of(new ChipLogicalUnit("MEMORY",
                "memory.packaged_sram", ParameterValues.empty()
                        .with(LibraryParameters.ADDRESS_WIDTH, addressWidth)
                        .with(LibraryParameters.WIDTH, 8)
                        .with(LibraryParameters.DUAL_CHIP_SELECT, dualSelect)));
        ArrayList<LogicalPinMapping> mappings = memoryBusMappings(packageDefinition, addressWidth,
                PortDirection.INOUT);
        mapNamedPin(packageDefinition, mappings, "MEMORY", "CS_N", "CS_N",
                PortDirection.INPUT);
        mapNamedPin(packageDefinition, mappings, "MEMORY", "OE_N", "OE_N",
                PortDirection.INPUT);
        mapNamedPin(packageDefinition, mappings, "MEMORY", "WE_N", "WE_N",
                PortDirection.INPUT);
        if (dualSelect) {
            mapNamedPin(packageDefinition, mappings, "MEMORY", "CS2", "CS2",
                    PortDirection.INPUT);
        }
        return new ChipDefinition(new ChipMetadata(part, "Memory", addressWidth < 13
                ? "2 KiB x 8 SRAM" : addressWidth == 13 ? "8 KiB x 8 SRAM"
                : "32 KiB x 8 SRAM", List.of("sram", packageType.name().toLowerCase())),
                packageDefinition, units, mappings);
    }

    private static ChipDefinition eprom(String part, int addressWidth, String[] pins) {
        PackageDefinition packageDefinition = dip(PackageType.DIP28, pins);
        List<ChipLogicalUnit> units = List.of(new ChipLogicalUnit("MEMORY",
                "memory.packaged_rom", ParameterValues.empty()
                        .with(LibraryParameters.ADDRESS_WIDTH, addressWidth)
                        .with(LibraryParameters.WIDTH, 8)));
        ArrayList<LogicalPinMapping> mappings = memoryBusMappings(packageDefinition, addressWidth,
                PortDirection.OUTPUT);
        mapNamedPin(packageDefinition, mappings, "MEMORY", "CE_N", "CE_N",
                PortDirection.INPUT);
        mapNamedPin(packageDefinition, mappings, "MEMORY", "OE_N", "OE_N",
                PortDirection.INPUT);
        packageDefinition.pins().stream().filter(pin -> pin.name().equals("PGM_N")).findFirst()
                .ifPresent(pin -> mappings.add(new LogicalPinMapping(pin.number(), "MEMORY",
                        "PROGRAM_N", -1, PortDirection.INPUT)));
        return new ChipDefinition(new ChipMetadata(part, "Memory",
                (1 << (addressWidth - 10)) + " KiB x 8 EPROM (read mode)",
                List.of("eprom", "dip28", "read only")), packageDefinition, units, mappings);
    }

    private static ArrayList<LogicalPinMapping> memoryBusMappings(
            PackageDefinition packageDefinition, int addressWidth, PortDirection dataDirection) {
        ArrayList<LogicalPinMapping> mappings = new ArrayList<>();
        for (int bit = 0; bit < addressWidth; bit++) {
            mapNamedPin(packageDefinition, mappings, "MEMORY", "A" + bit, "ADDRESS", bit,
                    PortDirection.INPUT);
        }
        for (int bit = 0; bit < 8; bit++) {
            mapNamedPin(packageDefinition, mappings, "MEMORY", "D" + bit, "DATA", bit,
                    dataDirection);
        }
        return mappings;
    }

    private static ChipDefinition chip(String partNumber, String summary,
                                       PackageDefinition packageDefinition,
                                       List<ChipLogicalUnit> units,
                                       List<LogicalPinMapping> mappings, List<String> tags) {
        return new ChipDefinition(new ChipMetadata(partNumber, "74HC", summary, tags),
                packageDefinition, units, mappings);
    }

    private static PackageDefinition dip14(String[] names) {
        return dip(PackageType.DIP14, names);
    }

    private static PackageDefinition dip(PackageType type, String[] names) {
        if (names.length != type.pinCount()) {
            throw new IllegalArgumentException(type + " requires " + type.pinCount()
                    + " pin names");
        }
        ArrayList<PackagePin> pins = new ArrayList<>(names.length);
        for (int index = 0; index < names.length; index++) {
            ElectricalPinType pinType = switch (names[index]) {
                case "VCC", "VPP" -> ElectricalPinType.POWER;
                case "GND" -> ElectricalPinType.GROUND;
                case "NC" -> ElectricalPinType.NC;
                default -> ElectricalPinType.SIGNAL;
            };
            pins.add(new PackagePin(index + 1, names[index], pinType));
        }
        return new PackageDefinition(type, pins);
    }

    private static void mapNamedPin(PackageDefinition packageDefinition,
                                    List<LogicalPinMapping> mappings, String unit,
                                    String pinName, String portName, PortDirection direction) {
        mapNamedPin(packageDefinition, mappings, unit, pinName, portName, -1, direction);
    }

    private static void mapNamedPin(PackageDefinition packageDefinition,
                                    List<LogicalPinMapping> mappings, String unit,
                                    String pinName, String portName, int bitIndex,
                                    PortDirection direction) {
        PackagePin pin = packageDefinition.pins().stream()
                .filter(candidate -> candidate.name().equals(pinName))
                .findFirst().orElseThrow();
        mappings.add(new LogicalPinMapping(pin.number(), unit, portName, bitIndex, direction));
    }
}
