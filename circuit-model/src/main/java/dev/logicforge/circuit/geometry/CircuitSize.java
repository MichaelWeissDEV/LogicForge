package dev.logicforge.circuit.geometry;

/** The size of a component body in circuit coordinates. */
public record CircuitSize(double width, double height) {

    public CircuitSize {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Size must be positive, was " + width + "x" + height);
        }
    }

    public double halfWidth() {
        return width / 2;
    }

    public double halfHeight() {
        return height / 2;
    }
}
