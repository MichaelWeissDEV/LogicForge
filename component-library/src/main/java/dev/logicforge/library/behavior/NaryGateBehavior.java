package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicOperation;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/**
 * The multi-input gates. AND/NAND, OR/NOR and XOR/XNOR differ only in the base operation
 * and whether the result is inverted, so they share this one implementation — while
 * staying six separate definitions with their own stable ids in saved projects.
 *
 * <p>The number of inputs is a parameter of the instance; the behaviour simply folds over
 * however many inputs the compiled component has.
 */
public record NaryGateBehavior(LogicOperation operation, boolean invertOutput) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        LogicState result = operation.identity();
        for (int i = 0; i < context.inputCount(); i++) {
            result = operation.apply(result, context.readInput(i).singleBit());
        }
        context.driveOutput(0, LogicVector.single(invertOutput ? LogicOperations.not(result) : result));
    }
}
