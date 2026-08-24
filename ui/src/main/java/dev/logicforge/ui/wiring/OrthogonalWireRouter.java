package dev.logicforge.ui.wiring;

import dev.logicforge.circuit.document.PlacedPort;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.PortSide;
import dev.logicforge.ui.viewport.Grid;
import java.util.ArrayList;
import java.util.List;

/**
 * A Manhattan router: wires leave a port straight out of its own side, then reach the
 * target with as few corners as possible.
 *
 * <p>The rules are simple on purpose. A wire is a horizontal and a vertical run, or two
 * horizontal runs joined by a vertical one — never a diagonal, never a random staircase.
 * Because the route is computed from the ports' current positions, wires follow
 * automatically when a component is moved or rotated.
 */
public final class OrthogonalWireRouter implements WireRouter {

    /** How far a wire runs straight out of a port before it may turn. */
    private static final double LEAD_OUT = Grid.SPACING * 2;

    @Override
    public WireRoute route(PlacedPort from, PlacedPort to, List<CircuitPoint> waypoints) {
        List<CircuitPoint> points = new ArrayList<>();
        points.add(from.position());
        CircuitPoint start = from.stubEnd(LEAD_OUT);
        points.add(start);

        CircuitPoint end = to.stubEnd(LEAD_OUT);
        if (waypoints.isEmpty()) {
            points.addAll(connect(start, from.side(), end, to.side()));
        } else {
            CircuitPoint previous = start;
            for (CircuitPoint waypoint : waypoints) {
                points.addAll(elbow(previous, waypoint, from.side().isHorizontal()));
                points.add(waypoint);
                previous = waypoint;
            }
            points.addAll(elbow(previous, end, true));
        }
        points.add(end);
        points.add(to.position());
        return new WireRoute(simplify(points));
    }

    @Override
    public WireRoute routeToPoint(PlacedPort from, CircuitPoint target) {
        List<CircuitPoint> points = new ArrayList<>();
        points.add(from.position());
        CircuitPoint start = from.stubEnd(LEAD_OUT);
        points.add(start);
        points.addAll(elbow(start, target, from.side().isHorizontal()));
        points.add(target);
        return new WireRoute(simplify(points));
    }

    /** The corners between the two lead-out points, excluding the points themselves. */
    private static List<CircuitPoint> connect(CircuitPoint start, PortSide startSide,
                                              CircuitPoint end, PortSide endSide) {
        boolean startHorizontal = startSide.isHorizontal();
        boolean endHorizontal = endSide.isHorizontal();

        if (startHorizontal && endHorizontal) {
            if (leavesTowards(startSide, end.x() - start.x()) && leavesTowards(endSide, start.x() - end.x())) {
                // Facing each other: one vertical run halfway between them.
                double middle = Grid.snap((start.x() + end.x()) / 2);
                return List.of(new CircuitPoint(middle, start.y()), new CircuitPoint(middle, end.y()));
            }
            // Pointing the same way or away: go around via a horizontal run in between.
            double middle = Grid.snap((start.y() + end.y()) / 2);
            return List.of(new CircuitPoint(start.x(), middle), new CircuitPoint(end.x(), middle));
        }
        if (!startHorizontal && !endHorizontal) {
            if (leavesTowards(startSide, end.y() - start.y()) && leavesTowards(endSide, start.y() - end.y())) {
                double middle = Grid.snap((start.y() + end.y()) / 2);
                return List.of(new CircuitPoint(start.x(), middle), new CircuitPoint(end.x(), middle));
            }
            double middle = Grid.snap((start.x() + end.x()) / 2);
            return List.of(new CircuitPoint(middle, start.y()), new CircuitPoint(middle, end.y()));
        }
        // One horizontal, one vertical: a single corner is enough.
        return startHorizontal
                ? List.of(new CircuitPoint(end.x(), start.y()))
                : List.of(new CircuitPoint(start.x(), end.y()));
    }

    /** {@code true} if a wire leaving this side heads in the given direction. */
    private static boolean leavesTowards(PortSide side, double delta) {
        CircuitPoint outwards = side.outwards();
        double direction = side.isHorizontal() ? outwards.x() : outwards.y();
        return direction * delta > 0;
    }

    private static List<CircuitPoint> elbow(CircuitPoint from, CircuitPoint to, boolean horizontalFirst) {
        if (from.x() == to.x() || from.y() == to.y()) {
            return List.of();
        }
        return List.of(horizontalFirst
                ? new CircuitPoint(to.x(), from.y())
                : new CircuitPoint(from.x(), to.y()));
    }

    /** Drops duplicate and collinear points so the route has as few corners as possible. */
    private static List<CircuitPoint> simplify(List<CircuitPoint> points) {
        List<CircuitPoint> result = new ArrayList<>(points.size());
        for (CircuitPoint point : points) {
            if (!result.isEmpty() && result.get(result.size() - 1).equals(point)) {
                continue;
            }
            result.add(point);
        }
        for (int i = 1; i < result.size() - 1; ) {
            CircuitPoint previous = result.get(i - 1);
            CircuitPoint current = result.get(i);
            CircuitPoint next = result.get(i + 1);
            boolean collinear = (previous.x() == current.x() && current.x() == next.x())
                    || (previous.y() == current.y() && current.y() == next.y());
            if (collinear) {
                result.remove(i);
            } else {
                i++;
            }
        }
        return result;
    }
}
