package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicState;
import dev.logicforge.simulation.ComponentRuntimeState;

/** The stored bit of a level-sensitive latch (SR, D). */
final class LatchState implements ComponentRuntimeState {

    LogicState q = LogicState.ZERO;

    @Override
    public void reset() {
        q = LogicState.ZERO;
    }
}
