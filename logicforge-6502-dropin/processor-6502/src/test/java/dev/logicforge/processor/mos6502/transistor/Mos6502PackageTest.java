package dev.logicforge.processor.mos6502.transistor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

final class Mos6502PackageTest {
    @Test
    void dipHasExactlyFortyUniquePhysicalPinNumbers() {
        assertEquals(40, Mos6502Package.dip40().size());
        assertEquals(40, new HashSet<>(Mos6502Package.dip40().stream().map(Mos6502Package.Pin::number).toList()).size());
        assertEquals("VSS", Mos6502Package.dip40().get(0).name());
        assertEquals("RES", Mos6502Package.dip40().get(39).name());
    }
}
