package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/** Drives a fixed, possibly multi-bit value — a Bus Constant. */
public record ConstantVectorBehavior(LogicVector value) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        context.driveOutput(0, value);
    }
}
