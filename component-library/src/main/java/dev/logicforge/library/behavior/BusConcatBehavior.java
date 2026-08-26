package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/** Concatenates HIGH and LOW, with LOW occupying output bit zero upward. */
public record BusConcatBehavior(BitWidth lowWidth, BitWidth highWidth) implements ComponentBehavior {

    public BusConcatBehavior {
        BitWidth.of(lowWidth.bits() + highWidth.bits());
    }

    @Override
    public void evaluate(ComponentContext context) {
        LogicVector low = context.readInput(0).requireWidth(lowWidth);
        LogicVector high = context.readInput(1).requireWidth(highWidth);
        context.driveOutput(0, high.concat(low));
    }
}
