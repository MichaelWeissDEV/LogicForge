package dev.logicforge.ui.render;

import javafx.scene.canvas.GraphicsContext;

/**
 * The handful of outlines the gate symbols are built from — the flat-backed AND body, the
 * curved OR shield, the XOR's second back arc, the driver triangle and the inversion
 * bubble.
 *
 * <p>Six gates share four shapes; the differences are which outline is used and whether
 * there is a bubble on the output.
 */
final class SymbolShapes {

    /** Bezier factor that turns four segments into a circle. */
    private static final double KAPPA = 0.5522847498;

    static final double BUBBLE_RADIUS = 4;

    private SymbolShapes() {
    }

    /** Flat back, straight top and bottom, semicircular nose: the AND family. */
    static void andBody(GraphicsContext graphics, double halfWidth, double halfHeight) {
        double left = -halfWidth;
        double right = halfWidth;
        double radius = halfHeight;
        double arcStart = right - radius;
        double control = KAPPA * radius;

        graphics.beginPath();
        graphics.moveTo(left, -halfHeight);
        graphics.lineTo(arcStart, -halfHeight);
        graphics.bezierCurveTo(arcStart + control, -halfHeight, right, -control, right, 0);
        graphics.bezierCurveTo(right, control, arcStart + control, halfHeight, arcStart, halfHeight);
        graphics.lineTo(left, halfHeight);
        graphics.closePath();
    }

    /** Curved back, pointed nose: the OR family. */
    static void orBody(GraphicsContext graphics, double halfWidth, double halfHeight) {
        double left = -halfWidth;
        double right = halfWidth;
        double width = halfWidth * 2;

        graphics.beginPath();
        graphics.moveTo(left, -halfHeight);
        graphics.quadraticCurveTo(left + width * 0.62, -halfHeight, right, 0);
        graphics.quadraticCurveTo(left + width * 0.62, halfHeight, left, halfHeight);
        graphics.quadraticCurveTo(left + width * 0.30, 0, left, -halfHeight);
        graphics.closePath();
    }

    /** The extra arc that tells an XOR from an OR. */
    static void xorBackArc(GraphicsContext graphics, double halfWidth, double halfHeight) {
        double left = -halfWidth - 6;
        double width = halfWidth * 2;

        graphics.beginPath();
        graphics.moveTo(left, halfHeight);
        graphics.quadraticCurveTo(left + width * 0.30, 0, left, -halfHeight);
        graphics.stroke();
    }

    /** The driver triangle used by the buffer, the inverter and the tri-state buffers. */
    static void triangle(GraphicsContext graphics, double halfWidth, double halfHeight) {
        graphics.beginPath();
        graphics.moveTo(-halfWidth, -halfHeight);
        graphics.lineTo(halfWidth, 0);
        graphics.lineTo(-halfWidth, halfHeight);
        graphics.closePath();
    }

    /** The inversion bubble, drawn just past the nose of a symbol. */
    static void bubble(GraphicsContext graphics, double noseX) {
        double diameter = BUBBLE_RADIUS * 2;
        graphics.fillOval(noseX, -BUBBLE_RADIUS, diameter, diameter);
        graphics.strokeOval(noseX, -BUBBLE_RADIUS, diameter, diameter);
    }
}
