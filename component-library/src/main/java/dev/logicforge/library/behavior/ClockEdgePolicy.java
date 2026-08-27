package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;

/** Conservative active-edge classification once a clock has been observed low. */
final class ClockEdgePolicy {
    private ClockEdgePolicy() {
    }

    static LogicState rising(LogicState previous, LogicState current) {
        if (LogicOperations.asGateInput(previous) != LogicState.ZERO) {
            // UNKNOWN is also the initial clock state; do not invent a startup edge.
            return LogicState.ZERO;
        }
        return switch (LogicOperations.asGateInput(current)) {
            case ZERO -> LogicState.ZERO;
            case ONE -> LogicState.ONE;
            case UNKNOWN, HIGH_IMPEDANCE -> LogicState.UNKNOWN;
        };
    }
}
