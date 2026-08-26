package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;

/** Shared conservative four-state policy for controls that choose a stored next state. */
final class StatefulControlPolicy {

    private StatefulControlPolicy() {
    }

    /** Chooses inactive/active, or merges both possible results when the control is X/Z. */
    static LogicVector choose(LogicState control, LogicVector inactive, LogicVector active) {
        return switch (LogicOperations.asGateInput(control)) {
            case ZERO -> inactive;
            case ONE -> active;
            case UNKNOWN, HIGH_IMPEDANCE -> merge(inactive, active);
        };
    }

    /** Bitwise possible-state merge: equal results survive; disagreements become X. */
    static LogicVector merge(LogicVector first, LogicVector second) {
        if (first.width() != second.width()) {
            throw new IllegalArgumentException("Cannot merge state vectors of different widths");
        }
        LogicVector result = first;
        for (int bit = 0; bit < first.width(); bit++) {
            if (first.getBit(bit) != second.getBit(bit)) {
                result = result.withBit(bit, LogicState.UNKNOWN);
            }
        }
        return result;
    }

    /** Selects every concrete choice compatible with a four-state selector and merges them. */
    static LogicVector select(LogicVector selector, LogicVector... choices) {
        LogicVector gateSelector = LogicOperations.asGateInput(selector);
        if (choices.length != 1 << gateSelector.width()) {
            throw new IllegalArgumentException("Selector width does not match choice count");
        }
        LogicVector result = null;
        for (int choice = 0; choice < choices.length; choice++) {
            if (isPossible(gateSelector, choice)) {
                result = result == null ? choices[choice] : merge(result, choices[choice]);
            }
        }
        return result;
    }

    static boolean isPossible(LogicVector selector, int value) {
        LogicVector gateSelector = LogicOperations.asGateInput(selector);
        for (int bit = 0; bit < gateSelector.width(); bit++) {
            LogicState selected = gateSelector.getBit(bit);
            LogicState candidate = ((value >>> bit) & 1) == 0
                    ? LogicState.ZERO : LogicState.ONE;
            if (selected.isDefined() && selected != candidate) {
                return false;
            }
        }
        return true;
    }
}
