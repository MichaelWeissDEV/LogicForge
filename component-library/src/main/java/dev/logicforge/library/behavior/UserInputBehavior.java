package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;

/**
 * A component the user drives directly: a toggle switch or a push button.
 *
 * @param initialValue what the component outputs after a reset
 * @param inverted     inverts the output, so a button can be active low
 */
public record UserInputBehavior(LogicState initialValue, boolean inverted) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        LogicVector value = ((UserInputState) context.state()).value();
        context.driveOutput(0, inverted
                ? LogicVector.single(LogicOperations.not(value.singleBit()))
                : value);
    }

    @Override
    public ComponentRuntimeState createState() {
        return new UserInputState(initialValue);
    }
}
