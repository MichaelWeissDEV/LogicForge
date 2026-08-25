package dev.logicforge.library.behavior;

import static dev.logicforge.logic.LogicState.ONE;
import static dev.logicforge.logic.LogicState.UNKNOWN;
import static dev.logicforge.logic.LogicState.ZERO;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;

/**
 * An edge-triggered T flip-flop: on the configured clock edge, toggles Q when T is 1 and
 * holds when T is 0. Ports are {@code T, CLK}.
 *
 * @param risingEdge {@code true} to trigger on 0-&gt;1, {@code false} to trigger on 1-&gt;0
 */
public record TFlipFlopBehavior(boolean risingEdge) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        EdgeTriggeredState state = (EdgeTriggeredState) context.state();
        LogicState clock = LogicOperations.asGateInput(context.readInput(1).singleBit());
        boolean edge = risingEdge
                ? state.lastClock == ZERO && clock == ONE
                : state.lastClock == ONE && clock == ZERO;

        if (edge) {
            LogicState t = LogicOperations.asGateInput(context.readInput(0).singleBit());
            if (t == ONE) {
                state.q = LogicOperations.not(state.q);
            } else if (t != ZERO) {
                state.q = UNKNOWN;
            }
        }
        state.lastClock = clock;
        context.driveOutput(0, LogicVector.single(state.q));
        context.driveOutput(1, LogicVector.single(LogicOperations.not(state.q)));
    }

    @Override
    public ComponentRuntimeState createState() {
        return new EdgeTriggeredState();
    }
}
