package dev.logicforge.circuit.geometry;

/**
 * A point in circuit (world) coordinates. Deliberately a plain record so that the model
 * layer stays free of any UI toolkit types.
 */
public record CircuitPoint(double x, double y) {

    public static final CircuitPoint ORIGIN = new CircuitPoint(0, 0);

    public CircuitPoint plus(CircuitPoint other) {
        return new CircuitPoint(x + other.x, y + other.y);
    }

    public CircuitPoint plus(double dx, double dy) {
        return new CircuitPoint(x + dx, y + dy);
    }

    public CircuitPoint minus(CircuitPoint other) {
        return new CircuitPoint(x - other.x, y - other.y);
    }

    /** Rounds both coordinates to the nearest multiple of {@code spacing}. */
    public CircuitPoint snappedTo(double spacing) {
        return new CircuitPoint(Math.round(x / spacing) * spacing, Math.round(y / spacing) * spacing);
    }

    public double distanceTo(CircuitPoint other) {
        return Math.hypot(x - other.x, y - other.y);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
