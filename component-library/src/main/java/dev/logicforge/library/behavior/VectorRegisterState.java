package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentRuntimeState;

/**
 * The stored word of a bus-width sequential component (register, counter, shift
 * register), plus the clock level last observed for edge detection. Power-on value is all
 * zero bits, the deterministic default the project asked for.
 */
final class VectorRegisterState implements ComponentRuntimeState {

    private final BitWidth width;
    LogicVector value;
    LogicState lastClock = LogicState.UNKNOWN;

    VectorRegisterState(BitWidth width) {
        this.width = width;
        this.value = LogicVector.repeat(LogicState.ZERO, width);
    }

    @Override
    public void reset() {
        value = LogicVector.repeat(LogicState.ZERO, width);
        lastClock = LogicState.UNKNOWN;
    }
}
