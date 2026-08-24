package dev.logicforge.logic;

import static dev.logicforge.logic.LogicState.HIGH_IMPEDANCE;
import static dev.logicforge.logic.LogicState.ONE;
import static dev.logicforge.logic.LogicState.UNKNOWN;
import static dev.logicforge.logic.LogicState.ZERO;

/**
 * The single, central definition of LogicForge's four-state logic semantics.
 *
 * <p>Every gate, every net and every component in the simulator uses the rules defined
 * here. Components must never implement their own slightly different treatment of
 * {@code X} or {@code Z}.
 *
 * <h2>Rule 1 — a floating gate input is unknown</h2>
 *
 * A gate input that reads {@link LogicState#HIGH_IMPEDANCE} is not connected to any active
 * driver. Real logic families do not produce a defined result for such an input, so
 * LogicForge maps {@code Z} to {@code X} <em>on the way into</em> any gate evaluation (see
 * {@link #asGateInput(LogicState)}). Unconnected gate inputs are therefore {@code X}, never
 * an implicit {@code 0}.
 *
 * <h2>Rule 2 — controlling values dominate</h2>
 *
 * If an input forces the result of an operation regardless of the other inputs, the result
 * is defined even when other inputs are unknown:
 *
 * <pre>
 *   0 AND X = 0        1 OR X = 1
 *   1 AND X = X        0 OR X = X
 * </pre>
 *
 * XOR has no controlling value, so any unknown input makes the result {@code X}.
 *
 * <h2>Rule 3 — inverting variants share the base table</h2>
 *
 * NAND, NOR and XNOR are defined as their base operation followed by {@link #not},
 * with {@code NOT X = X}. There is no separate table for them.
 *
 * <h2>Rule 4 — gates never output Z</h2>
 *
 * Only an explicitly tri-stated driver (a disabled tri-state buffer, or a net without any
 * driver) produces {@code Z}. Ordinary logic gates always drive their output.
 *
 * <h2>Rule 5 — net resolution</h2>
 *
 * See {@link #resolve(LogicState, LogicState)}. Undriven contributions are ignored,
 * agreeing drivers keep their value, and conflicting drivers produce {@code X}.
 */
public final class LogicOperations {

    private LogicOperations() {
    }

    /**
     * Converts a raw net value into the value seen by a gate input: {@code Z} becomes
     * {@code X} (rule 1), everything else is unchanged.
     */
    public static LogicState asGateInput(LogicState state) {
        return state == HIGH_IMPEDANCE ? UNKNOWN : state;
    }

    public static LogicState not(LogicState a) {
        return switch (asGateInput(a)) {
            case ZERO -> ONE;
            case ONE -> ZERO;
            default -> UNKNOWN;
        };
    }

    public static LogicState and(LogicState a, LogicState b) {
        LogicState x = asGateInput(a);
        LogicState y = asGateInput(b);
        if (x == ZERO || y == ZERO) {
            return ZERO; // controlling value
        }
        if (x == ONE && y == ONE) {
            return ONE;
        }
        return UNKNOWN;
    }

    public static LogicState or(LogicState a, LogicState b) {
        LogicState x = asGateInput(a);
        LogicState y = asGateInput(b);
        if (x == ONE || y == ONE) {
            return ONE; // controlling value
        }
        if (x == ZERO && y == ZERO) {
            return ZERO;
        }
        return UNKNOWN;
    }

    public static LogicState xor(LogicState a, LogicState b) {
        LogicState x = asGateInput(a);
        LogicState y = asGateInput(b);
        if (!x.isDefined() || !y.isDefined()) {
            return UNKNOWN; // XOR has no controlling value
        }
        return LogicState.of(x != y);
    }

    public static LogicState nand(LogicState a, LogicState b) {
        return not(and(a, b));
    }

    public static LogicState nor(LogicState a, LogicState b) {
        return not(or(a, b));
    }

    public static LogicState xnor(LogicState a, LogicState b) {
        return not(xor(a, b));
    }

    /**
     * Folds {@code inputs} with {@code operation}. All supported operations are
     * commutative and associative under these rules, so the result does not depend on the
     * input order. An empty input array yields the operation's identity element.
     *
     * <p>Multi-input XOR therefore means "odd number of ones", and becomes {@code X} as
     * soon as any input is not fully defined.
     */
    public static LogicState reduce(LogicOperation operation, LogicState... inputs) {
        LogicState result = operation.identity();
        for (LogicState input : inputs) {
            result = operation.apply(result, input);
        }
        return result;
    }

    /**
     * Resolves the contributions of all drivers of a net into the net's value.
     *
     * <pre>
     *   Z + Z            = Z    (nothing drives the net)
     *   Z + v            = v    (undriven contributions are ignored)
     *   0 + 0            = 0
     *   1 + 1            = 1
     *   0 + 1            = X    (driver conflict)
     *   X + anything     = X
     * </pre>
     *
     * The operation is commutative and associative, so a net's value never depends on the
     * order in which its drivers are visited.
     */
    public static LogicState resolve(LogicState a, LogicState b) {
        if (a == HIGH_IMPEDANCE) {
            return b;
        }
        if (b == HIGH_IMPEDANCE) {
            return a;
        }
        return a == b ? a : UNKNOWN;
    }

    /** Resolves a whole set of driver contributions. No drivers at all resolves to {@code Z}. */
    public static LogicState resolve(LogicState... drivers) {
        LogicState result = HIGH_IMPEDANCE;
        for (LogicState driver : drivers) {
            result = resolve(result, driver);
        }
        return result;
    }

    /** {@code true} if at least two drivers actively drive conflicting values. */
    public static boolean hasDriverConflict(LogicState... drivers) {
        LogicState first = HIGH_IMPEDANCE;
        for (LogicState driver : drivers) {
            if (driver == HIGH_IMPEDANCE) {
                continue;
            }
            if (first == HIGH_IMPEDANCE) {
                first = driver;
            } else if (first != driver) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Vector variants. All of them operate bit by bit and refuse to combine
    // signals of different widths (no silent truncation, no zero extension).
    // ---------------------------------------------------------------------

    /** Applies {@link #asGateInput(LogicState)} to every bit. */
    public static LogicVector asGateInput(LogicVector value) {
        LogicVector result = value;
        for (int i = 0; i < value.width(); i++) {
            result = result.withBit(i, asGateInput(value.getBit(i)));
        }
        return result;
    }

    public static LogicVector not(LogicVector a) {
        LogicVector result = a;
        for (int i = 0; i < a.width(); i++) {
            result = result.withBit(i, not(a.getBit(i)));
        }
        return result;
    }

    /** Applies {@code operation} bit by bit. Both operands must have the same width. */
    public static LogicVector apply(LogicOperation operation, LogicVector a, LogicVector b) {
        b.requireWidth(a.width());
        LogicVector result = a;
        for (int i = 0; i < a.width(); i++) {
            result = result.withBit(i, operation.apply(a.getBit(i), b.getBit(i)));
        }
        return result;
    }

    /**
     * Folds {@code inputs} bit by bit with {@code operation}. All inputs must have
     * {@code width} bits; an empty input list yields the operation's identity.
     */
    public static LogicVector reduce(LogicOperation operation, int width, LogicVector... inputs) {
        LogicVector result = LogicVector.repeat(operation.identity(), width);
        for (LogicVector input : inputs) {
            result = apply(operation, result, input);
        }
        return result;
    }

    /** Resolves two driver contributions bit by bit. Both must have the same width. */
    public static LogicVector resolve(LogicVector a, LogicVector b) {
        b.requireWidth(a.width());
        LogicVector result = a;
        for (int i = 0; i < a.width(); i++) {
            result = result.withBit(i, resolve(a.getBit(i), b.getBit(i)));
        }
        return result;
    }
}
