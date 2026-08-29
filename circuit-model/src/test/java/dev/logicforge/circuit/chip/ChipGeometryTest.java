package dev.logicforge.circuit.chip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.geometry.CircuitBounds;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.PortSide;
import dev.logicforge.circuit.geometry.Rotation;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

class ChipGeometryTest {

    private static PackageDefinition dip(PackageType type) {
        List<PackagePin> pins = new ArrayList<>();
        for (int number = 1; number <= type.pinCount(); number++) {
            pins.add(new PackagePin(number, "P" + number, ElectricalPinType.SIGNAL));
        }
        return new PackageDefinition(type, pins);
    }

    @ParameterizedTest
    @EnumSource(value = PackageType.class, names = {"DIP14", "DIP16", "DIP24", "DIP28"})
    void unrotatedPin1IsTopLeftAndLastPinIsTopRight(PackageType type) {
        int count = type.pinCount();
        CircuitPoint pin1 = ChipGeometry.localPinTip(type, 1);
        CircuitPoint lastPin = ChipGeometry.localPinTip(type, count);
        CircuitPoint firstOppositeSide = ChipGeometry.localPinTip(type, count / 2 + 1);

        assertTrue(pin1.x() < 0, "pin 1 is on the left");
        assertTrue(pin1.y() < 0, "pin 1 is at the top");
        assertTrue(lastPin.x() > 0, "last pin is on the right");
        assertEquals(pin1.y(), lastPin.y(), 1e-9, "last pin is level with pin 1");
        assertTrue(firstOppositeSide.x() > 0, "first pin of the opposite side is on the right");
        assertTrue(firstOppositeSide.y() > 0, "first pin of the opposite side is at the bottom");

        assertEquals(PortSide.LEFT, ChipGeometry.localPinSide(type, 1));
        assertEquals(PortSide.RIGHT, ChipGeometry.localPinSide(type, count));
    }

    @ParameterizedTest
    @EnumSource(value = PackageType.class, names = {"DIP14", "DIP16", "DIP24", "DIP28"})
    void bodyBoundsScalesWithPinCount(PackageType type) {
        ChipInstance instance = ChipInstance.create("PART", CircuitPoint.ORIGIN, "U1");
        CircuitBounds bounds = ChipGeometry.bodyBounds(instance, type);
        assertEquals(ChipGeometry.BODY_WIDTH, bounds.width(), 1e-9);
        assertEquals(ChipGeometry.bodyHeight(type), bounds.height(), 1e-9);
        assertEquals(CircuitPoint.ORIGIN, bounds.center());
    }

    @ParameterizedTest
    @EnumSource(Rotation.class)
    void pin1IdentityIsPreservedAcrossRotation(Rotation rotation) {
        PackageDefinition definition = dip(PackageType.DIP14);
        ChipInstance instance = new ChipInstance(java.util.UUID.randomUUID(), "PART",
                new CircuitPoint(100, 200), rotation, "U1", ChipDisplayMode.PACKAGE);

        PlacedChipPin pin1 = ChipGeometry.pin(instance, definition, 1).orElseThrow();
        assertEquals(1, pin1.number());
        assertEquals("P1", pin1.name());

        // The pin tip always lies exactly PIN_LENGTH beyond the body edge, regardless of rotation.
        double leadLength = pin1.position().distanceTo(pin1.bodyAnchor());
        assertEquals(ChipGeometry.PIN_LENGTH, leadLength, 1e-9);
    }

    @Test
    void rotationMovesPinsButKeepsPhysicalNumberingStable() {
        PackageDefinition definition = dip(PackageType.DIP14);
        CircuitPoint center = new CircuitPoint(500, 500);
        ChipInstance unrotated = new ChipInstance(java.util.UUID.randomUUID(), "PART", center,
                Rotation.DEG_0, "U1", ChipDisplayMode.PACKAGE);
        ChipInstance rotated90 = unrotated.withRotation(Rotation.DEG_90);

        PlacedChipPin pin1Unrotated = ChipGeometry.pin(unrotated, definition, 1).orElseThrow();
        PlacedChipPin pin1Rotated = ChipGeometry.pin(rotated90, definition, 1).orElseThrow();

        // Same physical pin (number/name) survives rotation, only its world position changes.
        assertEquals(pin1Unrotated.number(), pin1Rotated.number());
        assertEquals(pin1Unrotated.name(), pin1Rotated.name());
        assertTrue(!pin1Unrotated.position().equals(pin1Rotated.position()));

        // 90 degree rotation: what was "up" (negative y offset from center) becomes "right".
        CircuitPoint unrotatedOffset = pin1Unrotated.position().minus(center);
        CircuitPoint rotatedOffset = pin1Rotated.position().minus(center);
        assertEquals(unrotatedOffset.y(), -rotatedOffset.x(), 1e-9);
        assertEquals(unrotatedOffset.x(), rotatedOffset.y(), 1e-9);
    }

    @Test
    void allPinsAreDistinctAndWithinOuterBounds() {
        PackageDefinition definition = dip(PackageType.DIP28);
        ChipInstance instance = ChipInstance.create("PART", new CircuitPoint(10, 10), "U1")
                .withRotation(Rotation.DEG_270);
        List<PlacedChipPin> pins = ChipGeometry.pins(instance, definition);
        assertEquals(28, pins.size());
        assertEquals(28, pins.stream().map(PlacedChipPin::number).distinct().count());

        CircuitBounds outer = ChipGeometry.outerBounds(instance, definition);
        for (PlacedChipPin pin : pins) {
            assertTrue(outer.contains(pin.position()),
                    "pin " + pin.number() + " at " + pin.position() + " outside " + outer);
        }
    }

    @Test
    void unknownPinNumberIsAbsent() {
        PackageDefinition definition = dip(PackageType.DIP14);
        ChipInstance instance = ChipInstance.create("PART", CircuitPoint.ORIGIN, "U1");
        assertTrue(ChipGeometry.pin(instance, definition, 15).isEmpty());
        assertTrue(ChipGeometry.pin(instance, definition, 0).isEmpty());
    }
}
