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
 * A parallel-in/parallel-out register: on the configured clock edge, while LOAD is 1, Q
 * takes the value on DATA; otherwise it holds. An optional asynchronous RESET clears Q to
 * all zero bits immediately, independent of the clock.
 *
 * <p>Ports are {@code DATA, CLK, LOAD[, RESET]} in, {@code Q} out; {@code RESET} exists
 * only when {@link #hasReset()} is true.
 *
 * @param width the bus width of DATA and Q
 * @param risingEdge {@code true} to trigger on 0-&gt;1, {@code false} to trigger on 1-&gt;0
 * @param hasReset whether this instance has an asynchronous RESET input
 */
public record RegisterBehavior(BitWidth width, boolean risingEdge, boolean hasReset) implements ComponentBehavior {

    private static final int DATA = 0;
    private static final int CLK = 1;
    private static final int LOAD = 2;
    private static final int RESET = 3;

    @Override
    public void evaluate(ComponentContext context) {
        VectorRegisterState state = (VectorRegisterState) context.state();
        LogicState clock = LogicOperations.asGateInput(context.readInput(CLK).singleBit());
        boolean edge = risingEdge
                ? state.lastClock == ZERO && clock == ONE
                : state.lastClock == ONE && clock == ZERO;

        LogicVector normal = state.value;
        if (edge) {
            LogicState load = LogicOperations.asGateInput(context.readInput(LOAD).singleBit());
            normal = StatefulControlPolicy.choose(load, state.value,
                    LogicOperations.asGateInput(context.readInput(DATA)));
        }
        state.value = hasReset
                ? StatefulControlPolicy.choose(context.readInput(RESET).singleBit(), normal,
                        LogicVector.repeat(ZERO, width))
                : normal;
        state.lastClock = clock;
        context.driveOutput(0, state.value);
    }

    @Override
    public ComponentRuntimeState createState() {
        return new VectorRegisterState(width);
    }

    @Override
    public dev.logicforge.simulation.ComponentDebugSnapshot debugSnapshot(ComponentRuntimeState state) {
        return new dev.logicforge.simulation.ComponentDebugSnapshot(
                java.util.Map.of("Stored", ((VectorRegisterState) state).value),
                java.util.List.of(), null, java.util.Map.of());
    }
}
