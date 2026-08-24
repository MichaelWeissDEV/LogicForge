package dev.logicforge.circuit.geometry;

/** An axis-aligned rectangle in circuit coordinates. */
public record CircuitBounds(double x, double y, double width, double height) {

    public static CircuitBounds around(CircuitPoint center, CircuitSize size) {
        return new CircuitBounds(center.x() - size.halfWidth(), center.y() - size.halfHeight(),
                size.width(), size.height());
    }

    /** The smallest rectangle containing both corners, in any order. */
    public static CircuitBounds between(CircuitPoint a, CircuitPoint b) {
        double minX = Math.min(a.x(), b.x());
        double minY = Math.min(a.y(), b.y());
        return new CircuitBounds(minX, minY, Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y()));
    }

    public double maxX() {
        return x + width;
    }

    public double maxY() {
        return y + height;
    }

    public CircuitPoint center() {
        return new CircuitPoint(x + width / 2, y + height / 2);
    }

    public boolean contains(CircuitPoint point) {
        return point.x() >= x && point.x() <= maxX() && point.y() >= y && point.y() <= maxY();
    }

    public boolean contains(CircuitBounds other) {
        return other.x >= x && other.y >= y && other.maxX() <= maxX() && other.maxY() <= maxY();
    }

    public boolean intersects(CircuitBounds other) {
        return other.x <= maxX() && other.maxX() >= x && other.y <= maxY() && other.maxY() >= y;
    }

    /** This rectangle enlarged by {@code margin} on every side. */
    public CircuitBounds grownBy(double margin) {
        return new CircuitBounds(x - margin, y - margin, width + 2 * margin, height + 2 * margin);
    }

    public CircuitBounds union(CircuitBounds other) {
        double minX = Math.min(x, other.x);
        double minY = Math.min(y, other.y);
        return new CircuitBounds(minX, minY, Math.max(maxX(), other.maxX()) - minX,
                Math.max(maxY(), other.maxY()) - minY);
    }
}
