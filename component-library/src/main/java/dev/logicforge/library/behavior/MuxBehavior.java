package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import java.util.OptionalLong;

/**
 * An N:1 multiplexer: drives the data input SEL selects onto OUT. Ports are
 * {@code IN0..IN(n-1), SEL} in that order, so {@code SEL} is read at input index
 * {@code inputCount}. An undefined SEL, or a SEL value with no corresponding input (e.g.
 * inputCount is not a power of two), drives OUT to X rather than guessing.
 *
 * @param dataWidth the bus width of every data input and of OUT
 * @param inputCount how many data inputs this instance has
 */
public record MuxBehavior(BitWidth dataWidth, int inputCount) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        LogicVector selector = context.readInput(inputCount);
        OptionalLong selected = selector.toUnsignedLong();
        if (selected.isEmpty() || selected.getAsLong() >= inputCount) {
            context.driveOutput(0, LogicVector.repeat(dev.logicforge.logic.LogicState.UNKNOWN, dataWidth));
            return;
        }
        context.driveOutput(0, LogicOperations.asGateInput(context.readInput((int) selected.getAsLong())));
    }

    /** Bits needed to select among {@code inputCount} inputs, at least 1. */
    public static int selectWidth(int inputCount) {
        return Math.max(1, 32 - Integer.numberOfLeadingZeros(Math.max(1, inputCount - 1)));
    }
}
