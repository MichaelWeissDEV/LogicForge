package dev.logicforge.library.behavior;

import static dev.logicforge.logic.LogicState.ONE;
import static dev.logicforge.logic.LogicState.UNKNOWN;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;

/**
 * An edge-triggered D flip-flop: on the configured clock edge, Q takes the value D had at
 * that instant; between edges Q holds. Optionally has asynchronous SET/RESET inputs that
 * override the clocked behaviour immediately, independent of any edge.
 *
 * <p>Ports are {@code D, CLK[, SET, RESET]} in that order; {@code SET}/{@code RESET} exist
 * only when {@link #hasAsyncControls()} is true.
 *
 * @param risingEdge {@code true} to trigger on 0-&gt;1, {@code false} to trigger on 1-&gt;0
 * @param hasAsyncControls whether this instance has SET/RESET inputs
 */
public record DFlipFlopBehavior(boolean risingEdge, boolean hasAsyncControls) implements ComponentBehavior {

    private static final int D = 0;
    private static final int CLK = 1;
    private static final int SET = 2;
    private static final int RESET = 3;

    @Override
    public void evaluate(ComponentContext context) {
        EdgeTriggeredState state = (EdgeTriggeredState) context.state();
        LogicState clock = LogicOperations.asGateInput(context.readInput(CLK).singleBit());
        boolean edge = risingEdge
                ? state.lastClock == LogicState.ZERO && clock == ONE
                : state.lastClock == ONE && clock == LogicState.ZERO;

        LogicState normal = edge
                ? LogicOperations.asGateInput(context.readInput(D).singleBit()) : state.q;
        LogicState q = normal;
        if (hasAsyncControls) {
            LogicState set = LogicOperations.asGateInput(context.readInput(SET).singleBit());
            LogicState reset = LogicOperations.asGateInput(context.readInput(RESET).singleBit());
            q = StatefulControlPolicy.select(LogicVector.ofLsbFirst(set, reset),
                    LogicVector.single(normal), LogicVector.ONE, LogicVector.ZERO,
                    LogicVector.UNKNOWN).singleBit();
        }
        state.q = q;
        state.lastClock = clock;
        context.driveOutput(0, LogicVector.single(q));
        context.driveOutput(1, LogicVector.single(LogicOperations.not(q)));
    }

    @Override
    public ComponentRuntimeState createState() {
        return new EdgeTriggeredState();
    }
}
