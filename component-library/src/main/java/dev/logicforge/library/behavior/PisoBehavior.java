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
 * Parallel-in/serial-out: on the active clock edge, LOAD captures DATA in parallel;
 * otherwise, while SHIFT is 1, the register shifts one bit towards the LSB, dropping the
 * old bit 0 (already visible on SERIAL_OUT the cycle before) and pulling SERIAL_IN in at
 * the MSB — chaining several of these lets SERIAL_IN carry the previous stage's SERIAL_OUT.
 * LOAD takes priority over SHIFT.
 *
 * <p>{@code SERIAL_OUT} is combinational, not clocked: it always reflects Q's current bit 0,
 * the bit the next shift will drop.
 *
 * <p>Ports are {@code DATA, LOAD, SHIFT, SERIAL_IN, CLK, RESET} in, {@code Q, SERIAL_OUT} out.
 *
 * @param width the bus width of DATA/Q
 * @param risingEdge {@code true} to trigger on 0-&gt;1, {@code false} to trigger on 1-&gt;0
 */
public record PisoBehavior(BitWidth width, boolean risingEdge) implements ComponentBehavior {

    private static final int DATA = 0;
    private static final int LOAD = 1;
    private static final int SHIFT = 2;
    private static final int SERIAL_IN = 3;
    private static final int CLK = 4;
    private static final int RESET = 5;

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
            LogicState load = LogicOperations.asGateInput(context.readInput(LOAD).singleBit());
            LogicState shift = LogicOperations.asGateInput(context.readInput(SHIFT).singleBit());
            if (load == ONE) {
                state.value = LogicOperations.asGateInput(context.readInput(DATA));
            } else if (load == ZERO && shift == ONE) {
                LogicState in = LogicOperations.asGateInput(context.readInput(SERIAL_IN).singleBit());
                state.value = width.bits() == 1
                        ? LogicVector.single(in)
                        : LogicVector.single(in).concat(state.value.slice(1, width.bits() - 1));
            } else if (load != ZERO || shift != ZERO) {
                state.value = LogicVector.repeat(UNKNOWN, width);
            }
        }
        state.lastClock = clock;

        context.driveOutput(0, state.value);
        context.driveOutput(1, LogicVector.single(state.value.getBit(0)));
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
