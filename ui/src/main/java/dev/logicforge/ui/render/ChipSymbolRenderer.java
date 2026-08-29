package dev.logicforge.ui.render;

import dev.logicforge.circuit.chip.ChipDefinition;
import dev.logicforge.circuit.chip.ChipGeometry;
import dev.logicforge.circuit.chip.ChipLogicalUnit;
import dev.logicforge.circuit.chip.PackagePin;
import dev.logicforge.circuit.chip.PackageType;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.Rotation;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * A compact logical view of a physical chip: a smaller body naming the contained logical
 * units instead of full DIP package artwork.
 *
 * <p>Pin positions come from {@link ChipGeometry}, exactly like {@link ChipPackageRenderer}
 * — only the body and its stub lengths shrink. A pin is at the same clickable world position
 * in every display mode, so hit testing never has to know which one is on screen.
 */
public final class ChipSymbolRenderer {

    private static final double BODY_WIDTH = 90;
    private static final double MIN_BODY_HEIGHT = 60;
    private static final double UNIT_ROW_HEIGHT = 16;
    private static final double HEADER_HEIGHT = 34;

    public void draw(GraphicsContext graphics, ChipDefinition chip, String referenceDesignator,
                     CircuitPoint center, Rotation rotation, IntFunction<Color> pinColor, int hoveredPinNumber,
                     Function<String, String> unitDisplayName) {
        PackageType type = chip.packageDefinition().type();
        double bodyHeight = Math.max(MIN_BODY_HEIGHT, ChipGeometry.bodyHeight(type));

        graphics.save();
        graphics.translate(center.x(), center.y());
        graphics.rotate(rotation.degrees());

        graphics.setFill(Theme.CHIP_BODY_FILL);
        graphics.setStroke(Theme.CHIP_BODY_STROKE);
        graphics.setLineWidth(1.4);
        graphics.fillRoundRect(-BODY_WIDTH / 2, -bodyHeight / 2, BODY_WIDTH, bodyHeight, 6, 6);
        graphics.strokeRoundRect(-BODY_WIDTH / 2, -bodyHeight / 2, BODY_WIDTH, bodyHeight, 6, 6);

        graphics.setTextAlign(TextAlignment.CENTER);
        graphics.setFont(Font.font(12));
        graphics.setFill(Theme.TEXT_PRIMARY);
        graphics.fillText(referenceDesignator, 0, -bodyHeight / 2 + 14);
        graphics.setFont(Font.font(10));
        graphics.setFill(Theme.TEXT_SECONDARY);
        graphics.fillText(chip.metadata().partNumber(), 0, -bodyHeight / 2 + 27);

        double unitY = -bodyHeight / 2 + HEADER_HEIGHT;
        graphics.setFont(Font.font(9));
        graphics.setFill(Theme.TEXT_MUTED);
        for (Map.Entry<String, Long> unit : unitSummary(chip, unitDisplayName).entrySet()) {
            graphics.fillText(unit.getValue() + "× " + unit.getKey(), 0, unitY);
            unitY += UNIT_ROW_HEIGHT;
        }

        for (PackagePin pin : chip.packageDefinition().pins()) {
            CircuitPoint tip = ChipGeometry.localPinTip(type, pin.number());
            CircuitPoint bodyEdge = new CircuitPoint(Math.copySign(BODY_WIDTH / 2, tip.x()), tip.y());
            boolean hovered = pin.number() == hoveredPinNumber;
            Color color = pinColor.apply(pin.number());
            graphics.setLineWidth(hovered ? Theme.WIRE_STROKE + 0.8 : Theme.WIRE_STROKE);
            graphics.setStroke(color);
            graphics.strokeLine(bodyEdge.x(), bodyEdge.y(), tip.x(), tip.y());
            double radius = hovered ? Theme.PORT_RADIUS * 1.8 : Theme.PORT_RADIUS;
            graphics.setFill(hovered ? Theme.PORT_HIGHLIGHT : color);
            graphics.fillOval(tip.x() - radius, tip.y() - radius, radius * 2, radius * 2);
        }
        graphics.setLineWidth(Theme.WIRE_STROKE);
        graphics.restore();
    }

    /** Logical units grouped by their component type, in first-seen order, with a count each. */
    private static Map<String, Long> unitSummary(ChipDefinition chip, Function<String, String> unitDisplayName) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ChipLogicalUnit unit : chip.logicalUnits()) {
            String label = unitDisplayName.apply(unit.componentDefinitionId())
                    .toUpperCase(Locale.ROOT);
            counts.merge(label, 1L, Long::sum);
        }
        return counts;
    }
}
