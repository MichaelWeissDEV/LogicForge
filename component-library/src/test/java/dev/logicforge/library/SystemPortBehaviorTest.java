package dev.logicforge.library;

import static dev.logicforge.logic.LogicState.HIGH_IMPEDANCE;
import static dev.logicforge.logic.LogicState.ONE;
import static dev.logicforge.logic.LogicState.UNKNOWN;
import static dev.logicforge.logic.LogicState.ZERO;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.logicforge.library.behavior.InputPortBehavior;
import dev.logicforge.library.behavior.OutputPortBehavior;
import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.InputSourceState;
import org.junit.jupiter.api.Test;

class SystemPortBehaviorTest {

    @Test
    void outputPortCapturesOnlySelectedWritesAndResets() {
        OutputPortBehavior behavior = new OutputPortBehavior(BitWidth.of(8));
        BehaviorHarness harness = BehaviorHarness.ofVectors(behavior, 1,
                byteValue(0x42), bit(ONE), bit(ONE), bit(ZERO), bit(ZERO));

        behavior.evaluate(harness);
        harness.setInput(3, ONE);
        behavior.evaluate(harness);
        assertEquals(byteValue(0x42), harness.outputVector(0));

        harness.setInput(3, ZERO);
        harness.setInput(0, byteValue(0x99));
        harness.setInput(1, ZERO);
        behavior.evaluate(harness);
        harness.setInput(3, ONE);
        behavior.evaluate(harness);
        assertEquals(byteValue(0x42), harness.outputVector(0), "inactive write must hold");

        harness.setInput(4, ONE);
        behavior.evaluate(harness);
        assertEquals(byteValue(0), harness.outputVector(0));
    }

    @Test
    void outputPortMergesStateWhenWriteControlsAreUnknown() {
        OutputPortBehavior behavior = new OutputPortBehavior(BitWidth.of(4));
        BehaviorHarness harness = BehaviorHarness.ofVectors(behavior, 1,
                LogicVector.fromUnsignedLong(0b0101, 4), bit(UNKNOWN), bit(ONE),
                bit(ZERO), bit(ZERO));
        behavior.evaluate(harness);
        harness.setInput(3, ONE);
        behavior.evaluate(harness);
        assertEquals(LogicVector.of("0X0X"), harness.outputVector(0));
    }

    @Test
    void inputPortDrivesOnlyASelectedReadAndHandlesUnknownControlsConservatively() {
        InputPortBehavior behavior = new InputPortBehavior(BitWidth.of(4));
        BehaviorHarness harness = BehaviorHarness.ofVectors(behavior, 1,
                bit(ONE), bit(ONE));
        ((InputSourceState) harness.state()).setValue(LogicVector.fromUnsignedLong(0b1010, 4));
        behavior.evaluate(harness);
        assertEquals(LogicVector.fromUnsignedLong(0b1010, 4), harness.outputVector(0));

        harness.setInput(0, ZERO);
        behavior.evaluate(harness);
        assertEquals(LogicVector.repeat(HIGH_IMPEDANCE, 4), harness.outputVector(0));

        harness.setInput(0, UNKNOWN);
        behavior.evaluate(harness);
        assertEquals(LogicVector.repeat(UNKNOWN, 4), harness.outputVector(0));
    }

    private static LogicVector bit(dev.logicforge.logic.LogicState value) {
        return LogicVector.single(value);
    }

    private static LogicVector byteValue(int value) {
        return LogicVector.fromUnsignedLong(value, 8);
    }
}
