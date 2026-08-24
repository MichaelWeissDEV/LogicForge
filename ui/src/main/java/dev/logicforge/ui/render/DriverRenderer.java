package dev.logicforge.ui.render;

import javafx.scene.canvas.GraphicsContext;

/**
 * Buffer, inverter and the two tri-state buffers: a triangle, optionally with an inversion
 * bubble, and for the tri-state variants a short stem for the enable input.
 */
public record DriverRenderer(boolean inverted, boolean triState) implements ComponentRenderer {

    @Override
    public void drawSymbol(GraphicsContext graphics, SymbolContext context) {
        double halfWidth = context.halfWidth();
        double halfHeight = context.halfHeight();

        graphics.setFill(Theme.COMPONENT_FILL);
        graphics.setStroke(context.stroke());
        graphics.setLineWidth(Theme.BODY_STROKE);

        SymbolShapes.triangle(graphics, halfWidth, halfHeight);
        graphics.fill();
        graphics.stroke();

        if (triState) {
            // The enable input arrives at the flat underside of the triangle.
            graphics.strokeLine(0, halfHeight / 2, 0, halfHeight);
        }
        if (inverted) {
            graphics.setFill(Theme.COMPONENT_FILL);
            SymbolShapes.bubble(graphics, halfWidth);
        }
    }
}
