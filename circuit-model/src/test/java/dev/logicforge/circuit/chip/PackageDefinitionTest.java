package dev.logicforge.circuit.chip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PackageDefinitionTest {
    @Test
    void validatesAndOrdersEveryPhysicalPin() {
        ArrayList<PackagePin> pins = new ArrayList<>();
        for (int number = 8; number >= 1; number--) {
            pins.add(new PackagePin(number, "P" + number, ElectricalPinType.SIGNAL));
        }
        PackageDefinition definition = new PackageDefinition(PackageType.DIP8, pins);
        assertEquals(1, definition.pins().getFirst().number());
        assertEquals(8, definition.pins().getLast().number());
    }

    @Test
    void rejectsIncompletePackages() {
        assertThrows(IllegalArgumentException.class, () -> new PackageDefinition(
                PackageType.DIP8, List.of(new PackagePin(1, "P1", ElectricalPinType.SIGNAL))));
    }
}
