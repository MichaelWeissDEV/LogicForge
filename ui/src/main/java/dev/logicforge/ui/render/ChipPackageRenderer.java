package dev.logicforge.ui.render;

import dev.logicforge.circuit.chip.ChipDefinition;
import dev.logicforge.circuit.chip.PackagePin;
import dev.logicforge.circuit.chip.PackageType;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.Rotation;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/** Top-view DIP renderer with physical numbering, notch and pin-one marker. */
public final class ChipPackageRenderer {

    public static final double PIN_PITCH = 28;
    public static final double BODY_WIDTH = 150;
    public static final double PIN_LENGTH = 24;

    public void draw(GraphicsContext graphics, ChipDefinition chip, String referenceDesignator,
                     CircuitPoint center, Rotation rotation) {
        int count = chip.packageDefinition().type().pinCount();
        int side = count / 2;
        double bodyHeight = (side - 1) * PIN_PITCH + 30;
        graphics.save();
        graphics.translate(center.x(), center.y());
        graphics.rotate(rotation.degrees());
        graphics.setFill(Color.web("#20252B"));
        graphics.setStroke(Color.web("#CBD5E1"));
        graphics.setLineWidth(2);
        graphics.fillRoundRect(-BODY_WIDTH / 2, -bodyHeight / 2,
                BODY_WIDTH, bodyHeight, 10, 10);
        graphics.strokeRoundRect(-BODY_WIDTH / 2, -bodyHeight / 2,
                BODY_WIDTH, bodyHeight, 10, 10);
        graphics.strokeArc(-14, -bodyHeight / 2 - 7, 28, 14, 180, 180,
                javafx.scene.shape.ArcType.OPEN);
        graphics.setFill(Color.web("#CBD5E1"));
        graphics.fillOval(-BODY_WIDTH / 2 + 10, -bodyHeight / 2 + 10, 7, 7);
        graphics.setTextAlign(TextAlignment.CENTER);
        graphics.setFont(Font.font(13));
        graphics.fillText(referenceDesignator + "  " + chip.metadata().partNumber(), 0, -6);
        graphics.setFont(Font.font(10));
        graphics.fillText("implicitly powered digital model", 0, 12);

        for (PackagePin pin : chip.packageDefinition().pins()) {
            PinPosition location = pinPosition(chip.packageDefinition().type(), pin.number());
            double xBody = Math.copySign(BODY_WIDTH / 2, location.x());
            double xEnd = Math.copySign(BODY_WIDTH / 2 + PIN_LENGTH, location.x());
            graphics.strokeLine(xBody, location.y(), xEnd, location.y());
            graphics.setTextAlign(location.x() < 0 ? TextAlignment.RIGHT : TextAlignment.LEFT);
            double textX = location.x() < 0 ? xBody - 5 : xBody + 5;
            graphics.fillText(pin.number() + " " + pin.name(), textX, location.y() + 4);
        }
        graphics.restore();
    }

    /** Pin coordinates in unrotated top view; right-side numbers descend top-to-bottom. */
    public static PinPosition pinPosition(PackageType type, int pinNumber) {
        int count = type.pinCount();
        if (pinNumber < 1 || pinNumber > count) {
            throw new IllegalArgumentException("Pin is outside " + type + ": " + pinNumber);
        }
        int side = count / 2;
        int row = pinNumber <= side ? pinNumber - 1 : count - pinNumber;
        double y = (row - (side - 1) / 2.0) * PIN_PITCH;
        double x = pinNumber <= side ? -(BODY_WIDTH / 2 + PIN_LENGTH)
                : BODY_WIDTH / 2 + PIN_LENGTH;
        return new PinPosition(x, y);
    }

    public record PinPosition(double x, double y) {
    }
}
