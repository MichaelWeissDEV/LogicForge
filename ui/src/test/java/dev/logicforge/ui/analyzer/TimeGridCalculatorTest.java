package dev.logicforge.ui.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TimeGridCalculatorTest {

    @Test
    void choosesOneTwoFiveMajorIntervalsForDifferentSpans() {
        assertEquals(1_000, TimeGridCalculator.calculate(
                new TimelineTransform(0, 10_000, 1_000)).majorStep());
        assertEquals(20_000, TimeGridCalculator.calculate(
                new TimelineTransform(0, 200_000, 1_000)).majorStep());
        assertEquals(500_000, TimeGridCalculator.calculate(
                new TimelineTransform(0, 5_000_000, 1_000)).majorStep());
    }

    @Test
    void formatsReadableEngineeringTimeUnits() {
        assertEquals("12 ns", TimeGridCalculator.formatTime(12_000));
        assertEquals("12 µs", TimeGridCalculator.formatTime(12_000_000));
        assertEquals("12 ms", TimeGridCalculator.formatTime(12_000_000_000L));
        assertEquals("12 s", TimeGridCalculator.formatTime(12_000_000_000_000L));
        assertTrue(TimeGridCalculator.formatFrequency(60_000).endsWith("MHz"));
    }
}
