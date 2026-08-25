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
 * A level-sensitive D latch: transparent (Q follows D) while EN is 1, holds its last value
 * while EN is 0. An unknown EN cannot say which of those applies, so Q becomes X.
 */
public final class DLatchBehavior implements ComponentBehavior {

    public static final DLatchBehavior INSTANCE = new DLatchBehavior();

    @Override
    public void evaluate(ComponentContext context) {
        LogicState d = LogicOperations.asGateInput(context.readInput(0).singleBit());
        LogicState en = LogicOperations.asGateInput(context.readInput(1).singleBit());
        LatchState state = (LatchState) context.state();

        LogicState q = switch (en) {
            case ONE -> d;
            case ZERO -> state.q;
            default -> UNKNOWN;
        };
        state.q = q;
        context.driveOutput(0, LogicVector.single(q));
        context.driveOutput(1, LogicVector.single(LogicOperations.not(q)));
    }

    @Override
    public ComponentRuntimeState createState() {
        return new LatchState();
    }
}
