package dev.logicforge.ui.viewport;

/**
 * A point in screen (pixel) coordinates. A separate type from
 * {@link dev.logicforge.circuit.geometry.CircuitPoint} on purpose: mixing the two up is
 * the classic source of "everything is offset after zooming".
 */
public record ScreenPoint(double x, double y) {

    public ScreenPoint plus(double dx, double dy) {
        return new ScreenPoint(x + dx, y + dy);
    }
}
