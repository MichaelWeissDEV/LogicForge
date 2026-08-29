package dev.logicforge.analyzer;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicVector;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The recorded history of one watched signal: a label for display, its bit width, the
 * runtime binding it was reconstructed from, and the ordered transitions captured so far.
 *
 * <p>Consecutive identical values are never recorded twice — a signal that does not change
 * produces no new transition — so {@link #transitions()} is exactly the waveform's corner
 * points.
 */
public final class SignalTrace {

    /** Large enough for long captures, while keeping analyzer memory use predictable. */
    public static final int DEFAULT_CAPACITY = 100_000;

    private final AnalyzerSignalBinding binding;
    private final String label;
    private final BitWidth width;
    private final int capacity;
    private final Deque<SignalTransition> transitions;

    SignalTrace(AnalyzerSignalBinding binding, String label, BitWidth width) {
        this(binding, label, width, DEFAULT_CAPACITY);
    }

    SignalTrace(AnalyzerSignalBinding binding, String label, BitWidth width, int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.binding = binding;
        this.label = label;
        this.width = width;
        this.capacity = capacity;
        this.transitions = new ArrayDeque<>(Math.min(capacity, 1_024));
    }

    /** The runtime net or nets this trace was reconstructed from. */
    public AnalyzerSignalBinding binding() {
        return binding;
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

    /** A derived scalar view of one bit of this recorded vector. */
    public SignalTrace bit(int bitIndex) {
        if (bitIndex < 0 || bitIndex >= width.bits()) {
            throw new IndexOutOfBoundsException("Bit " + bitIndex + " of " + width);
        }
        SignalTrace extracted = new SignalTrace(
                binding, label + "[" + bitIndex + "]", BitWidth.ONE, capacity);
        for (SignalTransition transition : transitions) {
            extracted.record(transition.time(), transition.deltaCycle(),
                    LogicVector.single(transition.value().getBit(bitIndex)));
        }
        return extracted;
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
            SignalTransition last = transitions.getLast();
            if (last.value().equals(value)) {
                return;
            }
        }
        transitions.addLast(new SignalTransition(time, deltaCycle, value));
        if (transitions.size() > capacity) {
            transitions.removeFirst();
        }
    }

    void clear() {
        transitions.clear();
    }
}
