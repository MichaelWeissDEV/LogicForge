package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/** Extracts a contiguous LSB-relative slice from a bus. */
public record BusSliceBehavior(BitWidth inputWidth, int lsb, BitWidth outputWidth)
        implements ComponentBehavior {

    public BusSliceBehavior {
        if (lsb < 0 || lsb + outputWidth.bits() > inputWidth.bits()) {
            throw new IllegalArgumentException("Slice must fit inside its input width");
        }
    }

    @Override
    public void evaluate(ComponentContext context) {
        context.driveOutput(0, context.readInput(0).requireWidth(inputWidth)
                .slice(lsb, outputWidth.bits()));
    }
}
