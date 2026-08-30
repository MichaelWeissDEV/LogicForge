package dev.logicforge.ui.analyzer;

import dev.logicforge.analyzer.WaveformSegment;
import java.util.ArrayList;
import java.util.List;

/**
 * Screen-space X span for each waveform segment.
 *
 * <p>{@link dev.logicforge.analyzer.WaveformSegments} already keeps every delta-cycle value
 * change as its own zero-duration segment (see its own documentation), so the data is
 * delta-cycle accurate. Left to a naive time-to-pixel mapping, several such segments sharing
 * one physical timestamp would all land on the exact same pixel column and overdraw one
 * another, so what a same-instant sequence of glitches actually did would be invisible. This
 * spreads them into a small visible ladder instead, purely as a rendering concern — the
 * underlying segment list, and the analyzer module below it, are untouched.
 */
public final class WaveformLayout {

    private WaveformLayout() {
    }

    /** The pixel span a segment should be painted at, widened and/or nudged for visibility. */
    public record SegmentSpan(WaveformSegment segment, double x0, double x1) {
    }

    /**
     * Lays out segments left to right in order. A segment narrower than {@code minWidth}
     * pixels is widened to at least that. When consecutive segments share the same
     * simulation timestamp — several delta cycles resolving at one physical instant — each
     * one after the first is nudged {@code glitchStep} pixels further right, so a 0→1→0
     * glitch at one instant reads as three distinct marks instead of one blob.
     */
    public static List<SegmentSpan> layout(
            List<WaveformSegment> segments, TimelineTransform timeline, double minWidth, double glitchStep) {
        List<SegmentSpan> spans = new ArrayList<>(segments.size());
        long previousStartTime = 0;
        boolean havePrevious = false;
        int sameTimestampIndex = 0;
        for (WaveformSegment segment : segments) {
            if (havePrevious && segment.startTime() == previousStartTime) {
                sameTimestampIndex++;
            } else {
                sameTimestampIndex = 0;
            }
            previousStartTime = segment.startTime();
            havePrevious = true;

            double offset = sameTimestampIndex * glitchStep;
            double x0 = timeline.timeToX(segment.startTime()) + offset;
            double x1 = Math.max(timeline.timeToX(segment.endTime()) + offset, x0 + minWidth);
            spans.add(new SegmentSpan(segment, x0, x1));
        }
        return List.copyOf(spans);
    }
}
