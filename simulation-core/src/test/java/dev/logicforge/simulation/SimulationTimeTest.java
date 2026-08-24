package dev.logicforge.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SimulationTimeTest {

    @Test
    void zeroTime() {
        SimulationTime zero = SimulationTime.ZERO;
        assertEquals(0, zero.toPicoseconds());
        assertEquals(0, zero.toNanoseconds());
        assertEquals("0 ps", zero.toString());
    }

    @Test
    void ofPicoseconds() {
        SimulationTime time = SimulationTime.ofPicoseconds(1234);
        assertEquals(1234, time.toPicoseconds());
        assertEquals("1234 ps", time.toString());
    }

    @Test
    void ofNanoseconds() {
        SimulationTime time = SimulationTime.ofNanoseconds(5);
        assertEquals(5_000, time.toPicoseconds());
        assertEquals(5, time.toNanoseconds());
        assertEquals("5 ns", time.toString());
    }

    @Test
    void ofMicroseconds() {
        SimulationTime time = SimulationTime.ofMicroseconds(3);
        assertEquals(3_000_000, time.toPicoseconds());
        assertEquals(3, time.toMicroseconds());
        assertEquals("3 us", time.toString());
    }

    @Test
    void ofMilliseconds() {
        SimulationTime time = SimulationTime.ofMilliseconds(2);
        assertEquals(2_000_000_000L, time.toPicoseconds());
        assertEquals(2, time.toMilliseconds());
        assertEquals("2 ms", time.toString());
    }

    @Test
    void ofSeconds() {
        SimulationTime time = SimulationTime.ofSeconds(1);
        assertEquals(1_000_000_000_000L, time.toPicoseconds());
        assertEquals(1, time.toSeconds());
        assertEquals("1 s", time.toString());
    }

    @Test
    void plus() {
        SimulationTime t1 = SimulationTime.ofNanoseconds(10);
        SimulationTime t2 = SimulationTime.ofNanoseconds(20);
        SimulationTime sum = t1.plus(t2);
        assertEquals(30_000, sum.toPicoseconds());
        assertEquals("30 ns", sum.toString());
    }

    @Test
    void minus() {
        SimulationTime t1 = SimulationTime.ofNanoseconds(30);
        SimulationTime t2 = SimulationTime.ofNanoseconds(10);
        SimulationTime diff = t1.minus(t2);
        assertEquals(20_000, diff.toPicoseconds());
    }

    @Test
    void isLessThan() {
        SimulationTime t1 = SimulationTime.ofNanoseconds(10);
        SimulationTime t2 = SimulationTime.ofNanoseconds(20);
        assertTrue(t1.isLessThan(t2));
        assertFalse(t2.isLessThan(t1));
        assertFalse(t1.isLessThan(t1));
    }

    @Test
    void isGreaterThan() {
        SimulationTime t1 = SimulationTime.ofNanoseconds(10);
        SimulationTime t2 = SimulationTime.ofNanoseconds(20);
        assertTrue(t2.isGreaterThan(t1));
        assertFalse(t1.isGreaterThan(t2));
        assertFalse(t1.isGreaterThan(t1));
    }

    @Test
    void isEqualTo() {
        SimulationTime t1 = SimulationTime.ofNanoseconds(10);
        SimulationTime t2 = SimulationTime.ofNanoseconds(10);
        SimulationTime t3 = SimulationTime.ofNanoseconds(20);
        assertTrue(t1.isEqualTo(t2));
        assertTrue(t1.isEqualTo(t1));
        assertFalse(t1.isEqualTo(t3));
    }

    @Test
    void compareTo() {
        SimulationTime t1 = SimulationTime.ofNanoseconds(10);
        SimulationTime t2 = SimulationTime.ofNanoseconds(20);
        assertTrue(t1.compareTo(t2) < 0);
        assertTrue(t2.compareTo(t1) > 0);
        assertEquals(0, t1.compareTo(t1));
    }

    @Test
    void equalsAndHashCode() {
        SimulationTime t1 = SimulationTime.ofNanoseconds(10);
        SimulationTime t2 = SimulationTime.ofNanoseconds(10);
        SimulationTime t3 = SimulationTime.ofNanoseconds(20);
        
        assertEquals(t1, t2);
        assertNotEquals(t1, t3);
        assertNotEquals(t1, null);
        assertNotEquals(t1, "not a SimulationTime");
        
        assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    void largeValues() {
        // Test that we can represent large time values
        SimulationTime time = SimulationTime.ofSeconds(3600); // 1 hour
        assertEquals(3600 * 1_000_000_000_000L, time.toPicoseconds());
        assertEquals("3600 s", time.toString());
    }
}
