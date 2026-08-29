package dev.logicforge.circuit.chip;

import dev.logicforge.circuit.geometry.CircuitBounds;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.CircuitSize;
import dev.logicforge.circuit.geometry.PortSide;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Headless geometry for a top-view DIP package: body bounds, pin anchors and pin stubs.
 *
 * <p>This is the single place where physical-chip geometry is computed. The renderer and
 * the hit tester both call it, the same way {@code ComponentGeometry} is the single source
 * of geometry for ordinary components, so a rotated chip can never be drawn at one pin
 * position and hit-tested at another.
 *
 * <p>Only through-hole dual-inline packages are modelled: pins run down the left side and
 * up the right side, top view, pin 1 at the top left next to the notch — the standard DIP
 * numbering convention.
 */
public final class ChipGeometry {

    /** Vertical distance between adjacent pins on one side. */
    public static final double PIN_PITCH = 28;
    /** Width of the package body, unrotated. */
    public static final double BODY_WIDTH = 150;
    /** Length of a pin lead from the body edge to its connectable tip. */
    public static final double PIN_LENGTH = 24;
    /** Fixed margin added above/below the outermost pin row. */
    private static final double BODY_MARGIN = 30;

    private ChipGeometry() {
    }

    /** Unrotated body height for a package with this many pins. */
    public static double bodyHeight(PackageType type) {
        int side = type.pinCount() / 2;
        return (side - 1) * PIN_PITCH + BODY_MARGIN;
    }

    /** Unrotated body size. */
    public static CircuitSize bodySize(PackageType type) {
        return new CircuitSize(BODY_WIDTH, bodyHeight(type));
    }

    /** The package body rectangle in world coordinates, rotation included. */
    public static CircuitBounds bodyBounds(ChipInstance instance, PackageType type) {
        CircuitSize size = instance.rotation().apply(bodySize(type));
        return CircuitBounds.around(instance.position(), size);
    }

    /**
     * Local (component-local, unrotated) position of a pin's connectable tip, origin at the
     * body centre. Pin 1 is top-left; numbering descends the left side then ascends the
     * right side, matching standard DIP top-view numbering.
     */
    public static CircuitPoint localPinTip(PackageType type, int pinNumber) {
        return localPinPoint(type, pinNumber, BODY_WIDTH / 2 + PIN_LENGTH);
    }

    /** Local position where the pin's lead meets the body edge (no lead length). */
    public static CircuitPoint localPinBodyAnchor(PackageType type, int pinNumber) {
        return localPinPoint(type, pinNumber, BODY_WIDTH / 2);
    }

    /** LEFT for the first half of the pins, RIGHT for the second half, unrotated. */
    public static PortSide localPinSide(PackageType type, int pinNumber) {
        requireValidPin(type, pinNumber);
        return pinNumber <= type.pinCount() / 2 ? PortSide.LEFT : PortSide.RIGHT;
    }

    private static CircuitPoint localPinPoint(PackageType type, int pinNumber, double xMagnitude) {
        requireValidPin(type, pinNumber);
        int side = type.pinCount() / 2;
        int row = pinNumber <= side ? pinNumber - 1 : type.pinCount() - pinNumber;
        double y = (row - (side - 1) / 2.0) * PIN_PITCH;
        double x = pinNumber <= side ? -xMagnitude : xMagnitude;
        return new CircuitPoint(x, y);
    }

    private static void requireValidPin(PackageType type, int pinNumber) {
        if (pinNumber < 1 || pinNumber > type.pinCount()) {
            throw new IllegalArgumentException("Pin is outside " + type + ": " + pinNumber);
        }
    }

    /** The world position of one pin's connectable tip for a placed chip instance. */
    public static CircuitPoint pinTip(ChipInstance instance, PackageType type, int pinNumber) {
        return instance.position().plus(instance.rotation().apply(localPinTip(type, pinNumber)));
    }

    /** One physical pin resolved into world coordinates for this placed chip instance. */
    public static Optional<PlacedChipPin> pin(ChipInstance instance, PackageDefinition packageDefinition,
                                              int pinNumber) {
        Optional<PackagePin> pin = packageDefinition.pin(pinNumber);
        if (pin.isEmpty()) {
            return Optional.empty();
        }
        PackageType type = packageDefinition.type();
        CircuitPoint tip = instance.position().plus(instance.rotation().apply(localPinTip(type, pinNumber)));
        CircuitPoint bodyAnchor = instance.position()
                .plus(instance.rotation().apply(localPinBodyAnchor(type, pinNumber)));
        PortSide side = localPinSide(type, pinNumber).rotatedBy(instance.rotation());
        return Optional.of(new PlacedChipPin(pin.get(), tip, bodyAnchor, side));
    }

    /** Every physical pin of this placed chip instance, resolved into world coordinates. */
    public static List<PlacedChipPin> pins(ChipInstance instance, PackageDefinition packageDefinition) {
        List<PlacedChipPin> placed = new ArrayList<>(packageDefinition.pins().size());
        for (PackagePin pin : packageDefinition.pins()) {
            placed.add(pin(instance, packageDefinition, pin.number()).orElseThrow());
        }
        return placed;
    }

    /**
     * Bounds covering the body plus every pin tip — used for selection rectangles and for
     * keeping wires clear of the package.
     */
    public static CircuitBounds outerBounds(ChipInstance instance, PackageDefinition packageDefinition) {
        CircuitBounds bounds = bodyBounds(instance, packageDefinition.type());
        for (PlacedChipPin pin : pins(instance, packageDefinition)) {
            bounds = bounds.union(new CircuitBounds(pin.position().x(), pin.position().y(), 0, 0));
        }
        return bounds;
    }
}
