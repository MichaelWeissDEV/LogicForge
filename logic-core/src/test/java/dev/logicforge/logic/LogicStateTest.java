package dev.logicforge.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LogicStateTest {

    @Test
    void symbolsRoundTrip() {
        for (LogicState state : LogicState.values()) {
            assertEquals(state, LogicState.fromSymbol(state.symbol()));
        }
        assertEquals(LogicState.UNKNOWN, LogicState.fromSymbol('x'));
        assertEquals(LogicState.HIGH_IMPEDANCE, LogicState.fromSymbol('z'));
        assertThrows(IllegalArgumentException.class, () -> LogicState.fromSymbol('?'));
    }

    @Test
    void definedAndDrivenClassification() {
        assertTrue(LogicState.ZERO.isDefined());
        assertTrue(LogicState.ONE.isDefined());
        assertFalse(LogicState.UNKNOWN.isDefined());
        assertFalse(LogicState.HIGH_IMPEDANCE.isDefined());

        assertTrue(LogicState.ZERO.isDriven());
        assertTrue(LogicState.UNKNOWN.isDriven(), "X means driven but unknown");
        assertFalse(LogicState.HIGH_IMPEDANCE.isDriven());
    }

    @Test
    void booleanConversion() {
        assertEquals(LogicState.ONE, LogicState.of(true));
        assertEquals(LogicState.ZERO, LogicState.of(false));
    }
}
