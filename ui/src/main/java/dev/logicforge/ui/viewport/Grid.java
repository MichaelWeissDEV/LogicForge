package dev.logicforge.ui.viewport;

import dev.logicforge.circuit.geometry.CircuitGrid;
import dev.logicforge.circuit.geometry.CircuitPoint;

/**
 * The editing grid. Every component sits on it, and so does every port, which is what
 * makes wires meet their ports exactly.
 */
public final class Grid {

    /** Distance between two grid lines, in circuit units. */
    public static final double SPACING = CircuitGrid.SPACING;

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

    /** {@code true} if a coordinate falls on a line drawn at {@code step} intervals. */
    public static boolean isOnLine(double worldCoordinate, double step) {
        return Math.abs(Math.IEEEremainder(worldCoordinate, step)) < 1e-6;
    }
}
