package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicState;
import dev.logicforge.simulation.ComponentRuntimeState;

/** A running edge count plus the divided output level and the clock level last observed. */
final class ClockDividerState implements ComponentRuntimeState {

    LogicState lastClock = LogicState.UNKNOWN;
    long edgeCount;
    boolean edgeCountUnknown;
    LogicState output = LogicState.ZERO;

    @Override
    public void reset() {
        lastClock = LogicState.UNKNOWN;
        edgeCount = 0;
        edgeCountUnknown = false;
        output = LogicState.ZERO;
    }

    @Override
    public Object snapshot() {
        return new Snapshot(lastClock, edgeCount, edgeCountUnknown, output);
    }

    @Override
    public void restore(Object snap) {
        if (snap instanceof Snapshot s) {
            lastClock = s.lastClock();
            edgeCount = s.edgeCount();
            edgeCountUnknown = s.edgeCountUnknown();
            output = s.output();
        }
    }

    record Snapshot(LogicState lastClock, long edgeCount, boolean edgeCountUnknown,
                    LogicState output) {}
}
