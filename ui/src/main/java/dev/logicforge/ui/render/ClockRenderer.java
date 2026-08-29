package dev.logicforge.ui.render;

import dev.logicforge.logic.LogicState;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/** Purpose-built clock symbol with a compact, electrically meaningful square-wave glyph. */
public final class ClockRenderer implements ComponentRenderer {

    /** Testable local-coordinate geometry shared by drawing and renderer tests. */
    public record GlyphGeometry(
            double left, double right, double highY, double lowY,
            double firstRiseX, double firstFallX, double secondRiseX) {

        public GlyphGeometry {
            if (!(left < firstRiseX && firstRiseX < firstFallX
                    && firstFallX < secondRiseX && secondRiseX < right)) {
                throw new IllegalArgumentException("clock transitions must be strictly ordered");
            }
            if (highY >= lowY) {
                throw new IllegalArgumentException("high rail must be above low rail");
            }
        }
    }

    public static GlyphGeometry geometry(double halfWidth, double halfHeight) {
        double left = -halfWidth + 7;
        double right = halfWidth - 7;
        double high = -Math.min(7, halfHeight - 5);
        double low = Math.min(7, halfHeight - 5);
        double span = right - left;
        return new GlyphGeometry(left, right, high, low,
                left + span * 0.18, left + span * 0.50, left + span * 0.82);
    }

    @Override
    public void drawSymbol(GraphicsContext graphics, SymbolContext context) {
        double halfWidth = context.halfWidth();
        double halfHeight = context.halfHeight();
        LogicState output = context.value("OUT");

        graphics.setFill(Theme.COMPONENT_FILL);
        graphics.setStroke(context.stroke());
        graphics.setLineWidth(Theme.BODY_STROKE);
        graphics.fillRoundRect(-halfWidth, -halfHeight, halfWidth * 2, halfHeight * 2, 5, 5);
        graphics.strokeRoundRect(-halfWidth, -halfHeight, halfWidth * 2, halfHeight * 2, 5, 5);

        GlyphGeometry glyph = geometry(halfWidth, halfHeight);
        graphics.setStroke(Theme.TEXT_PRIMARY);
        graphics.setLineWidth(1.7);
        graphics.strokeLine(glyph.left(), glyph.lowY(), glyph.firstRiseX(), glyph.lowY());
        graphics.strokeLine(glyph.firstRiseX(), glyph.lowY(), glyph.firstRiseX(), glyph.highY());
        graphics.strokeLine(glyph.firstRiseX(), glyph.highY(), glyph.firstFallX(), glyph.highY());
        graphics.strokeLine(glyph.firstFallX(), glyph.highY(), glyph.firstFallX(), glyph.lowY());
        graphics.strokeLine(glyph.firstFallX(), glyph.lowY(), glyph.secondRiseX(), glyph.lowY());
        graphics.strokeLine(glyph.secondRiseX(), glyph.lowY(), glyph.secondRiseX(), glyph.highY());
        graphics.strokeLine(glyph.secondRiseX(), glyph.highY(), glyph.right(), glyph.highY());

        // A small output-state marker supports live debugging without becoming the symbol.
        graphics.setFill(Theme.signalColor(output));
        graphics.fillOval(halfWidth - 5, -2, 4, 4);

        if (halfHeight >= 20) {
            graphics.setFill(Theme.TEXT_MUTED);
            graphics.setFont(Font.font(7.5));
            graphics.setTextAlign(TextAlignment.CENTER);
            graphics.setTextBaseline(VPos.BOTTOM);
            graphics.fillText("CLK", 0, halfHeight - 2);
        }
    }
}
