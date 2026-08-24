package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.InputSourceState;

/**
 * What a switch or button currently puts out, and what it returns to on reset.
 *
 * <p>The current value is simulation state: flipping a switch changes the circuit's
 * behaviour but not the saved project. The reset value comes from a component parameter
 * and therefore is part of the project.
 */
public final class UserInputState implements InputSourceState {

    private final LogicState initialValue;
    private LogicVector value;

    public UserInputState(LogicState initialValue) {
        this.initialValue = initialValue;
        this.value = LogicVector.single(initialValue);
    }

    @Override
    public LogicVector value() {
        return value;
    }

    @Override
    public void setValue(LogicVector newValue) {
        this.value = newValue.requireWidth(1);
    }

    @Override
    public void reset() {
        this.value = LogicVector.single(initialValue);
    }

    public LogicState initialValue() {
        return initialValue;
    }
}
