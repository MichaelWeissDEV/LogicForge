package dev.logicforge.library.chip;

import dev.logicforge.circuit.chip.ChipDefinition;
import dev.logicforge.circuit.chip.ChipMetadata;
import dev.logicforge.circuit.chip.ChipRegistry;
import dev.logicforge.circuit.chip.ElectricalPinType;
import dev.logicforge.circuit.chip.LogicalPinMapping;
import dev.logicforge.circuit.chip.PackageDefinition;
import dev.logicforge.circuit.chip.PackagePin;
import dev.logicforge.circuit.chip.PackageType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Standard physical-chip catalog backed by ordinary LogicForge component definitions. */
public final class StandardChipLibrary {
    private StandardChipLibrary() {
    }

    public static ChipRegistry create() {
        ChipRegistry registry = new ChipRegistry();
        registry.register(quadGate("74HC00", "Quad 2-input NAND gate", "logic.nand", false));
        registry.register(quadGate("74HC02", "Quad 2-input NOR gate", "logic.nor", true));
        registry.register(hexInverter());
        registry.register(quadGate("74HC08", "Quad 2-input AND gate", "logic.and", false));
        registry.register(quadGate("74HC32", "Quad 2-input OR gate", "logic.or", false));
        registry.register(quadGate("74HC86", "Quad 2-input XOR gate", "logic.xor", false));
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
        LinkedHashMap<String, String> units = new LinkedHashMap<>();
        ArrayList<LogicalPinMapping> mappings = new ArrayList<>();
        for (int unit = 1; unit <= 4; unit++) {
            String unitName = Integer.toString(unit);
            units.put(unitName, componentDefinitionId);
            mapNamedPin(packageDefinition, mappings, unitName, unit + "A", "IN0");
            mapNamedPin(packageDefinition, mappings, unitName, unit + "B", "IN1");
            mapNamedPin(packageDefinition, mappings, unitName, unit + "Y", "OUT");
        }
        return chip(partNumber, summary, packageDefinition, units, mappings,
                List.of("74hc", "dip14", "quad gate"));
    }

    private static ChipDefinition hexInverter() {
        PackageDefinition packageDefinition = dip14(new String[] {
                "1A", "1Y", "2A", "2Y", "3A", "3Y", "GND",
                "4Y", "4A", "5Y", "5A", "6Y", "6A", "VCC"});
        LinkedHashMap<String, String> units = new LinkedHashMap<>();
        ArrayList<LogicalPinMapping> mappings = new ArrayList<>();
        for (int unit = 1; unit <= 6; unit++) {
            String unitName = Integer.toString(unit);
            units.put(unitName, "logic.not");
            mapNamedPin(packageDefinition, mappings, unitName, unit + "A", "A");
            mapNamedPin(packageDefinition, mappings, unitName, unit + "Y", "Y");
        }
        return chip("74HC04", "Hex inverter", packageDefinition, units, mappings,
                List.of("74hc", "dip14", "hex inverter"));
    }

    private static ChipDefinition chip(String partNumber, String summary,
                                       PackageDefinition packageDefinition,
                                       Map<String, String> units,
                                       List<LogicalPinMapping> mappings, List<String> tags) {
        return new ChipDefinition(new ChipMetadata(partNumber, "74HC", summary, tags),
                packageDefinition, units, mappings);
    }

    private static PackageDefinition dip14(String[] names) {
        if (names.length != 14) {
            throw new IllegalArgumentException("DIP14 requires fourteen pin names");
        }
        ArrayList<PackagePin> pins = new ArrayList<>(14);
        for (int index = 0; index < names.length; index++) {
            ElectricalPinType type = switch (names[index]) {
                case "VCC" -> ElectricalPinType.POWER;
                case "GND" -> ElectricalPinType.GROUND;
                case "NC" -> ElectricalPinType.NC;
                default -> ElectricalPinType.SIGNAL;
            };
            pins.add(new PackagePin(index + 1, names[index], type));
        }
        return new PackageDefinition(PackageType.DIP14, pins);
    }

    private static void mapNamedPin(PackageDefinition packageDefinition,
                                    List<LogicalPinMapping> mappings, String unit,
                                    String pinName, String portName) {
        PackagePin pin = packageDefinition.pins().stream()
                .filter(candidate -> candidate.name().equals(pinName))
                .findFirst().orElseThrow();
        mappings.add(new LogicalPinMapping(pin.number(), unit, portName));
    }
}
