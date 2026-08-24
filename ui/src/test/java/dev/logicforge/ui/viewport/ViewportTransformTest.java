package dev.logicforge.ui.viewport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.geometry.CircuitBounds;
import dev.logicforge.circuit.geometry.CircuitPoint;
import org.junit.jupiter.api.Test;

class ViewportTransformTest {

    private static final double EPSILON = 1e-9;

    private final ViewportTransform viewport = new ViewportTransform();

    @Test
    void screenAndWorldRoundTripAtAnyZoomAndPan() {
        viewport.zoomBy(2.5, 300, 200);
        viewport.panBy(-137, 42);

        CircuitPoint world = new CircuitPoint(123.5, -87.25);
        ScreenPoint screen = viewport.worldToScreen(world);
        CircuitPoint back = viewport.screenToWorld(screen);

        assertEquals(world.x(), back.x(), EPSILON);
        assertEquals(world.y(), back.y(), EPSILON);
    }

    @Test
    void droppingAComponentLandsUnderTheCursorAfterZooming() {
        // What drag & drop does: take a screen position, ask for the circuit position.
        viewport.zoomBy(3.0, 400, 300);
        viewport.panBy(60, -25);

        CircuitPoint dropped = viewport.screenToWorld(512, 377);
        ScreenPoint drawnAgain = viewport.worldToScreen(dropped);

        assertEquals(512, drawnAgain.x(), EPSILON);
        assertEquals(377, drawnAgain.y(), EPSILON);
    }

    @Test
    void zoomingKeepsThePointUnderTheCursorInPlace() {
        CircuitPoint underCursor = viewport.screenToWorld(640, 360);

        viewport.zoomBy(1.2, 640, 360);
        viewport.zoomBy(1.2, 640, 360);
        viewport.zoomBy(0.5, 640, 360);

        CircuitPoint stillUnderCursor = viewport.screenToWorld(640, 360);
        assertEquals(underCursor.x(), stillUnderCursor.x(), 1e-6);
        assertEquals(underCursor.y(), stillUnderCursor.y(), 1e-6);
    }

    @Test
    void zoomingAtTheCursorMovesEverythingElse() {
        CircuitPoint elsewhere = viewport.screenToWorld(0, 0);
        viewport.zoomBy(2.0, 640, 360);
        assertTrue(Math.abs(viewport.screenToWorld(0, 0).x() - elsewhere.x()) > 1,
                "zooming around the cursor is not zooming around the origin");
    }

    @Test
    void zoomIsClamped() {
        for (int i = 0; i < 100; i++) {
            viewport.zoomBy(2, 0, 0);
        }
        assertEquals(ViewportTransform.MAX_SCALE, viewport.scale(), EPSILON);

        for (int i = 0; i < 200; i++) {
            viewport.zoomBy(0.5, 0, 0);
        }
        assertEquals(ViewportTransform.MIN_SCALE, viewport.scale(), EPSILON);
    }

    @Test
    void panningMovesTheViewByExactlyThatManyPixels() {
        ScreenPoint before = viewport.worldToScreen(new CircuitPoint(10, 10));
        viewport.panBy(25, -40);
        ScreenPoint after = viewport.worldToScreen(new CircuitPoint(10, 10));

        assertEquals(before.x() + 25, after.x(), EPSILON);
        assertEquals(before.y() - 40, after.y(), EPSILON);
    }

    @Test
    void lengthsScaleButAreNotTranslated() {
        viewport.zoomBy(2, 100, 100);
        viewport.panBy(500, 500);

        assertEquals(32, viewport.worldToScreenLength(16), EPSILON);
        assertEquals(16, viewport.screenToWorldLength(32), EPSILON);
    }

    @Test
    void centeringPutsAPointInTheMiddleOfTheViewport() {
        viewport.centerOn(new CircuitPoint(200, 100), 800, 600);
        ScreenPoint screen = viewport.worldToScreen(new CircuitPoint(200, 100));

        assertEquals(400, screen.x(), EPSILON);
        assertEquals(300, screen.y(), EPSILON);
    }

    @Test
    void fittingContentMakesItVisible() {
        CircuitBounds content = new CircuitBounds(0, 0, 1600, 1200);
        viewport.fit(content, 800, 600);

        CircuitBounds visible = viewport.visibleWorldBounds(800, 600);
        assertTrue(visible.contains(content.center()));
        assertTrue(viewport.scale() < 1, "large content is zoomed out to fit");
    }

    @Test
    void visibleBoundsFollowThePan() {
        CircuitBounds before = viewport.visibleWorldBounds(800, 600);
        viewport.panBy(-800, 0);
        CircuitBounds after = viewport.visibleWorldBounds(800, 600);

        assertEquals(before.x() + 800, after.x(), EPSILON);
        assertEquals(800, after.width(), EPSILON);
    }

    @Test
    void gridSnappingIsIndependentOfZoom() {
        assertEquals(new CircuitPoint(16, 8), Grid.snap(new CircuitPoint(18, 5)));
        assertEquals(new CircuitPoint(0, 0), Grid.snap(new CircuitPoint(3, -3)));
        assertEquals(8, Grid.firstLineAtOrAfter(1.5), EPSILON);
        assertEquals(-8, Grid.firstLineAtOrAfter(-8), EPSILON);
        assertTrue(Grid.isOnLine(32, Grid.SPACING * Grid.MAJOR_EVERY));
        assertTrue(!Grid.isOnLine(24, Grid.SPACING * Grid.MAJOR_EVERY));
    }
}
