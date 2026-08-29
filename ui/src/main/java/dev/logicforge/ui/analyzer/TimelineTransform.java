package dev.logicforge.ui.analyzer;

/** Immutable mapping between virtual simulation time (picoseconds) and waveform pixels. */
public record TimelineTransform(long startTime, long endTime, double pixelWidth) {

    public TimelineTransform {
        if (endTime <= startTime) {
            throw new IllegalArgumentException("endTime must be greater than startTime");
        }
        if (!Double.isFinite(pixelWidth) || pixelWidth <= 0) {
            throw new IllegalArgumentException("pixelWidth must be finite and positive");
        }
    }

    public long visibleDuration() {
        return endTime - startTime;
    }

    public double timeToX(long time) {
        return (time - startTime) * pixelWidth / visibleDuration();
    }

    public long xToTime(double x) {
        if (!Double.isFinite(x)) {
            throw new IllegalArgumentException("x must be finite");
        }
        double time = startTime + x * visibleDuration() / pixelWidth;
        if (time >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        if (time <= Long.MIN_VALUE) {
            return Long.MIN_VALUE;
        }
        return Math.round(time);
    }

    public double pixelsPerPicosecond() {
        return pixelWidth / visibleDuration();
    }
}
