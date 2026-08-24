package dev.logicforge.ui.wiring;

import dev.logicforge.circuit.geometry.CircuitPoint;
import java.util.List;

/** A wire's path: a polyline of axis-aligned segments in circuit coordinates. */
public record WireRoute(List<CircuitPoint> points) {

    public WireRoute {
        points = List.copyOf(points);
    }

    /** The shortest distance from {@code point} to this wire, for hit testing. */
    public double distanceTo(CircuitPoint point) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < points.size() - 1; i++) {
            best = Math.min(best, distanceToSegment(point, points.get(i), points.get(i + 1)));
        }
        return best;
    }

    private static double distanceToSegment(CircuitPoint point, CircuitPoint from, CircuitPoint to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0) {
            return point.distanceTo(from);
        }
        double t = Math.clamp(((point.x() - from.x()) * dx + (point.y() - from.y()) * dy) / lengthSquared,
                0.0, 1.0);
        return point.distanceTo(new CircuitPoint(from.x() + t * dx, from.y() + t * dy));
    }

    /** Every corner of the route, i.e. the points where it changes direction. */
    public List<CircuitPoint> corners() {
        return points.size() <= 2 ? List.of() : points.subList(1, points.size() - 1);
    }
}
