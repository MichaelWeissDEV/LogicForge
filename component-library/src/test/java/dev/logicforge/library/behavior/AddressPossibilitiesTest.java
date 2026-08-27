package dev.logicforge.library.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.logic.LogicVector;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class AddressPossibilitiesTest {
    @Test
    void expandsOnlyCompatibleConcreteAddresses() {
        AddressPossibilities possibilities = AddressPossibilities.resolve(
                LogicVector.of("10X1"), 16, 16);
        ArrayList<Integer> addresses = new ArrayList<>();
        possibilities.forEach(addresses::add);
        assertEquals(java.util.List.of(9, 11), addresses);
        assertTrue(possibilities.contains(9));
        assertFalse(possibilities.contains(10));
    }

    @Test
    void fallsBackToWholeMemoryPastExplosionThreshold() {
        AddressPossibilities possibilities = AddressPossibilities.resolve(
                LogicVector.of("XXXXXXXX"), 256, 8);
        assertTrue(possibilities.coversAllAddresses());
        assertEquals(256, possibilities.possibleCount());
    }
}
