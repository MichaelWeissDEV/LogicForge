package dev.logicforge.library.behavior;

import static dev.logicforge.logic.LogicState.ONE;
import static dev.logicforge.logic.LogicState.UNKNOWN;
import static dev.logicforge.logic.LogicState.ZERO;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;
import java.util.OptionalLong;

/**
 * A binary counter: on the configured clock edge, while ENABLE is 1, COUNT increments or
 * decrements by one, wrapping at the width's range. RESET is asynchronous and clears COUNT
 * to zero.
 *
 * <p>Ports are {@code CLK, ENABLE, RESET[, UP_DOWN]} in, {@code COUNT, TC} out.
 * {@code UP_DOWN} exists only for {@link Direction#SELECTABLE}: 1 counts up, 0 counts down.
 * {@code TC} (terminal count) is 1 when COUNT is about to wrap on the next active edge —
 * at the maximum value while counting up, or at zero while counting down.
 *
 * @param width the bus width of COUNT
 * @param risingEdge {@code true} to trigger on 0-&gt;1, {@code false} to trigger on 1-&gt;0
 * @param direction whether this instance only counts up, only down, or is selectable
 */
public record CounterBehavior(BitWidth width, boolean risingEdge, Direction direction) implements ComponentBehavior {

    public enum Direction {
        UP, DOWN, SELECTABLE
    }

    private static final int CLK = 0;
    private static final int ENABLE = 1;
    private static final int RESET = 2;
    private static final int UP_DOWN = 3;

    @Override
    public void evaluate(ComponentContext context) {
        VectorRegisterState state = (VectorRegisterState) context.state();
        LogicState clock = LogicOperations.asGateInput(context.readInput(CLK).singleBit());
        boolean edge = risingEdge
                ? state.lastClock == ZERO && clock == ONE
                : state.lastClock == ONE && clock == ZERO;
        LogicState reset = LogicOperations.asGateInput(context.readInput(RESET).singleBit());

        LogicState countingUp = direction == Direction.SELECTABLE
                ? LogicOperations.asGateInput(context.readInput(UP_DOWN).singleBit())
                : (direction == Direction.UP ? ONE : ZERO);

        if (reset == ONE) {
            state.value = LogicVector.repeat(ZERO, width);
        } else if (edge) {
            LogicState enable = LogicOperations.asGateInput(context.readInput(ENABLE).singleBit());
            if (enable == ONE) {
                state.value = counted(state.value, countingUp);
            } else if (enable != ZERO) {
                state.value = LogicVector.repeat(UNKNOWN, width);
            }
        }
        state.lastClock = clock;

        context.driveOutput(0, state.value);
        context.driveOutput(1, LogicVector.single(terminalCount(state.value, countingUp)));
    }

    private LogicVector counted(LogicVector current, LogicState countingUp) {
        OptionalLong defined = current.toUnsignedLong();
        if (defined.isEmpty() || countingUp == UNKNOWN) {
            return LogicVector.repeat(UNKNOWN, width);
        }
        long mask = width.bits() == 64 ? -1L : (1L << width.bits()) - 1;
        long next = countingUp == ONE ? (defined.getAsLong() + 1) & mask
                : (defined.getAsLong() - 1) & mask;
        return LogicVector.fromUnsignedLong(next, width.bits());
    }

    private LogicState terminalCount(LogicVector count, LogicState countingUp) {
        OptionalLong defined = count.toUnsignedLong();
        if (defined.isEmpty() || countingUp == UNKNOWN) {
            return UNKNOWN;
        }
        long mask = width.bits() == 64 ? -1L : (1L << width.bits()) - 1;
        boolean atMax = defined.getAsLong() == mask;
        boolean atZero = defined.getAsLong() == 0;
        return LogicState.of(countingUp == ONE ? atMax : atZero);
    }

    @Override
    public ComponentRuntimeState createState() {
        return new VectorRegisterState(width);
    }

    @Override
    public dev.logicforge.simulation.ComponentDebugSnapshot debugSnapshot(ComponentRuntimeState state) {
        return new dev.logicforge.simulation.ComponentDebugSnapshot(
                java.util.Map.of("Count", ((VectorRegisterState) state).value),
                java.util.List.of(), null, java.util.Map.of());
    }
}
