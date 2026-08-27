package dev.logicforge.library;

import static dev.logicforge.logic.LogicState.ONE;
import static dev.logicforge.logic.LogicState.UNKNOWN;
import static dev.logicforge.logic.LogicState.ZERO;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.logicforge.library.behavior.TimerBehavior;
import dev.logicforge.logic.LogicVector;
import org.junit.jupiter.api.Test;

class TimerBehaviorTest {

    @Test
    void periodicTimerRaisesIrqAndWriteOneToStatusAcknowledgesIt() {
        TimerBehavior behavior = new TimerBehavior();
        BehaviorHarness timer = harness(behavior);

        write(timer, behavior, 0, 3);
        write(timer, behavior, 1, 0);
        write(timer, behavior, 2, TimerBehavior.CONTROL_ENABLE
                | TimerBehavior.CONTROL_PERIODIC | TimerBehavior.CONTROL_IRQ_ENABLE);
        risingEdge(timer, behavior);
        risingEdge(timer, behavior);
        risingEdge(timer, behavior);

        assertEquals(ONE, timer.output(1));
        timer.setInput(0, LogicVector.fromUnsignedLong(3, 2));
        timer.setInput(1, ONE);
        timer.setInput(2, ONE);
        timer.setInput(3, ZERO);
        behavior.evaluate(timer);
        assertEquals(LogicVector.fromUnsignedLong(1, 8), timer.outputVector(0));

        write(timer, behavior, 3, TimerBehavior.STATUS_IRQ_PENDING);
        assertEquals(ZERO, timer.output(1));
    }

    @Test
    void oneShotDisablesItselfAfterExpiry() {
        TimerBehavior behavior = new TimerBehavior();
        BehaviorHarness timer = harness(behavior);
        write(timer, behavior, 0, 1);
        write(timer, behavior, 2,
                TimerBehavior.CONTROL_ENABLE | TimerBehavior.CONTROL_IRQ_ENABLE);
        risingEdge(timer, behavior);

        assertEquals(ONE, timer.output(1));
        assertEquals(ZERO, behavior.debugSnapshot(timer.state()).namedValues()
                .get("CONTROL").getBit(0));
    }

    @Test
    void unknownResetPersistsAsMergedTimerState() {
        TimerBehavior behavior = new TimerBehavior();
        BehaviorHarness timer = harness(behavior);
        write(timer, behavior, 0, 0xff);
        timer.setInput(5, UNKNOWN);
        behavior.evaluate(timer);
        timer.setInput(5, ZERO);
        behavior.evaluate(timer);
        assertEquals(LogicVector.repeat(UNKNOWN, 8), behavior.debugSnapshot(timer.state())
                .namedValues().get("RELOAD").slice(0, 8));
    }

    private static BehaviorHarness harness(TimerBehavior behavior) {
        BehaviorHarness timer = BehaviorHarness.ofVectors(behavior, 2,
                LogicVector.fromUnsignedLong(0, 2),
                LogicVector.ZERO, LogicVector.ZERO, LogicVector.ZERO,
                LogicVector.ZERO, LogicVector.ZERO,
                LogicVector.fromUnsignedLong(0, 8));
        behavior.evaluate(timer);
        return timer;
    }

    private static void write(BehaviorHarness timer, TimerBehavior behavior,
                              int register, int value) {
        timer.setInput(0, LogicVector.fromUnsignedLong(register, 2));
        timer.setInput(1, ONE);
        timer.setInput(2, ZERO);
        timer.setInput(3, ONE);
        timer.setInput(6, LogicVector.fromUnsignedLong(value, 8));
        risingEdge(timer, behavior);
        timer.setInput(3, ZERO);
        behavior.evaluate(timer);
    }

    private static void risingEdge(BehaviorHarness timer, TimerBehavior behavior) {
        timer.setInput(4, ZERO);
        behavior.evaluate(timer);
        timer.setInput(4, ONE);
        behavior.evaluate(timer);
    }
}
