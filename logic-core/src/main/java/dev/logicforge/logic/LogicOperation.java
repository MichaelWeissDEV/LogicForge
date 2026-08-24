package dev.logicforge.logic;

/**
 * The associative, commutative base operations shared by the multi-input gates.
 *
 * <p>Inverting gates (NAND, NOR, XNOR) are built from these plus
 * {@link LogicOperations#not(LogicState)} rather than from tables of their own.
 */
public enum LogicOperation {

    AND(LogicState.ONE) {
        @Override
        public LogicState apply(LogicState a, LogicState b) {
            return LogicOperations.and(a, b);
        }
    },
    OR(LogicState.ZERO) {
        @Override
        public LogicState apply(LogicState a, LogicState b) {
            return LogicOperations.or(a, b);
        }
    },
    XOR(LogicState.ZERO) {
        @Override
        public LogicState apply(LogicState a, LogicState b) {
            return LogicOperations.xor(a, b);
        }
    };

    private final LogicState identity;

    LogicOperation(LogicState identity) {
        this.identity = identity;
    }

    public abstract LogicState apply(LogicState a, LogicState b);

    /** The neutral element of this operation. */
    public LogicState identity() {
        return identity;
    }
}
