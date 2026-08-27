package dev.logicforge.ui.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.chip.PackageType;
import org.junit.jupiter.api.Test;

class ChipPackageRendererTest {

    @Test
    void dipTopViewNumbersLeftAscendingAndRightDescending() {
        var pin1 = ChipPackageRenderer.pinPosition(PackageType.DIP14, 1);
        var pin7 = ChipPackageRenderer.pinPosition(PackageType.DIP14, 7);
        var pin14 = ChipPackageRenderer.pinPosition(PackageType.DIP14, 14);
        var pin8 = ChipPackageRenderer.pinPosition(PackageType.DIP14, 8);

        assertTrue(pin1.x() < 0);
        assertTrue(pin14.x() > 0);
        assertEquals(pin1.y(), pin14.y());
        assertEquals(pin7.y(), pin8.y());
        assertTrue(pin1.y() < pin7.y());
    }
}
