package dev.logicforge.analyzer;

import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import java.util.Objects;

/**
 * What must happen to a watched signal's value for a trigger to fire, expressed purely in
 * four-valued analyzer vocabulary — never a compiler {@code ResolvedSignal} (see {@link
 * AnalyzerSignalBinding}'s own javadoc for why this module stays independent of the
 * compiler).
 *
 * <p>Edge conditions look at one bit of the watched value and require an actual two-valued
 * transition: {@link RisingEdge} only fires on a literal {@code ZERO -> ONE} and {@link
 * FallingEdge} only on {@code ONE -> ZERO} — a transition through {@code X} or {@code Z}
 * (e.g. {@code ZERO -> X -> ONE}, observed as two separate delta-cycle events) never counts
 * as either, matching how a real logic analyzer's edge trigger behaves. {@link AnyEdge} is
 * the four-valued-aware counterpart: it fires on any actual change of that bit, X/Z
 * transitions included.
 */
public sealed interface TriggerCondition {

    /** {@code true} if the transition from {@code previous} to {@code current} fires this. */
    boolean matches(LogicVector previous, LogicVector current);

    /** A literal {@code ZERO -> ONE} on one bit; a transition through X or Z never matches. */
    record RisingEdge(int bitIndex) implements TriggerCondition {

        public RisingEdge {
            requireNonNegative(bitIndex);
        }

        @Override
        public boolean matches(LogicVector previous, LogicVector current) {
            return previous.getBit(bitIndex) == LogicState.ZERO
                    && current.getBit(bitIndex) == LogicState.ONE;
        }
    }

    /** A literal {@code ONE -> ZERO} on one bit; a transition through X or Z never matches. */
    record FallingEdge(int bitIndex) implements TriggerCondition {

        public FallingEdge {
            requireNonNegative(bitIndex);
        }

        @Override
        public boolean matches(LogicVector previous, LogicVector current) {
            return previous.getBit(bitIndex) == LogicState.ONE
                    && current.getBit(bitIndex) == LogicState.ZERO;
        }
    }

    /** Any actual change of one bit, including a transition into or out of X or Z. */
    record AnyEdge(int bitIndex) implements TriggerCondition {

        public AnyEdge {
            requireNonNegative(bitIndex);
        }

        @Override
        public boolean matches(LogicVector previous, LogicVector current) {
            return previous.getBit(bitIndex) != current.getBit(bitIndex);
        }
    }

    /**
     * Fires the instant the whole watched value becomes exactly {@code target} — four-valued
     * equality, so a {@code target} bit of X or Z only matches that exact state, never a
     * wildcard.
     */
    record ValueEquals(LogicVector target) implements TriggerCondition {

        public ValueEquals {
            Objects.requireNonNull(target, "target");
        }

        @Override
        public boolean matches(LogicVector previous, LogicVector current) {
            return current.equals(target);
        }
    }

    /** Fires the instant the whole watched value stops being exactly {@code target}. */
    record ValueNotEquals(LogicVector target) implements TriggerCondition {

        public ValueNotEquals {
            Objects.requireNonNull(target, "target");
        }

        @Override
        public boolean matches(LogicVector previous, LogicVector current) {
            return !current.equals(target);
        }
    }

    private static void requireNonNegative(int bitIndex) {
        if (bitIndex < 0) {
            throw new IllegalArgumentException("bitIndex must not be negative: " + bitIndex);
        }
    }
}
