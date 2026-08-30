package dev.logicforge.ui.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.analyzer.WaveformSegment;
import dev.logicforge.logic.LogicVector;
import java.util.List;
import org.junit.jupiter.api.Test;

class WaveformLayoutTest {

    private final TimelineTransform timeline = new TimelineTransform(0, 1_000, 1_000);

    @Test
    void aNormalDurationSegmentIsNotWidenedOrNudged() {
        List<WaveformSegment> segments = List.of(
                new WaveformSegment(0, 100, LogicVector.ZERO));
        var spans = WaveformLayout.layout(segments, timeline, 1.5, 3);

        assertEquals(1, spans.size());
        assertEquals(0, spans.get(0).x0(), 1e-9);
        assertEquals(100, spans.get(0).x1(), 1e-9);
    }

    @Test
    void aZeroDurationSegmentIsWidenedToTheMinimumVisibleWidth() {
        List<WaveformSegment> segments = List.of(
                new WaveformSegment(50, 50, LogicVector.ONE));
        var spans = WaveformLayout.layout(segments, timeline, 1.5, 3);

        assertEquals(50, spans.get(0).x0(), 1e-9);
        assertEquals(51.5, spans.get(0).x1(), 1e-9);
    }

    @Test
    void severalDeltaCycleGlitchesAtOneInstantAreSpreadIntoAVisibleLadder() {
        // A 0 -> 1 -> 0 glitch, all within delta cycles of the same physical time 50.
        List<WaveformSegment> segments = List.of(
                new WaveformSegment(0, 50, LogicVector.ZERO),
                new WaveformSegment(50, 50, LogicVector.ONE),
                new WaveformSegment(50, 50, LogicVector.ZERO),
                new WaveformSegment(50, 200, LogicVector.ZERO));
        var spans = WaveformLayout.layout(segments, timeline, 1.5, 3);

        assertEquals(4, spans.size());
        // The leading segment ending at 50 keeps its true position.
        assertEquals(50, spans.get(0).x1(), 1e-9);
        // The first glitch at time 50 is unshifted (index 0 within the run)...
        assertEquals(50, spans.get(1).x0(), 1e-9);
        // ...the second glitch at the same instant is nudged one step right...
        assertEquals(53, spans.get(2).x0(), 1e-9);
        // ...and the segment that follows, also starting at 50, one step further still.
        assertEquals(56, spans.get(3).x0(), 1e-9);
        assertTrue(spans.get(2).x0() > spans.get(1).x0(),
                "consecutive same-instant glitches must not overdraw the same pixel column");
    }

    @Test
    void aSegmentAtADifferentTimestampResetsTheLadder() {
        List<WaveformSegment> segments = List.of(
                new WaveformSegment(10, 10, LogicVector.ONE),
                new WaveformSegment(10, 10, LogicVector.ZERO),
                new WaveformSegment(20, 20, LogicVector.ONE));
        var spans = WaveformLayout.layout(segments, timeline, 1.5, 3);

        assertEquals(10, spans.get(0).x0(), 1e-9);
        assertEquals(13, spans.get(1).x0(), 1e-9);
        assertEquals(20, spans.get(2).x0(), 1e-9, "a new timestamp starts a fresh ladder, not offset 6");
    }
}
