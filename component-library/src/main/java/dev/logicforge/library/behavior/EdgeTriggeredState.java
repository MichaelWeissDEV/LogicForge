package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicState;
import dev.logicforge.simulation.ComponentRuntimeState;

/**
 * The stored bit of an edge-triggered flip-flop (D, JK, T), plus the clock level last seen
 * so a genuine edge can be told apart from re-evaluation at the same level.
 *
 * <p>{@code lastClock} starts as {@link LogicState#UNKNOWN} rather than a defined level,
 * so a circuit that powers up with CLK already at 1 does not read that as a spurious
 * rising edge — an edge requires having actually observed the opposite level first.
 */
final class EdgeTriggeredState implements ComponentRuntimeState {

    LogicState q = LogicState.ZERO;
    LogicState lastClock = LogicState.UNKNOWN;

    @Override
    public void reset() {
        q = LogicState.ZERO;
        lastClock = LogicState.UNKNOWN;
    }
}
