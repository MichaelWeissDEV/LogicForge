package dev.logicforge.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.library.chip.StandardChipLibrary;
import org.junit.jupiter.api.Test;

class ChipInstanceExpanderTest {

    @Test
    void onePhysical74hc00ExpandsToFourUnitsSharingOnePackageIdentity() {
        var definition = StandardChipLibrary.create().require("74HC00");
        var instance = ChipInstance.create("74HC00", new CircuitPoint(100, 200), "U1");
        var first = new ChipInstanceExpander().expand(instance, definition);
        var second = new ChipInstanceExpander().expand(instance, definition);

        assertEquals(instance.id(), first.packageInstanceId());
        assertEquals(4, first.logicalUnits().size());
        assertEquals(12, first.signalPins().size());
        assertEquals(first.logicalUnits().stream().map(component -> component.id()).toList(),
                second.logicalUnits().stream().map(component -> component.id()).toList());
        assertNotEquals(first.logicalUnits().get(0).id(), first.logicalUnits().get(1).id());
        assertEquals("U11", first.logicalUnits().getFirst().label());
    }
}
