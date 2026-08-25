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
 * Latches the four standard condition-code flags on the configured clock edge while LOAD
 * is 1, so a combinational ALU's transient Z/C/N/V outputs can be held for later branch
 * decisions — the same load/hold behaviour as {@link RegisterBehavior}, but with the four
 * flag bits as separate named inputs rather than one bus, since they typically come from
 * four different wires rather than one.
 *
 * <p>Ports are {@code Z, C, N, V, CLK, LOAD} in, {@code FLAGS} out (4 bits, LSB first:
 * bit 0 = Z, bit 1 = C, bit 2 = N, bit 3 = V).
 *
 * @param risingEdge {@code true} to trigger on 0-&gt;1, {@code false} to trigger on 1-&gt;0
 */
public record FlagsRegisterBehavior(boolean risingEdge) implements ComponentBehavior {

    private static final BitWidth WIDTH = BitWidth.of(4);

    private static final int IN_Z = 0;
    private static final int IN_C = 1;
    private static final int IN_N = 2;
    private static final int IN_V = 3;
    private static final int IN_CLK = 4;
    private static final int IN_LOAD = 5;

    @Override
    public void evaluate(ComponentContext context) {
        VectorRegisterState state = (VectorRegisterState) context.state();
        LogicState clock = LogicOperations.asGateInput(context.readInput(IN_CLK).singleBit());
        boolean edge = risingEdge
                ? state.lastClock == ZERO && clock == ONE
                : state.lastClock == ONE && clock == ZERO;

        if (edge) {
            LogicState load = LogicOperations.asGateInput(context.readInput(IN_LOAD).singleBit());
            if (load == ONE) {
                LogicState z = LogicOperations.asGateInput(context.readInput(IN_Z).singleBit());
                LogicState c = LogicOperations.asGateInput(context.readInput(IN_C).singleBit());
                LogicState n = LogicOperations.asGateInput(context.readInput(IN_N).singleBit());
                LogicState v = LogicOperations.asGateInput(context.readInput(IN_V).singleBit());
                state.value = LogicVector.ofLsbFirst(z, c, n, v);
            } else if (load != ZERO) {
                state.value = LogicVector.repeat(LogicState.UNKNOWN, WIDTH);
            }
        }
        state.lastClock = clock;
        context.driveOutput(0, state.value);
    }

    @Override
    public ComponentRuntimeState createState() {
        return new VectorRegisterState(WIDTH);
    }
}
