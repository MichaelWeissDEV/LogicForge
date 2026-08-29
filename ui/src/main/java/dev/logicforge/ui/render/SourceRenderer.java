package dev.logicforge.ui.render;

import dev.logicforge.library.LibraryParameters;
import dev.logicforge.logic.LogicState;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * Constants, toggle switches and push buttons.
 *
 * <p>All of them show what they are currently driving, so the state of the inputs of a
 * circuit can be read at a glance.
 */
public record SourceRenderer(Kind kind, LogicState constantValue) implements ComponentRenderer {

    public enum Kind {
        CONSTANT,
        TOGGLE,
        BUTTON
    }

    @Override
    public void drawSymbol(GraphicsContext graphics, SymbolContext context) {
        double halfWidth = context.halfWidth();
        double halfHeight = context.halfHeight();
        LogicState value = kind == Kind.CONSTANT ? constantValue : context.value("OUT");

        graphics.setFill(Theme.COMPONENT_FILL);
        graphics.setStroke(context.stroke());
        graphics.setLineWidth(Theme.BODY_STROKE);
        graphics.fillRoundRect(-halfWidth, -halfHeight, halfWidth * 2, halfHeight * 2, 6, 6);
        graphics.strokeRoundRect(-halfWidth, -halfHeight, halfWidth * 2, halfHeight * 2, 6, 6);

        switch (kind) {
            case CONSTANT -> drawValue(graphics, value);
            case TOGGLE -> drawSwitch(graphics, halfWidth, halfHeight, value);
            case BUTTON -> drawButton(graphics, value, context);
        }
    }

    private void drawValue(GraphicsContext graphics, LogicState value) {
        graphics.setFill(Theme.signalColor(value));
        graphics.setFont(Font.font(14));
        graphics.setTextAlign(TextAlignment.CENTER);
        graphics.setTextBaseline(VPos.CENTER);
        graphics.fillText(String.valueOf(value.symbol()), 0, 1);
    }

    /**
     * A knife switch: a lever hinged on the left that either rests on the right contact
     * (closed, driving 1) or stands away from it (open, driving 0).
     */
    private void drawSwitch(GraphicsContext graphics, double halfWidth, double halfHeight,
                            LogicState value) {
        boolean on = value == LogicState.ONE;
        double pivotX = -halfWidth + 7;
        double contactX = halfWidth - 7;
        double baseline = 5;

        graphics.setStroke(Theme.signalColor(value));
        graphics.setFill(Theme.signalColor(value));
        graphics.setLineWidth(1.8);

        // The two contacts.
        graphics.fillOval(pivotX - 2.5, baseline - 2.5, 5, 5);
        graphics.fillOval(contactX - 2.5, baseline - 2.5, 5, 5);

        // The lever, closed onto the right contact or lifted away from it.
        double leverEndX = on ? contactX : contactX - 3;
        double leverEndY = on ? baseline : baseline - 13;
        graphics.strokeLine(pivotX, baseline, leverEndX, leverEndY);
    }

    private void drawButton(GraphicsContext graphics, LogicState value, SymbolContext context) {
        boolean pressed = context.value("OUT") != LogicState.HIGH_IMPEDANCE
                && isPressed(context, value);
        double radius = context.halfHeight() - 7;

        graphics.setFill(pressed ? Theme.signalColor(value) : Theme.COMPONENT_FILL);
        graphics.setStroke(Theme.signalColor(value));
        graphics.setLineWidth(1.6);
        graphics.fillOval(-radius, -radius, radius * 2, radius * 2);
        graphics.strokeOval(-radius, -radius, radius * 2, radius * 2);
    }

    /** A button that is configured as inverted reads 1 while it is <em>not</em> pressed. */
    private boolean isPressed(SymbolContext context, LogicState value) {
        boolean inverted = context.instance().parameters().getBoolean(LibraryParameters.INVERTED);
        return inverted ? value == LogicState.ZERO : value == LogicState.ONE;
    }
}
