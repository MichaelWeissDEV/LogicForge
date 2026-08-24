package dev.logicforge.circuit.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParameterValuesTest {

    private static final ParameterSpec.IntegerParameter INPUTS =
            new ParameterSpec.IntegerParameter("inputs", "Input Count", 2, 2, 8);
    private static final ParameterSpec.BooleanParameter ACTIVE_HIGH =
            new ParameterSpec.BooleanParameter("activeHigh", "Active High", true);
    private static final ParameterSpec.EnumParameter STYLE =
            new ParameterSpec.EnumParameter("style", "Style", "round", List.of("round", "square"));

    @Test
    void defaultsComeFromTheSpecs() {
        ParameterValues values = ParameterValues.defaultsOf(List.of(INPUTS, ACTIVE_HIGH, STYLE));
        assertEquals(2, values.getInt(INPUTS));
        assertTrue(values.getBoolean(ACTIVE_HIGH));
        assertEquals("round", values.get(STYLE));
    }

    @Test
    void missingValuesFallBackToDefaults() {
        assertEquals(2, ParameterValues.empty().getInt(INPUTS));
    }

    @Test
    void valuesOutOfRangeAreClamped() {
        assertEquals(8, ParameterValues.empty().with(INPUTS, 99).getInt(INPUTS));
        assertEquals(2, ParameterValues.empty().with(INPUTS, -3).getInt(INPUTS));
    }

    @Test
    void rawValuesFromAProjectFileAreCoerced() {
        ParameterValues fromFile = ParameterValues.of(Map.of(
                "inputs", 4.0,
                "activeHigh", "false",
                "style", "hexagon"));
        assertEquals(4, fromFile.getInt(INPUTS));
        assertEquals(false, fromFile.getBoolean(ACTIVE_HIGH));
        assertEquals("round", fromFile.get(STYLE), "unknown enum options fall back to the default");
    }

    @Test
    void valuesAreImmutableAndComparable() {
        ParameterValues original = ParameterValues.empty().with(INPUTS, 3);
        ParameterValues changed = original.with(INPUTS, 4);
        assertEquals(3, original.getInt(INPUTS));
        assertEquals(4, changed.getInt(INPUTS));
        assertNotEquals(original, changed);
        assertEquals(original, ParameterValues.empty().with(INPUTS, 3));
    }
}
