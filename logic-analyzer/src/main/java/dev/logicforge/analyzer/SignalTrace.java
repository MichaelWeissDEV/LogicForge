package dev.logicforge.analyzer;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicVector;
import java.util.ArrayList;
import java.util.List;

/**
 * The recorded history of one net: a label for display, its bit width, and the ordered
 * transitions captured for it so far.
 *
 * <p>Consecutive identical values are never recorded twice — a net that does not change
 * produces no new transition — so {@link #transitions()} is exactly the waveform's corner
 * points.
 */
public final class SignalTrace {

    private final int netId;
    private final String label;
    private final BitWidth width;
    private final List<SignalTransition> transitions = new ArrayList<>();

    SignalTrace(int netId, String label, BitWidth width) {
        this.netId = netId;
        this.label = label;
        this.width = width;
    }

    public int netId() {
        return netId;
    }

    public String label() {
        return label;
    }

    public BitWidth width() {
        return width;
    }

    /** The transitions recorded so far, oldest first. */
    public List<SignalTransition> transitions() {
        return List.copyOf(transitions);
    }

    public boolean isEmpty() {
        return transitions.isEmpty();
    }

    /**
     * The value this trace held at {@code time}: the value of the last transition at or
     * before it, or empty if nothing was recorded yet that early.
     */
    public java.util.Optional<LogicVector> valueAt(long time) {
        SignalTransition last = null;
        for (SignalTransition transition : transitions) {
            if (transition.time() > time) {
                break;
            }
            last = transition;
        }
        return java.util.Optional.ofNullable(last).map(SignalTransition::value);
    }

    void record(long time, int deltaCycle, LogicVector value) {
        if (!transitions.isEmpty()) {
            SignalTransition last = transitions.get(transitions.size() - 1);
            if (last.value().equals(value)) {
                return;
            }
        }
        transitions.add(new SignalTransition(time, deltaCycle, value));
    }

    void clear() {
        transitions.clear();
    }
}
