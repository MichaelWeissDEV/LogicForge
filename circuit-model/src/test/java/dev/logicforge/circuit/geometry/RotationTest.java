package dev.logicforge.circuit.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RotationTest {

    @Test
    void quarterTurnsMoveAPointClockwiseOnScreen() {
        CircuitPoint right = new CircuitPoint(10, 0);
        assertEquals(new CircuitPoint(10, 0), Rotation.DEG_0.apply(right));
        assertEquals(new CircuitPoint(0, 10), Rotation.DEG_90.apply(right));
        assertEquals(new CircuitPoint(-10, 0), Rotation.DEG_180.apply(right));
        assertEquals(new CircuitPoint(0, -10), Rotation.DEG_270.apply(right));
    }

    @ParameterizedTest
    @EnumSource(Rotation.class)
    void fourQuarterTurnsAreIdentity(Rotation rotation) {
        CircuitPoint point = new CircuitPoint(3, -7);
        CircuitPoint result = point;
        for (int i = 0; i < 4; i++) {
            result = rotation.apply(result);
        }
        assertEquals(point, result);
    }

    @Test
    void inputSideBecomesTopAfterAQuarterTurn() {
        assertEquals(PortSide.TOP, PortSide.LEFT.rotatedBy(Rotation.DEG_90));
        assertEquals(PortSide.BOTTOM, PortSide.RIGHT.rotatedBy(Rotation.DEG_90));
        assertEquals(PortSide.RIGHT, PortSide.LEFT.rotatedBy(Rotation.DEG_180));
        assertEquals(PortSide.BOTTOM, PortSide.LEFT.rotatedBy(Rotation.DEG_270));
        assertEquals(PortSide.LEFT, PortSide.LEFT.rotatedBy(Rotation.DEG_0));
    }

    @Test
    void bodySizeSwapsOnOddQuarterTurns() {
        CircuitSize size = new CircuitSize(48, 64);
        assertEquals(size, Rotation.DEG_0.apply(size));
        assertEquals(new CircuitSize(64, 48), Rotation.DEG_90.apply(size));
        assertEquals(size, Rotation.DEG_180.apply(size));
    }

    @Test
    void steppingThroughOrientations() {
        assertEquals(Rotation.DEG_90, Rotation.DEG_0.rotatedClockwise());
        assertEquals(Rotation.DEG_0, Rotation.DEG_270.rotatedClockwise());
        assertEquals(Rotation.DEG_270, Rotation.DEG_0.rotatedCounterClockwise());
        assertEquals(Rotation.DEG_180, Rotation.ofDegrees(-180));
        assertThrows(IllegalArgumentException.class, () -> Rotation.ofDegrees(45));
    }
}
