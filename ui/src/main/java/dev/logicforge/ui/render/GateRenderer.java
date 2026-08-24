package dev.logicforge.ui.render;

import javafx.scene.canvas.GraphicsContext;

/**
 * The six multi-input gates. They differ in outline and in whether the output carries an
 * inversion bubble — one renderer covers all of them.
 */
public record GateRenderer(Outline outline, boolean inverted) implements ComponentRenderer {

    public enum Outline {
        AND,
        OR,
        XOR
    }

    @Override
    public void drawSymbol(GraphicsContext graphics, SymbolContext context) {
        double halfWidth = context.halfWidth();
        double halfHeight = context.halfHeight();

        graphics.setFill(Theme.COMPONENT_FILL);
        graphics.setStroke(context.stroke());
        graphics.setLineWidth(Theme.BODY_STROKE);

        switch (outline) {
            case AND -> SymbolShapes.andBody(graphics, halfWidth, halfHeight);
            case OR, XOR -> SymbolShapes.orBody(graphics, halfWidth, halfHeight);
        }
        graphics.fill();
        graphics.stroke();

        if (outline == Outline.XOR) {
            SymbolShapes.xorBackArc(graphics, halfWidth, halfHeight);
        }
        if (inverted) {
            graphics.setFill(Theme.COMPONENT_FILL);
            SymbolShapes.bubble(graphics, halfWidth);
        }
    }
}
