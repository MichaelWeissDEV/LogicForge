package dev.logicforge.analyzer;

import dev.logicforge.logic.LogicVector;

/** A value held over a timestamp interval; zero-duration intervals preserve delta-cycle pulses. */
public record WaveformSegment(long startTime, long endTime, LogicVector value) {

    public WaveformSegment {
        if (endTime < startTime) {
            throw new IllegalArgumentException("endTime must not precede startTime");
        }
        java.util.Objects.requireNonNull(value, "value");
    }

    public boolean intersects(long visibleStart, long visibleEnd) {
        return endTime >= visibleStart && startTime <= visibleEnd;
    }
}
