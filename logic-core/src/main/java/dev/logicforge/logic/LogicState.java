package dev.logicforge.logic;

/**
 * A single digital signal value in LogicForge's four-state logic system.
 *
 * <p>The four states are the ones commonly used by digital simulators and hardware
 * description languages:
 *
 * <ul>
 *   <li>{@link #ZERO} — driven low ({@code 0})
 *   <li>{@link #ONE} — driven high ({@code 1})
 *   <li>{@link #UNKNOWN} — driven, but the value is not known ({@code X}). Produced by
 *       conflicting drivers or by gates whose result cannot be determined.
 *   <li>{@link #HIGH_IMPEDANCE} — not driven at all ({@code Z}). Produced by disabled
 *       tri-state drivers and by nets that have no driver.
 * </ul>
 *
 * <p>The exact semantics of {@code X} and {@code Z} in gate evaluation and net resolution
 * are defined centrally in {@link LogicOperations}; no component may define its own
 * variant of those rules.
 */
public enum LogicState {

    ZERO('0'),
    ONE('1'),
    UNKNOWN('X'),
    HIGH_IMPEDANCE('Z');

    private final char symbol;

    LogicState(char symbol) {
        this.symbol = symbol;
    }

    /** The single-character symbol used in textual representations: {@code 0 1 X Z}. */
    public char symbol() {
        return symbol;
    }

    /** {@code true} for {@link #ZERO} and {@link #ONE}, the two fully defined states. */
    public boolean isDefined() {
        return this == ZERO || this == ONE;
    }

    /**
     * {@code true} if this state is actively driven, i.e. anything except
     * {@link #HIGH_IMPEDANCE}. {@code X} counts as driven: something is driving the net,
     * we simply cannot say what value results.
     */
    public boolean isDriven() {
        return this != HIGH_IMPEDANCE;
    }

    /** Maps {@code true}/{@code false} onto {@link #ONE}/{@link #ZERO}. */
    public static LogicState of(boolean value) {
        return value ? ONE : ZERO;
    }

    /** Parses one of {@code 0 1 X x Z z} into a state. */
    public static LogicState fromSymbol(char symbol) {
        return switch (symbol) {
            case '0' -> ZERO;
            case '1' -> ONE;
            case 'X', 'x' -> UNKNOWN;
            case 'Z', 'z' -> HIGH_IMPEDANCE;
            default -> throw new IllegalArgumentException("Not a logic state symbol: '" + symbol + "'");
        };
    }

    @Override
    public String toString() {
        return String.valueOf(symbol);
    }
}
