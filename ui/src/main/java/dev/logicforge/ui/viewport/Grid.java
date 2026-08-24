package dev.logicforge.ui.viewport;

import dev.logicforge.circuit.geometry.CircuitPoint;

/**
 * The editing grid. Every component sits on it, and so does every port, which is what
 * makes wires meet their ports exactly.
 */
public final class Grid {

    /** Distance between two grid lines, in circuit units. */
    public static final double SPACING = 8;

    /** Every fourth line is drawn stronger. */
    public static final int MAJOR_EVERY = 4;

    private Grid() {
    }

    public static CircuitPoint snap(CircuitPoint point) {
        return point.snappedTo(SPACING);
    }

    public static double snap(double value) {
        return Math.round(value / SPACING) * SPACING;
    }

    /** The first grid line at or after {@code worldStart}. */
    public static double firstLineAtOrAfter(double worldStart) {
        return Math.ceil(worldStart / SPACING) * SPACING;
    }

    public static boolean isMajorLine(double worldCoordinate) {
        double step = SPACING * MAJOR_EVERY;
        return Math.abs(Math.IEEEremainder(worldCoordinate, step)) < SPACING / 2;
    }
}
