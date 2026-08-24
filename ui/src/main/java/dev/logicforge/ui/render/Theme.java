package dev.logicforge.ui.render;

import dev.logicforge.logic.LogicState;
import javafx.scene.paint.Color;

/**
 * The design tokens of the application.
 *
 * <p>Every colour and every measurement used on the canvas comes from here, and the
 * matching tokens for the controls live in {@code logicforge.css}. Nothing anywhere else
 * invents a colour of its own, which is what keeps the editor looking like one program and
 * makes a light theme a matter of swapping this table.
 */
public final class Theme {

    // Surfaces -----------------------------------------------------------
    public static final Color CANVAS_BACKGROUND = Color.web("#15181d");
    public static final Color GRID_MINOR = Color.web("#1d2128");
    public static final Color GRID_MAJOR = Color.web("#272d36");

    // Text ---------------------------------------------------------------
    public static final Color TEXT_PRIMARY = Color.web("#e6e9ef");
    public static final Color TEXT_SECONDARY = Color.web("#98a2b3");
    public static final Color TEXT_MUTED = Color.web("#667085");

    // Components ---------------------------------------------------------
    public static final Color COMPONENT_FILL = Color.web("#1c2027");
    public static final Color COMPONENT_STROKE = Color.web("#aeb8c6");
    public static final Color COMPONENT_STROKE_HOVER = Color.web("#d7dee8");
    public static final Color SELECTION = Color.web("#5b9dff");
    public static final Color SELECTION_FILL = Color.web("#5b9dff33", 1);
    public static final Color PORT = Color.web("#7f8b9c");
    public static final Color PORT_HIGHLIGHT = Color.web("#5b9dff");

    // Signals ------------------------------------------------------------
    private static final Color SIGNAL_ZERO = Color.web("#59657a");
    private static final Color SIGNAL_ONE = Color.web("#4ade80");
    private static final Color SIGNAL_UNKNOWN = Color.web("#f59e0b");
    private static final Color SIGNAL_HIGH_Z = Color.web("#38bdf8");
    public static final Color SIGNAL_CONFLICT = Color.web("#ef4444");
    public static final Color WIRE_UNPOWERED = Color.web("#4a5361");

    // Measurements (circuit units) ---------------------------------------
    public static final double BODY_STROKE = 1.6;
    public static final double WIRE_STROKE = 1.6;
    public static final double PORT_RADIUS = 2.4;
    public static final double JUNCTION_RADIUS = 3.2;
    public static final double LABEL_SIZE = 10;
    public static final double PIN_LABEL_SIZE = 7;

    private Theme() {
    }

    /** The colour a wire or an indicator takes for a given signal value. */
    public static Color signalColor(LogicState state) {
        return switch (state) {
            case ZERO -> SIGNAL_ZERO;
            case ONE -> SIGNAL_ONE;
            case UNKNOWN -> SIGNAL_UNKNOWN;
            case HIGH_IMPEDANCE -> SIGNAL_HIGH_Z;
        };
    }

    /** The colour of an LED body for the configured colour name. */
    public static Color ledColor(String name) {
        return switch (name) {
            case "red" -> Color.web("#ef4444");
            case "amber" -> Color.web("#f59e0b");
            case "blue" -> Color.web("#3b82f6");
            default -> Color.web("#22c55e");
        };
    }
}
