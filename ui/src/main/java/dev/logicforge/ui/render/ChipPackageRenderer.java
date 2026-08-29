package dev.logicforge.ui.render;

import dev.logicforge.circuit.chip.ChipDefinition;
import dev.logicforge.circuit.chip.ChipGeometry;
import dev.logicforge.circuit.chip.PackagePin;
import dev.logicforge.circuit.chip.PackageType;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.Rotation;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * Top-view DIP renderer with physical numbering, notch and pin-one marker.
 *
 * <p>All body and pin geometry comes from {@link ChipGeometry}, the same headless source
 * the hit tester and wire router use, so a pin is never drawn in one place and clickable
 * in another.
 */
public final class ChipPackageRenderer {

    public static final double PIN_PITCH = ChipGeometry.PIN_PITCH;
    public static final double BODY_WIDTH = ChipGeometry.BODY_WIDTH;
    public static final double PIN_LENGTH = ChipGeometry.PIN_LENGTH;

    /** Draws a chip with every pin the same neutral color — for tests and simple previews. */
    public void draw(GraphicsContext graphics, ChipDefinition chip, String referenceDesignator,
                     CircuitPoint center, Rotation rotation) {
        draw(graphics, chip, referenceDesignator, center, rotation, pin -> Theme.WIRE_UNPOWERED, -1);
    }

    /**
     * @param pinColor        live signal color for a SIGNAL pin, or a muted color for
     *                        POWER/GROUND/NC — keyed by physical pin number
     * @param hoveredPinNumber the physical pin number to highlight, or a value below 1 for none
     */
    public void draw(GraphicsContext graphics, ChipDefinition chip, String referenceDesignator,
                     CircuitPoint center, Rotation rotation, java.util.function.IntFunction<Color> pinColor,
                     int hoveredPinNumber) {
        PackageType type = chip.packageDefinition().type();
        double bodyHeight = ChipGeometry.bodyHeight(type);
        graphics.save();
        graphics.translate(center.x(), center.y());
        graphics.rotate(rotation.degrees());
        graphics.setFill(Theme.CHIP_BODY_FILL);
        graphics.setStroke(Theme.CHIP_BODY_STROKE);
        graphics.setLineWidth(2);
        graphics.fillRoundRect(-BODY_WIDTH / 2, -bodyHeight / 2,
                BODY_WIDTH, bodyHeight, 10, 10);
        graphics.strokeRoundRect(-BODY_WIDTH / 2, -bodyHeight / 2,
                BODY_WIDTH, bodyHeight, 10, 10);
        graphics.strokeArc(-14, -bodyHeight / 2 - 7, 28, 14, 180, 180,
                javafx.scene.shape.ArcType.OPEN);
        graphics.setFill(Theme.CHIP_MARKER);
        graphics.fillOval(-BODY_WIDTH / 2 + 10, -bodyHeight / 2 + 10, 7, 7);
        graphics.setTextAlign(TextAlignment.CENTER);
        graphics.setFont(Font.font(13));
        graphics.fillText(referenceDesignator + "  " + chip.metadata().partNumber(), 0, -6);
        graphics.setFont(Font.font(10));
        graphics.fillText("implicitly powered digital model", 0, 12);

        for (PackagePin pin : chip.packageDefinition().pins()) {
            CircuitPoint bodyAnchor = ChipGeometry.localPinBodyAnchor(type, pin.number());
            CircuitPoint tip = ChipGeometry.localPinTip(type, pin.number());
            boolean hovered = pin.number() == hoveredPinNumber;
            Color color = pinColor.apply(pin.number());
            graphics.setLineWidth(hovered ? Theme.WIRE_STROKE + 0.8 : Theme.WIRE_STROKE);
            graphics.setStroke(color);
            graphics.strokeLine(bodyAnchor.x(), bodyAnchor.y(), tip.x(), tip.y());
            double radius = hovered ? Theme.PORT_RADIUS * 1.8 : Theme.PORT_RADIUS;
            graphics.setFill(hovered ? Theme.PORT_HIGHLIGHT : color);
            graphics.fillOval(tip.x() - radius, tip.y() - radius, radius * 2, radius * 2);
            graphics.setFill(Theme.TEXT_MUTED);
            graphics.setTextAlign(tip.x() < 0 ? TextAlignment.RIGHT : TextAlignment.LEFT);
            double textX = tip.x() < 0 ? bodyAnchor.x() - 5 : bodyAnchor.x() + 5;
            graphics.fillText(pin.number() + " " + pin.name(), textX, tip.y() + 4);
        }
        graphics.setLineWidth(Theme.WIRE_STROKE);
        graphics.restore();
    }

    /** Pin coordinates in unrotated top view; right-side numbers descend top-to-bottom. */
    public static PinPosition pinPosition(PackageType type, int pinNumber) {
        CircuitPoint tip = ChipGeometry.localPinTip(type, pinNumber);
        return new PinPosition(tip.x(), tip.y());
    }

    public record PinPosition(double x, double y) {
    }
}
