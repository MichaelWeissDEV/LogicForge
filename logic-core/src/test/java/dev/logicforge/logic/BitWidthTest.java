package dev.logicforge.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BitWidthTest {

    @Test
    void commonWidthsAreCached() {
        assertSame(BitWidth.of(8), BitWidth.of(8));
        assertSame(BitWidth.ONE, BitWidth.of(1));
    }

    @Test
    void invalidWidthsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> BitWidth.of(0));
        assertThrows(IllegalArgumentException.class, () -> BitWidth.of(-1));
        assertThrows(IllegalArgumentException.class, () -> BitWidth.of(BitWidth.MAX_BITS + 1));
    }

    @Test
    void widthsCompareAndDescribeThemselves() {
        assertTrue(BitWidth.of(1).compareTo(BitWidth.of(8)) < 0);
        assertTrue(BitWidth.ONE.isSingleBit());
        assertEquals("1 bit", BitWidth.ONE.toString());
        assertEquals("8 bits", BitWidth.of(8).toString());
    }
}
