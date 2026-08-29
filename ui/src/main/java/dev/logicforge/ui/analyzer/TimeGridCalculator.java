package dev.logicforge.ui.analyzer;

import java.util.Locale;

/** Adaptive 1/2/5 timing grid and engineering-unit formatting for the analyzer. */
public final class TimeGridCalculator {

    public static final double DEFAULT_MAJOR_PIXEL_SPACING = 90;

    public record Grid(long majorStep, long minorStep) {
        public Grid {
            if (majorStep < 1 || minorStep < 1 || majorStep < minorStep) {
                throw new IllegalArgumentException("invalid grid steps");
            }
        }
    }

    private TimeGridCalculator() {
    }

    public static Grid calculate(TimelineTransform transform) {
        return calculate(transform, DEFAULT_MAJOR_PIXEL_SPACING);
    }

    public static Grid calculate(TimelineTransform transform, double minimumMajorPixels) {
        if (!Double.isFinite(minimumMajorPixels) || minimumMajorPixels <= 0) {
            throw new IllegalArgumentException("minimumMajorPixels must be positive");
        }
        double raw = minimumMajorPixels / transform.pixelsPerPicosecond();
        long major = niceStep(raw);
        long minor = Math.max(1, major / 5);
        return new Grid(major, minor);
    }

    /** Smallest 1/2/5×10^n value greater than or equal to {@code rawStep}. */
    public static long niceStep(double rawStep) {
        if (!Double.isFinite(rawStep) || rawStep <= 1) {
            return 1;
        }
        double magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
        double residual = rawStep / magnitude;
        double multiplier = residual <= 1 ? 1 : residual <= 2 ? 2 : residual <= 5 ? 5 : 10;
        double result = multiplier * magnitude;
        return result >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(1, Math.round(result));
    }

    /** Formats picoseconds using the most readable ps/ns/µs/ms/s unit. */
    public static String formatTime(long picoseconds) {
        long magnitude = picoseconds == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(picoseconds);
        double divisor;
        String unit;
        if (magnitude >= 1_000_000_000_000L) {
            divisor = 1e12;
            unit = "s";
        } else if (magnitude >= 1_000_000_000L) {
            divisor = 1e9;
            unit = "ms";
        } else if (magnitude >= 1_000_000L) {
            divisor = 1e6;
            unit = "µs";
        } else if (magnitude >= 1_000L) {
            divisor = 1e3;
            unit = "ns";
        } else {
            divisor = 1;
            unit = "ps";
        }
        double value = picoseconds / divisor;
        String formatted = value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
        return formatted + " " + unit;
    }

    public static String formatFrequency(long periodPicoseconds) {
        if (periodPicoseconds <= 0) {
            return "–";
        }
        double hertz = 1e12 / periodPicoseconds;
        String unit;
        double scaled;
        if (hertz >= 1e9) {
            unit = "GHz";
            scaled = hertz / 1e9;
        } else if (hertz >= 1e6) {
            unit = "MHz";
            scaled = hertz / 1e6;
        } else if (hertz >= 1e3) {
            unit = "kHz";
            scaled = hertz / 1e3;
        } else {
            unit = "Hz";
            scaled = hertz;
        }
        return String.format(Locale.ROOT, scaled >= 100 ? "%.0f %s" : "%.3f %s", scaled, unit)
                .replaceAll("(\\.\\d*?)0+(?= )", "$1")
                .replaceAll("\\.(?= )", "");
    }
}
