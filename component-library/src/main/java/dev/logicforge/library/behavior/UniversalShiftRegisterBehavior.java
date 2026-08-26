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
 * A shift register that can hold, parallel-load, or shift either direction, selected by a
 * 2-bit MODE input sampled on the active clock edge: {@code 00}=HOLD, {@code 01}=LOAD,
 * {@code 10}=SHIFT_LEFT (towards the MSB, new bit enters at bit 0 from SERIAL_LEFT),
 * {@code 11}=SHIFT_RIGHT (towards the LSB, new bit enters at the MSB from SERIAL_RIGHT).
 *
 * <p>{@code SERIAL_OUT_LEFT} and {@code SERIAL_OUT_RIGHT} are combinational, not clocked:
 * they always reflect Q's current MSB and LSB respectively — the bit each direction's next
 * shift would drop — so a chain of these can be wired serially without an extra cycle of
 * latency.
 *
 * <p>Ports are {@code PARALLEL_DATA, SERIAL_LEFT, SERIAL_RIGHT, MODE, CLK, RESET} in,
 * {@code Q, SERIAL_OUT_LEFT, SERIAL_OUT_RIGHT} out.
 *
 * @param width the bus width of PARALLEL_DATA/Q
 * @param risingEdge {@code true} to trigger on 0-&gt;1, {@code false} to trigger on 1-&gt;0
 */
public record UniversalShiftRegisterBehavior(BitWidth width, boolean risingEdge) implements ComponentBehavior {

    private static final int PARALLEL_DATA = 0;
    private static final int SERIAL_LEFT = 1;
    private static final int SERIAL_RIGHT = 2;
    private static final int MODE = 3;
    private static final int CLK = 4;
    private static final int RESET = 5;

    private static final long MODE_HOLD = 0;
    private static final long MODE_LOAD = 1;
    private static final long MODE_SHIFT_LEFT = 2;
    private static final long MODE_SHIFT_RIGHT = 3;

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
            OptionalLong mode = LogicOperations.asGateInput(context.readInput(MODE)).toUnsignedLong();
            if (mode.isEmpty()) {
                state.value = LogicVector.repeat(UNKNOWN, width);
            } else if (mode.getAsLong() == MODE_LOAD) {
                state.value = LogicOperations.asGateInput(context.readInput(PARALLEL_DATA));
            } else if (mode.getAsLong() == MODE_SHIFT_LEFT) {
                LogicState in = LogicOperations.asGateInput(context.readInput(SERIAL_LEFT).singleBit());
                state.value = shiftLeft(state.value, in);
            } else if (mode.getAsLong() == MODE_SHIFT_RIGHT) {
                LogicState in = LogicOperations.asGateInput(context.readInput(SERIAL_RIGHT).singleBit());
                state.value = shiftRight(state.value, in);
            }
            // MODE_HOLD: no change.
        }
        state.lastClock = clock;

        context.driveOutput(0, state.value);
        context.driveOutput(1, LogicVector.single(state.value.getBit(width.bits() - 1)));
        context.driveOutput(2, LogicVector.single(state.value.getBit(0)));
    }

    private LogicVector shiftLeft(LogicVector current, LogicState in) {
        if (width.bits() == 1) {
            return LogicVector.single(in);
        }
        return current.slice(0, width.bits() - 1).concat(LogicVector.single(in));
    }

    private LogicVector shiftRight(LogicVector current, LogicState in) {
        if (width.bits() == 1) {
            return LogicVector.single(in);
        }
        return LogicVector.single(in).concat(current.slice(1, width.bits() - 1));
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
