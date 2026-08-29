package dev.logicforge.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WaveformSegmentsTest {

    @Test
    void repeatedDigitalSamplesCollapseToThreeSegments() {
        List<SignalTransition> samples = List.of(
                sample(0, LogicState.ZERO), sample(10, LogicState.ZERO),
                sample(20, LogicState.ONE), sample(30, LogicState.ONE),
                sample(40, LogicState.ZERO));

        List<WaveformSegment> segments = WaveformSegments.fromTransitions(samples, 50);

        assertEquals(3, segments.size());
        assertEquals(new WaveformSegment(0, 20, LogicVector.ZERO), segments.get(0));
        assertEquals(new WaveformSegment(20, 40, LogicVector.ONE), segments.get(1));
        assertEquals(new WaveformSegment(40, 50, LogicVector.ZERO), segments.get(2));
    }

    @Test
    void irregularTimestampsRemainExactSegmentBoundaries() {
        List<WaveformSegment> segments = WaveformSegments.fromTransitions(List.of(
                sample(0, LogicState.ZERO), sample(7, LogicState.ONE),
                sample(25, LogicState.ZERO), sample(40, LogicState.ONE)), 55);

        assertEquals(List.of(0L, 7L, 25L, 40L),
                segments.stream().map(WaveformSegment::startTime).toList());
        assertEquals(List.of(7L, 25L, 40L, 55L),
                segments.stream().map(WaveformSegment::endTime).toList());
    }

    @Test
    void oneTickPulseAndFourStateIntervalsAreNeverDropped() {
        List<SignalTransition> samples = List.of(
                sample(0, LogicState.ZERO), sample(100, LogicState.ONE),
                sample(101, LogicState.ZERO), sample(200, LogicState.UNKNOWN),
                sample(201, LogicState.HIGH_IMPEDANCE), sample(202, LogicState.ONE));

        List<WaveformSegment> segments = WaveformSegments.fromTransitions(samples, 300);

        assertEquals(6, segments.size());
        assertEquals(1, segments.get(1).endTime() - segments.get(1).startTime());
        assertEquals(LogicState.UNKNOWN, segments.get(3).value().singleBit());
        assertEquals(LogicState.HIGH_IMPEDANCE, segments.get(4).value().singleBit());
    }

    @Test
    void longStableSignalProducesOneDrawableSegment() {
        List<SignalTransition> samples = new ArrayList<>();
        for (int time = 0; time < 10_000; time++) samples.add(sample(time, LogicState.ZERO));

        assertEquals(1, WaveformSegments.fromTransitions(samples, 10_000).size());
    }

    private static SignalTransition sample(long time, LogicState state) {
        return new SignalTransition(time, 0, LogicVector.single(state));
    }
}
