package dev.logicforge.analyzer;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicVector;
import java.util.ArrayList;
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

    private final AnalyzerSignalBinding binding;
    private final String label;
    private final BitWidth width;
    private final List<SignalTransition> transitions = new ArrayList<>();

    SignalTrace(AnalyzerSignalBinding binding, String label, BitWidth width) {
        this.binding = binding;
        this.label = label;
        this.width = width;
    }

    /** The runtime net or nets this trace was reconstructed from. */
    public AnalyzerSignalBinding binding() {
        return binding;
    }

    /**
     * The single net this trace watches, for the common case of a {@link
     * AnalyzerSignalBinding.Vector} or {@link AnalyzerSignalBinding.Scalar} binding. Throws
     * for a {@link AnalyzerSignalBinding.Bits} binding, which has no single net — use {@link
     * #binding()} instead for signals that may span several.
     */
    public int netId() {
        List<Integer> nets = binding.netIds();
        if (nets.size() != 1) {
            throw new IllegalStateException(
                    "This trace's binding spans " + nets.size() + " nets, not one: " + binding);
        }
        return nets.get(0);
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
        SignalTrace extracted = new SignalTrace(binding, label + "[" + bitIndex + "]", BitWidth.ONE);
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
