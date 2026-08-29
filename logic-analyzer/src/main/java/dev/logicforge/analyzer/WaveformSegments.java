package dev.logicforge.analyzer;

import java.util.ArrayList;
import java.util.List;

/** Converts timestamped values into compact, edge-preserving waveform intervals. */
public final class WaveformSegments {

    private WaveformSegments() {
    }

    /**
     * Builds segments from timestamped samples or transitions. Adjacent equal values are
     * coalesced, but every actual value change remains represented, including several
     * changes at one physical time in different delta cycles.
     */
    public static List<WaveformSegment> fromTransitions(
            List<SignalTransition> transitions, long captureEnd) {
        if (transitions.isEmpty()) {
            return List.of();
        }
        List<SignalTransition> compact = new ArrayList<>(transitions.size());
        for (SignalTransition transition : transitions) {
            if (compact.isEmpty() || !compact.getLast().value().equals(transition.value())) {
                compact.add(transition);
            }
        }

        List<WaveformSegment> result = new ArrayList<>(compact.size());
        for (int index = 0; index < compact.size(); index++) {
            SignalTransition current = compact.get(index);
            long end = index + 1 < compact.size()
                    ? compact.get(index + 1).time()
                    : Math.max(captureEnd, current.time());
            result.add(new WaveformSegment(current.time(), Math.max(current.time(), end), current.value()));
        }
        return List.copyOf(result);
    }

    /** Returns only segments touching the viewport, plus the state entering from its left. */
    public static List<WaveformSegment> visible(
            List<WaveformSegment> segments, long visibleStart, long visibleEnd) {
        if (visibleEnd < visibleStart) {
            throw new IllegalArgumentException("visibleEnd must not precede visibleStart");
        }
        return segments.stream()
                .filter(segment -> segment.intersects(visibleStart, visibleEnd))
                .toList();
    }
}
