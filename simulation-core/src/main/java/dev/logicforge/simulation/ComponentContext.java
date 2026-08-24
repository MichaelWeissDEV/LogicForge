package dev.logicforge.simulation;

import dev.logicforge.logic.LogicVector;

/**
 * What a component behaviour sees while it is being evaluated: its input values, a way to
 * drive its outputs, and its own state.
 *
 * <p>Behaviours never reach for another component. All communication goes through nets,
 * which is what makes the simulator's evaluation order deterministic and lets nets have
 * several drivers.
 */
public interface ComponentContext {

    int inputCount();

    /** The current value on the net attached to input port {@code index}. */
    LogicVector readInput(int index);

    int outputCount();

    /**
     * Drives output port {@code index}. The new value takes effect in the next delta
     * cycle; driving the value the port already has costs nothing.
     */
    void driveOutput(int index, LogicVector value);

    /** This component's state object, or {@link ComponentRuntimeState#STATELESS}. */
    ComponentRuntimeState state();

    /** Current simulation time. Version 0.1 evaluates everything at time 0. */
    long time();

    int deltaCycle();
}
