package dev.logicforge.logic;

/**
 * Thrown when signals of incompatible widths are combined. LogicForge never silently
 * truncates or zero-extends a value.
 */
public class WidthMismatchException extends IllegalArgumentException {

    private final int expectedWidth;
    private final int actualWidth;

    public WidthMismatchException(int expectedWidth, int actualWidth) {
        super("Expected a " + expectedWidth + "-bit value but got " + actualWidth + " bits");
        this.expectedWidth = expectedWidth;
        this.actualWidth = actualWidth;
    }

    public int expectedWidth() {
        return expectedWidth;
    }

    public int actualWidth() {
        return actualWidth;
    }
}
