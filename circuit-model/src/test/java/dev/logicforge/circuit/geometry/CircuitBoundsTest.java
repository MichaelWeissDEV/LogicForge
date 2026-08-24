package dev.logicforge.circuit.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CircuitBoundsTest {

    @Test
    void boundsAroundACentre() {
        CircuitBounds bounds = CircuitBounds.around(new CircuitPoint(100, 100), new CircuitSize(40, 20));
        assertEquals(80, bounds.x());
        assertEquals(90, bounds.y());
        assertEquals(new CircuitPoint(100, 100), bounds.center());
        assertTrue(bounds.contains(new CircuitPoint(119, 99)));
        assertFalse(bounds.contains(new CircuitPoint(121, 100)));
    }

    @Test
    void rectangleSelectionUsesDragCornersInAnyOrder() {
        CircuitBounds dragged = CircuitBounds.between(new CircuitPoint(50, 60), new CircuitPoint(10, 20));
        assertEquals(new CircuitBounds(10, 20, 40, 40), dragged);
        assertTrue(dragged.contains(CircuitBounds.around(new CircuitPoint(30, 40), new CircuitSize(10, 10))));
        assertFalse(dragged.contains(CircuitBounds.around(new CircuitPoint(30, 40), new CircuitSize(100, 10))));
    }

    @Test
    void intersectionAndGrowth() {
        CircuitBounds a = new CircuitBounds(0, 0, 10, 10);
        assertTrue(a.intersects(new CircuitBounds(5, 5, 10, 10)));
        assertFalse(a.intersects(new CircuitBounds(11, 0, 5, 5)));
        assertTrue(a.grownBy(2).contains(new CircuitPoint(-1, -1)));
        assertEquals(new CircuitBounds(0, 0, 20, 10), a.union(new CircuitBounds(10, 0, 10, 10)));
    }

    @Test
    void snappingRoundsToTheNearestGridPoint() {
        assertEquals(new CircuitPoint(16, 8), new CircuitPoint(19, 5).snappedTo(8));
        assertEquals(new CircuitPoint(-8, 0), new CircuitPoint(-6, 3).snappedTo(8));
    }
}
