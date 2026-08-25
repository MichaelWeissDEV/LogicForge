package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/**
 * An N-bit tri-state buffer: the bus-width version of {@code logic.tristate}. Drives A onto
 * Y while ENABLE is active; otherwise every bit floats (Z).
 *
 * <p>Ports are {@code A, ENABLE} in, {@code Y} out.
 *
 * @param width the bus width of A and Y
 * @param activeLow {@code true} if this buffer drives while ENABLE is 0
 */
public record WideTriStateBehavior(BitWidth width, boolean activeLow) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        LogicState enable = LogicOperations.asGateInput(context.readInput(1).singleBit());
        LogicState activeLevel = activeLow ? LogicState.ZERO : LogicState.ONE;
        LogicVector result;
        if (enable == activeLevel) {
            result = LogicOperations.asGateInput(context.readInput(0));
        } else if (enable == LogicState.UNKNOWN) {
            result = LogicVector.repeat(LogicState.UNKNOWN, width);
        } else {
            result = LogicVector.repeat(LogicState.HIGH_IMPEDANCE, width);
        }
        context.driveOutput(0, result);
    }
}
