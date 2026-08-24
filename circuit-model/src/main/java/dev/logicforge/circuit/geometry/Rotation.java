package dev.logicforge.circuit.geometry;

/**
 * The four orientations a component can have. Rotation is applied around the component's
 * own centre, in screen orientation (x to the right, y downwards), so
 * {@link #DEG_90} turns "input on the left" into "input at the top".
 */
public enum Rotation {

    DEG_0(0),
    DEG_90(90),
    DEG_180(180),
    DEG_270(270);

    private final int degrees;

    Rotation(int degrees) {
        this.degrees = degrees;
    }

    public int degrees() {
        return degrees;
    }

    public static Rotation ofDegrees(int degrees) {
        int normalized = Math.floorMod(degrees, 360);
        for (Rotation rotation : values()) {
            if (rotation.degrees == normalized) {
                return rotation;
            }
        }
        throw new IllegalArgumentException("Only multiples of 90 degrees are supported, got " + degrees);
    }

    public Rotation rotatedClockwise() {
        return values()[(ordinal() + 1) % values().length];
    }

    public Rotation rotatedCounterClockwise() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }

    /** Rotates a point given in component-local coordinates around the local origin. */
    public CircuitPoint apply(CircuitPoint local) {
        return switch (this) {
            case DEG_0 -> local;
            case DEG_90 -> new CircuitPoint(negate(local.y()), local.x());
            case DEG_180 -> new CircuitPoint(negate(local.x()), negate(local.y()));
            case DEG_270 -> new CircuitPoint(local.y(), negate(local.x()));
        };
    }

    /** Negation that maps zero onto positive zero, so rotated points compare equal. */
    private static double negate(double value) {
        return value == 0 ? 0 : -value;
    }

    /** The size of a body of {@code size} after rotation. */
    public CircuitSize apply(CircuitSize size) {
        return switch (this) {
            case DEG_0, DEG_180 -> size;
            case DEG_90, DEG_270 -> new CircuitSize(size.height(), size.width());
        };
    }
}
