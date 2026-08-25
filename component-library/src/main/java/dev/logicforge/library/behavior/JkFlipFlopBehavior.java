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
 * An edge-triggered JK flip-flop.
 *
 * <pre>
 *   J K | Q+
 *   0 0 | hold
 *   1 0 | 1
 *   0 1 | 0
 *   1 1 | toggle
 * </pre>
 *
 * Ports are {@code J, K, CLK}.
 *
 * @param risingEdge {@code true} to trigger on 0-&gt;1, {@code false} to trigger on 1-&gt;0
 */
public record JkFlipFlopBehavior(boolean risingEdge) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        EdgeTriggeredState state = (EdgeTriggeredState) context.state();
        LogicState clock = LogicOperations.asGateInput(context.readInput(2).singleBit());
        boolean edge = risingEdge
                ? state.lastClock == ZERO && clock == ONE
                : state.lastClock == ONE && clock == ZERO;

        if (edge) {
            LogicState j = LogicOperations.asGateInput(context.readInput(0).singleBit());
            LogicState k = LogicOperations.asGateInput(context.readInput(1).singleBit());
            if (j == ONE && k == ONE) {
                state.q = LogicOperations.not(state.q);
            } else if (j == ONE && k == ZERO) {
                state.q = ONE;
            } else if (j == ZERO && k == ONE) {
                state.q = ZERO;
            } else if (j != ZERO || k != ZERO) {
                state.q = UNKNOWN;
            }
            // j == ZERO && k == ZERO: hold, state.q already has the right value
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
