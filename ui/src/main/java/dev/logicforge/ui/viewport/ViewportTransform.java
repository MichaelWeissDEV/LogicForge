package dev.logicforge.ui.viewport;

import dev.logicforge.circuit.geometry.CircuitBounds;
import dev.logicforge.circuit.geometry.CircuitPoint;

/**
 * The one place that converts between circuit coordinates and screen pixels.
 *
 * <pre>
 *   screen = world * scale + translation
 *   world  = (screen - translation) / scale
 * </pre>
 *
 * <p>Nothing else in the UI is allowed to do this arithmetic itself. Because the class is
 * plain Java it can be — and is — unit tested without starting a JavaFX toolkit, which is
 * what keeps drag &amp; drop landing on the right grid cell at any zoom level.
 */
public final class ViewportTransform {

    public static final double MIN_SCALE = 0.1;
    public static final double MAX_SCALE = 5.0;

    private double scale = 1.0;
    private double translationX;
    private double translationY;

    public double scale() {
        return scale;
    }

    public double translationX() {
        return translationX;
    }

    public double translationY() {
        return translationY;
    }

    public ScreenPoint worldToScreen(CircuitPoint world) {
        return new ScreenPoint(world.x() * scale + translationX, world.y() * scale + translationY);
    }

    public CircuitPoint screenToWorld(double screenX, double screenY) {
        return new CircuitPoint((screenX - translationX) / scale, (screenY - translationY) / scale);
    }

    public CircuitPoint screenToWorld(ScreenPoint screen) {
        return screenToWorld(screen.x(), screen.y());
    }

    /** Converts a length; screen distances scale, unlike positions they are not translated. */
    public double worldToScreenLength(double worldLength) {
        return worldLength * scale;
    }

    public double screenToWorldLength(double screenLength) {
        return screenLength / scale;
    }

    /** Moves the view by a screen-space delta, as a pan gesture does. */
    public void panBy(double screenDeltaX, double screenDeltaY) {
        translationX += screenDeltaX;
        translationY += screenDeltaY;
    }

    /**
     * Zooms around a fixed screen position — the mouse cursor — so the circuit point under
     * the cursor stays exactly where it is.
     */
    public void zoomBy(double factor, double screenX, double screenY) {
        CircuitPoint anchor = screenToWorld(screenX, screenY);
        setScale(scale * factor);
        translationX = screenX - anchor.x() * scale;
        translationY = screenY - anchor.y() * scale;
    }

    /** Sets the zoom level, keeping the given screen position fixed. */
    public void setScaleAround(double newScale, double screenX, double screenY) {
        zoomBy(newScale / scale, screenX, screenY);
    }

    private void setScale(double newScale) {
        scale = Math.clamp(newScale, MIN_SCALE, MAX_SCALE);
    }

    /** Positions the view so that {@code world} appears at the centre of the viewport. */
    public void centerOn(CircuitPoint world, double viewportWidth, double viewportHeight) {
        translationX = viewportWidth / 2 - world.x() * scale;
        translationY = viewportHeight / 2 - world.y() * scale;
    }

    /** Fits the given circuit area into the viewport, with a little margin. */
    public void fit(CircuitBounds content, double viewportWidth, double viewportHeight) {
        if (content.width() <= 0 || content.height() <= 0) {
            return;
        }
        double margin = 40;
        double fitScale = Math.min((viewportWidth - margin) / content.width(),
                (viewportHeight - margin) / content.height());
        setScale(fitScale);
        centerOn(content.center(), viewportWidth, viewportHeight);
    }

    public void reset() {
        scale = 1;
        translationX = 0;
        translationY = 0;
    }

    /**
     * The circuit area currently visible. Rendering and hit testing use it to skip
     * everything off screen.
     */
    public CircuitBounds visibleWorldBounds(double viewportWidth, double viewportHeight) {
        CircuitPoint topLeft = screenToWorld(0, 0);
        CircuitPoint bottomRight = screenToWorld(viewportWidth, viewportHeight);
        return CircuitBounds.between(topLeft, bottomRight);
    }
}
