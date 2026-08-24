package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/** Drives a fixed value: the {@code 0}, {@code 1}, {@code X} and {@code Z} sources. */
public record ConstantBehavior(LogicState value) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        context.driveOutput(0, LogicVector.single(value));
    }
}
