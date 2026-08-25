package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;

/**
 * ZERO = 1 iff every bit of A is 0; 0 if any bit is definitely 1; X only when no bit is
 * definitely 1 but at least one is unknown or floating — a zero flag cannot be partially
 * known.
 *
 * <p>Ports are {@code A} in, {@code ZERO} out.
 *
 * @param width the bus width of A
 */
public record ZeroDetectorBehavior(BitWidth width) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        LogicVector a = LogicOperations.asGateInput(context.readInput(0));
        context.driveOutput(0, zeroFlag(a));
    }

    /** Shared with {@link AluBehavior}, whose RESULT-is-zero flag follows the same rule. */
    static LogicVector zeroFlag(LogicVector value) {
        boolean hasUnknown = false;
        for (int i = 0; i < value.width(); i++) {
            LogicState bit = value.getBit(i);
            if (bit == LogicState.ONE) {
                return LogicVector.ZERO;
            }
            if (bit != LogicState.ZERO) {
                hasUnknown = true;
            }
        }
        return hasUnknown ? LogicVector.UNKNOWN : LogicVector.ONE;
    }

    @Override
    public ComponentRuntimeState createState() {
        return ComponentRuntimeState.STATELESS;
    }
}
