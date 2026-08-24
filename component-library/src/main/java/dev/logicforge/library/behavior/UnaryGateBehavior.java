package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/**
 * Buffer and inverter. Both drive a defined level in every case — a floating input reads
 * as {@code X} and produces {@code X}, never {@code Z}.
 */
public record UnaryGateBehavior(boolean invert) implements ComponentBehavior {

    public static final UnaryGateBehavior BUFFER = new UnaryGateBehavior(false);
    public static final UnaryGateBehavior INVERTER = new UnaryGateBehavior(true);

    @Override
    public void evaluate(ComponentContext context) {
        LogicState input = context.readInput(0).singleBit();
        LogicState result = invert ? LogicOperations.not(input) : LogicOperations.asGateInput(input);
        context.driveOutput(0, LogicVector.single(result));
    }
}
