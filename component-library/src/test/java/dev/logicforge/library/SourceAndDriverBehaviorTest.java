package dev.logicforge.library;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.logicforge.library.behavior.ConstantBehavior;
import dev.logicforge.library.behavior.TriStateBehavior;
import dev.logicforge.library.behavior.UserInputBehavior;
import dev.logicforge.library.behavior.UserInputState;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class SourceAndDriverBehaviorTest {

    @ParameterizedTest
    @EnumSource(LogicState.class)
    void constantsDriveExactlyTheirValue(LogicState value) {
        assertEquals(value, BehaviorHarness.evaluate(new ConstantBehavior(value)));
    }

    @Test
    void aToggleSwitchStartsAtItsConfiguredValueAndFollowsTheUser() {
        BehaviorHarness harness = BehaviorHarness.of(new UserInputBehavior(LogicState.ONE, false));
        UserInputBehavior behavior = new UserInputBehavior(LogicState.ONE, false);

        assertEquals(LogicState.ONE, harness.run(behavior), "configured to start switched on");

        ((UserInputState) harness.state()).setValue(LogicVector.ZERO);
        assertEquals(LogicState.ZERO, harness.run(behavior));

        harness.state().reset();
        assertEquals(LogicState.ONE, harness.run(behavior), "reset returns to the configured value");
    }

    @Test
    void anInvertedButtonReadsOneWhileReleased() {
        UserInputBehavior behavior = new UserInputBehavior(LogicState.ZERO, true);
        BehaviorHarness harness = BehaviorHarness.of(behavior);

        assertEquals(LogicState.ONE, harness.run(behavior), "released");

        ((UserInputState) harness.state()).setValue(LogicVector.ONE);
        assertEquals(LogicState.ZERO, harness.run(behavior), "pressed");
    }

    @ParameterizedTest(name = "A={0} ENABLE={1} -> {2}")
    @CsvSource({
            "ZERO,ONE,ZERO",
            "ONE,ONE,ONE",
            "UNKNOWN,ONE,UNKNOWN",
            "HIGH_IMPEDANCE,ONE,UNKNOWN",
            "ONE,ZERO,HIGH_IMPEDANCE",
            "ZERO,ZERO,HIGH_IMPEDANCE",
            "ONE,UNKNOWN,UNKNOWN",
            "ONE,HIGH_IMPEDANCE,UNKNOWN"})
    void triStateBufferOnlyFloatsWhenItIsCertainlyDisabled(LogicState data, LogicState enable,
                                                           LogicState expected) {
        assertEquals(expected, BehaviorHarness.evaluate(new TriStateBehavior(false, false), data, enable));
    }

    @Test
    void invertingAndActiveLowVariants() {
        assertEquals(LogicState.ZERO, BehaviorHarness.evaluate(new TriStateBehavior(true, false),
                LogicState.ONE, LogicState.ONE));
        assertEquals(LogicState.HIGH_IMPEDANCE, BehaviorHarness.evaluate(new TriStateBehavior(true, false),
                LogicState.ONE, LogicState.ZERO));

        assertEquals(LogicState.ONE, BehaviorHarness.evaluate(new TriStateBehavior(false, true),
                LogicState.ONE, LogicState.ZERO));
        assertEquals(LogicState.HIGH_IMPEDANCE, BehaviorHarness.evaluate(new TriStateBehavior(false, true),
                LogicState.ONE, LogicState.ONE));
    }
}
