package dev.logicforge.ui.render;

import dev.logicforge.library.LibraryParameters;
import dev.logicforge.logic.LogicState;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * LEDs, logic probes and output pins.
 *
 * <p>All three tell the four states apart: an LED lights up for 1, stays dark for 0 and
 * shows an X or a Z marking when the signal is unknown or floating.
 */
public record OutputRenderer(Kind kind) implements ComponentRenderer {

    public enum Kind {
        LED,
        PROBE,
        PIN
    }

    @Override
    public void drawSymbol(GraphicsContext graphics, SymbolContext context) {
        LogicState value = context.value("IN");
        switch (kind) {
            case LED -> drawLed(graphics, context, value);
            case PROBE -> drawProbe(graphics, context, value);
            case PIN -> drawPin(graphics, context, value);
        }
    }

    private void drawLed(GraphicsContext graphics, SymbolContext context, LogicState value) {
        double radius = context.halfHeight() - 2;
        Color colour = Theme.ledColor(context.instance().parameters()
                .get(LibraryParameters.LED_COLOR));

        Color fill = switch (value) {
            case ONE -> colour;
            case ZERO -> colour.deriveColor(0, 1, 0.32, 1);
            case UNKNOWN -> Theme.signalColor(LogicState.UNKNOWN).deriveColor(0, 1, 0.55, 1);
            case HIGH_IMPEDANCE -> Theme.COMPONENT_FILL;
        };

        if (value == LogicState.ONE) {
            graphics.setFill(colour.deriveColor(0, 1, 1, 0.22));
            graphics.fillOval(-radius - 5, -radius - 5, (radius + 5) * 2, (radius + 5) * 2);
        }
        graphics.setFill(fill);
        graphics.setStroke(context.stroke());
        graphics.setLineWidth(Theme.BODY_STROKE);
        graphics.fillOval(-radius, -radius, radius * 2, radius * 2);
        graphics.strokeOval(-radius, -radius, radius * 2, radius * 2);

        if (!value.isDefined()) {
            markState(graphics, value, Theme.TEXT_PRIMARY);
        }
    }

    private void drawProbe(GraphicsContext graphics, SymbolContext context, LogicState value) {
        double halfWidth = context.halfWidth();
        double halfHeight = context.halfHeight();

        graphics.setFill(Theme.COMPONENT_FILL);
        graphics.setStroke(context.stroke());
        graphics.setLineWidth(Theme.BODY_STROKE);
        graphics.fillRoundRect(-halfWidth, -halfHeight, halfWidth * 2, halfHeight * 2, 4, 4);
        graphics.strokeRoundRect(-halfWidth, -halfHeight, halfWidth * 2, halfHeight * 2, 4, 4);

        markState(graphics, value, Theme.signalColor(value));
    }

    private void drawPin(GraphicsContext graphics, SymbolContext context, LogicState value) {
        double halfWidth = context.halfWidth();
        double halfHeight = context.halfHeight() * 0.7;

        graphics.setFill(Theme.COMPONENT_FILL);
        graphics.setStroke(context.stroke());
        graphics.setLineWidth(Theme.BODY_STROKE);
        graphics.beginPath();
        graphics.moveTo(-halfWidth, -halfHeight);
        graphics.lineTo(halfWidth - 6, -halfHeight);
        graphics.lineTo(halfWidth, 0);
        graphics.lineTo(halfWidth - 6, halfHeight);
        graphics.lineTo(-halfWidth, halfHeight);
        graphics.closePath();
        graphics.fill();
        graphics.stroke();

        markState(graphics, value, Theme.signalColor(value));
    }

    private void markState(GraphicsContext graphics, LogicState value, Color colour) {
        graphics.setFill(colour);
        graphics.setFont(Font.font(13));
        graphics.setTextAlign(TextAlignment.CENTER);
        graphics.setTextBaseline(VPos.CENTER);
        graphics.fillText(String.valueOf(value.symbol()), 0, 1);
    }
}
