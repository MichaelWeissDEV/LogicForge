package dev.logicforge.library;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.logicforge.library.behavior.DFlipFlopBehavior;
import dev.logicforge.library.behavior.DLatchBehavior;
import dev.logicforge.library.behavior.JkFlipFlopBehavior;
import dev.logicforge.library.behavior.SrLatchBehavior;
import dev.logicforge.library.behavior.TFlipFlopBehavior;
import dev.logicforge.logic.LogicState;
import org.junit.jupiter.api.Test;

class SequentialBehaviorTest {

    @Test
    void srLatchSetsHoldsResetsAndFlagsTheForbiddenState() {
        SrLatchBehavior behavior = new SrLatchBehavior(false);
        BehaviorHarness harness = BehaviorHarness.of(behavior, 2, LogicState.ZERO, LogicState.ZERO);

        harness.setInput(0, LogicState.ONE);
        harness.run(behavior);
        assertEquals(LogicState.ONE, harness.output(0), "S=1 sets Q");
        assertEquals(LogicState.ZERO, harness.output(1), "Q' is the complement");

        harness.setInput(0, LogicState.ZERO);
        harness.run(behavior);
        assertEquals(LogicState.ONE, harness.output(0), "S=R=0 holds");

        harness.setInput(1, LogicState.ONE);
        harness.run(behavior);
        assertEquals(LogicState.ZERO, harness.output(0), "R=1 resets Q");

        harness.setInput(0, LogicState.ONE);
        harness.run(behavior);
        assertEquals(LogicState.UNKNOWN, harness.output(0), "S=R=1 is the undefined state");
    }

    @Test
    void dLatchIsTransparentWhileEnabledAndHoldsOtherwise() {
        DLatchBehavior behavior = DLatchBehavior.INSTANCE;
        BehaviorHarness harness = BehaviorHarness.of(behavior, 2, LogicState.ONE, LogicState.ONE);

        harness.run(behavior);
        assertEquals(LogicState.ONE, harness.output(0), "transparent: Q follows D");

        harness.setInput(1, LogicState.ZERO);
        harness.run(behavior);
        assertEquals(LogicState.ONE, harness.output(0), "EN=0 latches the last value");

        harness.setInput(0, LogicState.ZERO);
        harness.run(behavior);
        assertEquals(LogicState.ONE, harness.output(0), "D changing while disabled has no effect");
    }

    @Test
    void dFlipFlopCapturesDOnlyOnTheRisingEdge() {
        DFlipFlopBehavior behavior = new DFlipFlopBehavior(true, false);
        BehaviorHarness harness = BehaviorHarness.of(behavior, 2, LogicState.ONE, LogicState.ZERO);

        harness.run(behavior);
        assertEquals(LogicState.ZERO, harness.output(0), "no edge yet");

        harness.setInput(1, LogicState.ONE);
        harness.run(behavior);
        assertEquals(LogicState.ONE, harness.output(0), "D=1, rising edge, Q becomes 1");

        harness.setInput(0, LogicState.ZERO);
        harness.run(behavior);
        assertEquals(LogicState.ONE, harness.output(0), "CLK still high: D changing does not restrike");

        harness.setInput(1, LogicState.ZERO);
        harness.run(behavior);
        assertEquals(LogicState.ONE, harness.output(0), "falling edge is ignored by a rising-edge flop");
    }

    @Test
    void dFlipFlopAsynchronousSetAndResetOverrideTheClock() {
        DFlipFlopBehavior behavior = new DFlipFlopBehavior(true, true);
        // D, CLK, SET, RESET
        BehaviorHarness harness = BehaviorHarness.of(behavior, 2,
                LogicState.ZERO, LogicState.ZERO, LogicState.ZERO, LogicState.ZERO);

        harness.setInput(2, LogicState.ONE);
        harness.run(behavior);
        assertEquals(LogicState.ONE, harness.output(0), "SET forces Q=1 without any clock edge");

        harness.setInput(2, LogicState.ZERO);
        harness.setInput(3, LogicState.ONE);
        harness.run(behavior);
        assertEquals(LogicState.ZERO, harness.output(0), "RESET forces Q=0 without any clock edge");
    }

    @Test
    void jkFlipFlopTogglesOnlyWhenBothInputsAreOne() {
        JkFlipFlopBehavior behavior = new JkFlipFlopBehavior(true);
        // J, K, CLK
        BehaviorHarness harness = BehaviorHarness.of(behavior, 2,
                LogicState.ONE, LogicState.ZERO, LogicState.ZERO);
        harness.run(behavior); // observe CLK=0 first, so the next 0->1 is a real edge

        harness.setInput(2, LogicState.ONE);
        harness.run(behavior);
        assertEquals(LogicState.ONE, harness.output(0), "J=1,K=0: set");

        harness.setInput(2, LogicState.ZERO);
        harness.run(behavior);
        harness.setInput(0, LogicState.ONE);
        harness.setInput(1, LogicState.ONE);
        harness.setInput(2, LogicState.ONE);
        harness.run(behavior);
        assertEquals(LogicState.ZERO, harness.output(0), "J=1,K=1: toggles from 1 to 0");
    }

    @Test
    void tFlipFlopTogglesOnEveryRisingEdgeWhileTIsOne() {
        TFlipFlopBehavior behavior = new TFlipFlopBehavior(true);
        // T, CLK
        BehaviorHarness harness = BehaviorHarness.of(behavior, 2, LogicState.ONE, LogicState.ZERO);
        harness.run(behavior); // observe CLK=0 first, so the next 0->1 is a real edge

        for (int edge = 0; edge < 3; edge++) {
            harness.setInput(1, LogicState.ONE);
            harness.run(behavior);
            harness.setInput(1, LogicState.ZERO);
            harness.run(behavior);
        }
        // 3 rising edges from Q=0: 0 -> 1 -> 0 -> 1
        assertEquals(LogicState.ONE, harness.output(0));
    }
}
