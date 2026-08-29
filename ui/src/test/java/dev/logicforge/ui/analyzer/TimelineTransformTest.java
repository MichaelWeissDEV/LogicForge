package dev.logicforge.ui.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TimelineTransformTest {

    @Test
    void mapsTransitionAtTwentyNanosecondsToItsTimestampPosition() {
        TimelineTransform transform = new TimelineTransform(0, 40_000, 400);
        assertEquals(200, transform.timeToX(20_000), 1e-9);
    }

    @Test
    void irregularTimestampsMapProportionally() {
        TimelineTransform transform = new TimelineTransform(0, 40, 400);
        assertEquals(70, transform.timeToX(7), 1e-9);
        assertEquals(250, transform.timeToX(25), 1e-9);
    }

    @Test
    void timeToPixelRoundTripIsStableWithinOnePicosecond() {
        TimelineTransform transform = new TimelineTransform(7, 90_007, 1_237);
        for (long time : new long[]{7, 25, 20_000, 89_999, 90_007}) {
            assertEquals(time, transform.xToTime(transform.timeToX(time)), 1);
        }
    }
}
