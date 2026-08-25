package dev.logicforge.library.behavior;

import static dev.logicforge.logic.LogicState.ONE;
import static dev.logicforge.logic.LogicState.ZERO;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;

/**
 * A serial-in, parallel-out shift register: on each clock edge the whole word shifts one
 * position towards the most significant bit and SIN enters at bit 0. SOUT taps the current
 * most significant bit, the value the next edge will shift out — the same way a real shift
 * register's serial output is just the last stage's Q.
 *
 * <p>Ports are {@code SIN, CLK, RESET} in, {@code Q, SOUT} out. RESET is asynchronous and
 * clears the register to all zero bits.
 *
 * @param width the bus width of Q
 * @param risingEdge {@code true} to trigger on 0-&gt;1, {@code false} to trigger on 1-&gt;0
 */
public record ShiftRegisterBehavior(BitWidth width, boolean risingEdge) implements ComponentBehavior {

    private static final int SIN = 0;
    private static final int CLK = 1;
    private static final int RESET = 2;

    @Override
    public void evaluate(ComponentContext context) {
        VectorRegisterState state = (VectorRegisterState) context.state();
        LogicState clock = LogicOperations.asGateInput(context.readInput(CLK).singleBit());
        boolean edge = risingEdge
                ? state.lastClock == ZERO && clock == ONE
                : state.lastClock == ONE && clock == ZERO;
        LogicState reset = LogicOperations.asGateInput(context.readInput(RESET).singleBit());

        if (reset == ONE) {
            state.value = LogicVector.repeat(ZERO, width);
        } else if (edge) {
            LogicState sin = LogicOperations.asGateInput(context.readInput(SIN).singleBit());
            state.value = shiftIn(state.value, sin);
        }
        state.lastClock = clock;

        LogicState soutBit = state.value.getBit(width.bits() - 1);
        context.driveOutput(0, state.value);
        context.driveOutput(1, LogicVector.single(soutBit));
    }

    private LogicVector shiftIn(LogicVector old, LogicState sin) {
        if (width.bits() == 1) {
            return LogicVector.single(sin);
        }
        LogicVector upper = old.slice(0, width.bits() - 1);
        return upper.concat(LogicVector.single(sin));
    }

    @Override
    public ComponentRuntimeState createState() {
        return new VectorRegisterState(width);
    }
}
