package dev.logicforge.ui.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.ComponentGeometry;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.PlacedPort;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.Rotation;
import dev.logicforge.library.ComponentRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrthogonalWireRouterTest {

    private final ComponentRegistry registry = ComponentRegistry.standard();
    private final OrthogonalWireRouter router = new OrthogonalWireRouter();

    @Test
    void everySegmentIsHorizontalOrVertical() {
        WireRoute route = route(place("source.toggle", 0, 0), "OUT",
                place("logic.and", 200, 64), "IN1");

        assertOrthogonal(route);
    }

    @Test
    void aStraightConnectionStaysStraight() {
        ComponentInstance source = place("source.toggle", 0, 0);
        ComponentInstance sink = place("output.led", 200, 0);

        WireRoute route = route(source, "OUT", sink, "IN");

        assertEquals(2, route.points().size(), "no unnecessary corners");
        assertTrue(route.corners().isEmpty());
        assertOrthogonal(route);
    }

    @Test
    void wiresStartAndEndExactlyAtTheirPorts() {
        ComponentInstance source = place("source.toggle", 0, 0);
        ComponentInstance sink = place("logic.and", 300, 120);

        WireRoute route = route(source, "OUT", sink, "IN0");

        assertEquals(portOf(source, "OUT").position(), route.points().get(0));
        assertEquals(portOf(sink, "IN0").position(), route.points().get(route.points().size() - 1));
    }

    @Test
    void backwardsConnectionsRouteAroundInsteadOfThroughTheGate() {
        // The target sits to the left of the source, so the wire has to double back.
        WireRoute route = route(place("source.toggle", 400, 0), "OUT",
                place("logic.and", 100, 200), "IN0");

        assertOrthogonal(route);
        assertTrue(route.corners().size() >= 2, "doubling back needs at least two corners");
    }

    @Test
    void rotatingAComponentMovesTheWireWithIt() {
        ComponentInstance source = place("source.toggle", 0, 0);
        ComponentInstance sink = place("logic.and", 200, 0);

        WireRoute before = route(source, "OUT", sink, "IN0");
        ComponentInstance rotated = sink.withRotation(Rotation.DEG_90);
        WireRoute after = route(source, "OUT", rotated, "IN0");

        assertEquals(portOf(rotated, "IN0").position(), after.points().get(after.points().size() - 1));
        assertTrue(!before.points().equals(after.points()), "the route follows the rotated port");
        assertOrthogonal(after);
    }

    @Test
    void manualWaypointsAreFollowed() {
        ComponentInstance source = place("source.toggle", 0, 0);
        ComponentInstance sink = place("output.led", 300, 0);

        WireRoute route = router.route(portOf(source, "OUT"), portOf(sink, "IN"),
                List.of(new CircuitPoint(150, 96)));

        assertTrue(route.points().contains(new CircuitPoint(150, 96)));
        assertOrthogonal(route);
    }

    @Test
    void thePreviewWireFollowsTheCursor() {
        WireRoute preview = router.routeToPoint(portOf(place("source.toggle", 0, 0), "OUT"),
                new CircuitPoint(160, 80));

        assertEquals(new CircuitPoint(160, 80), preview.points().get(preview.points().size() - 1));
        assertOrthogonal(preview);
    }

    @Test
    void hitTestingMeasuresDistanceToTheWire() {
        WireRoute route = route(place("source.toggle", 0, 0), "OUT", place("output.led", 200, 0), "IN");

        assertTrue(route.distanceTo(new CircuitPoint(100, 0)) < 0.001, "on the wire");
        assertEquals(10, route.distanceTo(new CircuitPoint(100, 10)), 0.001);
        assertTrue(route.distanceTo(new CircuitPoint(100, 400)) > 100);
    }

    private WireRoute route(ComponentInstance from, String fromPort, ComponentInstance to, String toPort) {
        return router.route(portOf(from, fromPort), portOf(to, toPort), List.of());
    }

    private static void assertOrthogonal(WireRoute route) {
        List<CircuitPoint> points = route.points();
        for (int i = 0; i < points.size() - 1; i++) {
            CircuitPoint a = points.get(i);
            CircuitPoint b = points.get(i + 1);
            assertTrue(a.x() == b.x() || a.y() == b.y(),
                    "segment " + a + " -> " + b + " is diagonal");
        }
    }

    private ComponentInstance place(String definitionId, double x, double y) {
        return ComponentInstance.create(definitionId, new CircuitPoint(x, y),
                registry.require(definitionId).definition().defaultParameters());
    }

    private PlacedPort portOf(ComponentInstance instance, String portName) {
        ComponentDefinition definition = registry.require(instance.definitionId()).definition();
        return ComponentGeometry.port(instance, definition, portName).orElseThrow();
    }

    @Test
    void gatesWithMoreInputsStillRouteCleanly() {
        ComponentInstance gate = ComponentInstance.create("logic.and", new CircuitPoint(300, 0),
                ParameterValues.empty().with(dev.logicforge.library.LibraryParameters.INPUT_COUNT, 8));
        for (int i = 0; i < 8; i++) {
            assertOrthogonal(route(place("source.toggle", 0, i * 32), "OUT", gate, "IN" + i));
        }
    }
}
