package dev.logicforge.library.behavior;

import static dev.logicforge.logic.LogicState.ONE;
import static dev.logicforge.logic.LogicState.UNKNOWN;
import static dev.logicforge.logic.LogicState.ZERO;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;

/**
 * A level-sensitive SR latch, modelled functionally rather than as two cross-coupled
 * gates: an explicit state bit plus a truth table, since a behaviour evaluates once per
 * call and cannot iterate an internal feedback loop to a fixed point itself.
 *
 * <pre>
 *   S R | Q+
 *   0 0 | hold
 *   1 0 | 1
 *   0 1 | 0
 *   1 1 | undefined (X) -- both NOR and NAND latches misbehave here
 * </pre>
 *
 * @param activeLow {@code true} for the NAND-based latch, whose S/R inputs are asserted
 *                  at 0 rather than 1
 */
public record SrLatchBehavior(boolean activeLow) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        LogicState set = level(context.readInput(0).singleBit());
        LogicState reset = level(context.readInput(1).singleBit());
        LatchState state = (LatchState) context.state();

        LogicState q;
        if (set == ONE && reset == ONE) {
            q = UNKNOWN;
        } else if (set == ONE) {
            q = ONE;
        } else if (reset == ONE) {
            q = ZERO;
        } else if (set == ZERO && reset == ZERO) {
            q = state.q;
        } else {
            q = UNKNOWN;
        }
        state.q = q;
        context.driveOutput(0, LogicVector.single(q));
        context.driveOutput(1, LogicVector.single(LogicOperations.not(q)));
    }

    private LogicState level(LogicState raw) {
        LogicState defined = LogicOperations.asGateInput(raw);
        return activeLow ? LogicOperations.not(defined) : defined;
    }

    @Override
    public ComponentRuntimeState createState() {
        return new LatchState();
    }
}
