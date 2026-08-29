package dev.logicforge.ui.render;

import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
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
    public static final Color WORKSPACE_BACKGROUND = CANVAS_BACKGROUND;
    public static final Color PANEL_BACKGROUND = Color.web("#191d23");
    public static final Color ELEVATED_PANEL = Color.web("#1f242c");
    public static final Color BORDER = Color.web("#2a303a");
    public static final Color GRID_MINOR = Color.web("#1d2128");
    public static final Color GRID_MAJOR = Color.web("#272d36");
    public static final Color DIGITAL_REFERENCE = Color.web("#303640");

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
    public static final Color CURSOR_A = Color.web("#fbbf24");
    public static final Color CURSOR_B = Color.web("#c084fc");

    // Signals ------------------------------------------------------------
    private static final Color SIGNAL_ZERO = Color.web("#59657a");
    private static final Color SIGNAL_ONE = Color.web("#4ade80");
    private static final Color SIGNAL_UNKNOWN = Color.web("#f59e0b");
    private static final Color SIGNAL_HIGH_Z = Color.web("#38bdf8");
    public static final Color SIGNAL_CONFLICT = Color.web("#ef4444");
    public static final Color ERROR = SIGNAL_CONFLICT;
    public static final Color WARNING = Color.web("#f59e0b");
    public static final Color SUCCESS = Color.web("#22c55e");
    public static final Color WIRE_UNPOWERED = Color.web("#4a5361");
    public static final Color BUS_DEFINED = Color.web("#a78bfa");

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

    /**
     * The color to use for a multi-bit bus wire.
     * <ul>
     *   <li>Any bit UNKNOWN → {@link #SIGNAL_UNKNOWN} (amber)</li>
     *   <li>All bits HIGH_IMPEDANCE → {@link #SIGNAL_HIGH_Z} (cyan)</li>
     *   <li>All bits ZERO → {@link #SIGNAL_ZERO} (dark grey, same as an idle 1-bit wire)</li>
     *   <li>Mix of 0/1 → {@link #BUS_DEFINED} (purple/violet)</li>
     * </ul>
     * Conflict (multiple drivers) is handled at the call site using {@link #SIGNAL_CONFLICT}.
     */
    public static Color busColor(LogicVector value) {
        boolean hasUnknown = false;
        boolean allHighZ = true;
        boolean allZero = true;
        for (int i = 0; i < value.width(); i++) {
            LogicState bit = value.getBit(i);
            if (bit == LogicState.UNKNOWN) hasUnknown = true;
            if (bit != LogicState.HIGH_IMPEDANCE) allHighZ = false;
            if (bit != LogicState.ZERO) allZero = false;
        }
        if (hasUnknown) return SIGNAL_UNKNOWN;
        if (allHighZ) return SIGNAL_HIGH_Z;
        if (allZero) return SIGNAL_ZERO;
        return BUS_DEFINED;
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
