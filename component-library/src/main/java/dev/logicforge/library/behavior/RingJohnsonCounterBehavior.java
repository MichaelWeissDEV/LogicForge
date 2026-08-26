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

/**
 * Two educational "twisted ring" counters that shift a single bit pattern around a loop of
 * flip-flops rather than counting in binary. Both feed Q's current MSB back in at bit 0 on
 * every active clock edge, shifting everything else up by one; they differ only in whether
 * that feedback bit is inverted.
 *
 * <p>A {@link Kind#RING} counter resets to {@code 0...01} and walks that single 1 bit
 * around a {@code width}-cycle loop. A {@link Kind#JOHNSON} counter resets to all zero and
 * inverts the fed-back bit, producing a {@code 2 * width}-cycle sequence that counts up in
 * ones then back down in zeros (e.g. for width 4: 0000, 0001, 0011, 0111, 1111, 1110, 1100,
 * 1000, back to 0000).
 *
 * <p>Ports are {@code CLK, RESET} in, {@code Q} out.
 *
 * @param width the bus width of Q
 * @param risingEdge {@code true} to trigger on 0-&gt;1, {@code false} to trigger on 1-&gt;0
 * @param kind which of the two feedback rules this instance uses
 */
public record RingJohnsonCounterBehavior(BitWidth width, boolean risingEdge, Kind kind) implements ComponentBehavior {

    public enum Kind { RING, JOHNSON }

    private static final int CLK = 0;
    private static final int RESET = 1;

    @Override
    public void evaluate(ComponentContext context) {
        VectorRegisterState state = (VectorRegisterState) context.state();
        LogicState clock = LogicOperations.asGateInput(context.readInput(CLK).singleBit());
        boolean edge = risingEdge
                ? state.lastClock == ZERO && clock == ONE
                : state.lastClock == ONE && clock == ZERO;
        LogicState reset = LogicOperations.asGateInput(context.readInput(RESET).singleBit());

        if (reset == ONE) {
            state.value = kind == Kind.RING
                    ? LogicVector.fromUnsignedLong(1, width.bits())
                    : LogicVector.repeat(ZERO, width);
        } else if (edge) {
            LogicState msb = LogicOperations.asGateInput(state.value.getBit(width.bits() - 1));
            LogicState feedback = kind == Kind.RING ? msb : LogicOperations.not(msb);
            state.value = feedback == UNKNOWN
                    ? LogicVector.repeat(UNKNOWN, width)
                    : shiftInAtLsb(state.value, feedback);
        }
        state.lastClock = clock;

        context.driveOutput(0, state.value);
    }

    private LogicVector shiftInAtLsb(LogicVector current, LogicState in) {
        if (width.bits() == 1) {
            return LogicVector.single(in);
        }
        return current.slice(0, width.bits() - 1).concat(LogicVector.single(in));
    }

    @Override
    public ComponentRuntimeState createState() {
        return new VectorRegisterState(width);
    }

    @Override
    public dev.logicforge.simulation.ComponentDebugSnapshot debugSnapshot(ComponentRuntimeState state) {
        return new dev.logicforge.simulation.ComponentDebugSnapshot(
                java.util.Map.of("Q", ((VectorRegisterState) state).value),
                java.util.List.of(), null, java.util.Map.of());
    }
}
