package dev.logicforge.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class LogicVectorTest {

    @Test
    void parsesTextMostSignificantBitFirst() {
        LogicVector vector = LogicVector.of("0101XXZ1");
        assertEquals(8, vector.width());
        assertEquals(BitWidth.of(8), vector.bitWidth());
        assertEquals(LogicState.ONE, vector.getBit(0));
        assertEquals(LogicState.HIGH_IMPEDANCE, vector.getBit(1));
        assertEquals(LogicState.UNKNOWN, vector.getBit(2));
        assertEquals(LogicState.UNKNOWN, vector.getBit(3));
        assertEquals(LogicState.ONE, vector.getBit(4));
        assertEquals(LogicState.ZERO, vector.getBit(7));
        assertEquals("0101XXZ1", vector.toBinaryString());
    }

    @Test
    void lsbAndMsbFactoriesAgree() {
        LogicVector msb = LogicVector.ofMsbFirst(LogicState.ONE, LogicState.ZERO);
        LogicVector lsb = LogicVector.ofLsbFirst(LogicState.ZERO, LogicState.ONE);
        assertEquals(msb, lsb);
        assertEquals("10", msb.toBinaryString());
    }

    @Test
    void slicingTakesBitsFromTheLeastSignificantEnd() {
        LogicVector vector = LogicVector.of("11010010");
        assertEquals(LogicVector.of("0010"), vector.slice(0, 4));
        assertEquals(LogicVector.of("1101"), vector.slice(4, 4));
        assertEquals(LogicVector.of("10"), vector.slice(3, 2));
    }

    @Test
    void slicingOutOfRangeFails() {
        LogicVector vector = LogicVector.of("1010");
        assertThrows(IndexOutOfBoundsException.class, () -> vector.slice(2, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> vector.slice(-1, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> vector.getBit(4));
    }

    @Test
    void concatenationKeepsTheReceiverAsTheHighPart() {
        LogicVector high = LogicVector.of("1100");
        LogicVector low = LogicVector.of("0011");
        assertEquals("11000011", high.concat(low).toBinaryString());
        assertEquals(8, high.concat(low).width());
    }

    @Test
    void widthMismatchIsExplicit() {
        LogicVector vector = LogicVector.of("10101010");
        WidthMismatchException failure =
                assertThrows(WidthMismatchException.class, () -> vector.requireWidth(4));
        assertEquals(4, failure.expectedWidth());
        assertEquals(8, failure.actualWidth());
        assertThrows(WidthMismatchException.class, vector::singleBit);
    }

    @Test
    void equalityIncludesWidth() {
        assertEquals(LogicVector.of("01"), LogicVector.of("01"));
        assertEquals(LogicVector.of("01").hashCode(), LogicVector.of("01").hashCode());
        assertNotEquals(LogicVector.of("01"), LogicVector.of("001"));
        assertNotEquals(LogicVector.of("0"), LogicVector.of("Z"));
    }

    @Test
    void unsignedConversionOnlyForFullyDefinedVectors() {
        assertEquals(OptionalLong.of(0b1011), LogicVector.of("1011").toUnsignedLong());
        assertEquals(OptionalLong.empty(), LogicVector.of("10X1").toUnsignedLong());
        assertEquals(OptionalLong.empty(), LogicVector.of("10Z1").toUnsignedLong());
        assertEquals(LogicVector.of("00001111"), LogicVector.fromUnsignedLong(0x0F, 8));
        assertEquals(OptionalLong.of(255), LogicVector.fromUnsignedLong(255, 8).toUnsignedLong());
    }

    @Test
    void stateInspection() {
        assertTrue(LogicVector.of("1010").isFullyDefined());
        assertFalse(LogicVector.of("10X0").isFullyDefined());
        assertTrue(LogicVector.repeat(LogicState.HIGH_IMPEDANCE, 8).isHighImpedance());
        assertFalse(LogicVector.of("ZZZ0").isHighImpedance());
    }

    @Test
    void vectorsAreImmutable() {
        LogicVector original = LogicVector.of("0000");
        LogicVector changed = original.withBit(2, LogicState.ONE);
        assertEquals("0000", original.toBinaryString());
        assertEquals("0100", changed.toBinaryString());
        assertEquals(original, original.withBit(2, LogicState.ZERO));
    }

    @Test
    void singleBitVectorsAreShared() {
        assertEquals(LogicVector.ONE, LogicVector.single(LogicState.ONE));
        assertEquals(LogicState.ONE, LogicVector.ONE.singleBit());
        assertEquals("Z", LogicVector.HIGH_IMPEDANCE.toBinaryString());
    }

    @Test
    void emptyVectorsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> LogicVector.of(""));
        assertThrows(IllegalArgumentException.class, () -> LogicVector.repeat(LogicState.ZERO, 0));
    }
}
