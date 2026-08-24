package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/**
 * A tri-state buffer — the one component that may put a net into {@code Z}.
 *
 * <pre>
 *   ENABLE active   -&gt; the output follows A (inverted for the inverting variant)
 *   ENABLE inactive -&gt; the output floats (Z), leaving the net to other drivers
 *   ENABLE unknown  -&gt; the output is X: it is not known whether the driver is on
 * </pre>
 *
 * @param invertOutput inverting variant
 * @param activeLow    {@code true} if the buffer drives while ENABLE is 0
 */
public record TriStateBehavior(boolean invertOutput, boolean activeLow) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        LogicState enable = LogicOperations.asGateInput(context.readInput(1).singleBit());
        LogicState activeLevel = activeLow ? LogicState.ZERO : LogicState.ONE;
        LogicState result;
        if (enable == activeLevel) {
            LogicState input = context.readInput(0).singleBit();
            result = invertOutput ? LogicOperations.not(input) : LogicOperations.asGateInput(input);
        } else if (enable == LogicState.UNKNOWN) {
            result = LogicState.UNKNOWN;
        } else {
            result = LogicState.HIGH_IMPEDANCE;
        }
        context.driveOutput(0, LogicVector.single(result));
    }
}
