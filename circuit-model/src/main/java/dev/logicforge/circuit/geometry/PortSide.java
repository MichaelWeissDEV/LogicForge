package dev.logicforge.circuit.geometry;

/**
 * The side of an unrotated component body a port sticks out of. The wire router uses it
 * to leave a port in the right direction; the renderer uses it to draw the port stub.
 */
public enum PortSide {

    LEFT(-1, 0),
    RIGHT(1, 0),
    TOP(0, -1),
    BOTTOM(0, 1);

    private final int dx;
    private final int dy;

    PortSide(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    /** Unit vector pointing away from the body, in component-local coordinates. */
    public CircuitPoint outwards() {
        return new CircuitPoint(dx, dy);
    }

    /** The side this one becomes when the component is rotated. */
    public PortSide rotatedBy(Rotation rotation) {
        CircuitPoint rotated = rotation.apply(outwards());
        for (PortSide side : values()) {
            if (side.dx == (int) Math.round(rotated.x()) && side.dy == (int) Math.round(rotated.y())) {
                return side;
            }
        }
        throw new IllegalStateException("Rotation produced a non-axis direction: " + rotated);
    }

    public boolean isHorizontal() {
        return this == LEFT || this == RIGHT;
    }
}
