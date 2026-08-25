package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import java.util.OptionalLong;

/**
 * Adds or subtracts one, wrapping at the width's range: OUT = A + 1 (incrementer) or
 * A - 1 (decrementer). Ports are {@code A} in, {@code OUT} out.
 *
 * @param width the bus width of A and OUT
 * @param increment {@code true} to add one, {@code false} to subtract one
 */
public record IncrementDecrementBehavior(BitWidth width, boolean increment) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        OptionalLong a = context.readInput(0).toUnsignedLong();
        if (a.isEmpty()) {
            context.driveOutput(0, LogicVector.repeat(LogicState.UNKNOWN, width));
            return;
        }
        long mask = width.bits() == 64 ? -1L : (1L << width.bits()) - 1;
        long result = (increment ? a.getAsLong() + 1 : a.getAsLong() - 1) & mask;
        context.driveOutput(0, LogicVector.fromUnsignedLong(result, width.bits()));
    }
}
