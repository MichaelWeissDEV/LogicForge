package dev.logicforge.circuit.component;

/**
 * Defines how a user-interactive component responds to input gestures.
 *
 * <p>This metadata is separate from the component's electrical behavior and is used
 * by the UI to determine the correct interaction semantics.
 */
public enum InputInteraction {
    /** The component has no user interaction capability. */
    NONE,
    
    /** Toggles between states on each click (e.g., a toggle switch).
     * click → 0 ↔ 1 */
    TOGGLE,
    
    /** Outputs a signal only while actively pressed (e.g., a push button).
     * mouse press → active state
     * mouse release → inactive state */
    MOMENTARY
}
