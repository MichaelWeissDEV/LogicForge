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
     * cycle at the current time; driving the value the port already has costs nothing.
     */
    void driveOutput(int index, LogicVector value);

    /**
     * Drives output port {@code index} with a value that takes effect {@code delay}
     * picoseconds from now. {@code delay} must be positive.
     */
    void driveOutputAfter(int index, long delay, LogicVector value);

    /**
     * Drives output port {@code index} with a value that takes effect at the given
     * absolute simulation time, which must be strictly after {@link #time()}.
     */
    void driveOutputAt(int index, long time, LogicVector value);

    /**
     * Requests that this component be evaluated again at {@code time}, even if none of
     * its inputs change before then. {@code time} must be strictly after {@link #time()}.
     * This is how a component like a clock generator keeps itself running: it drives its
     * current level and schedules a wakeup for its own next edge.
     */
    void scheduleWakeup(long time);

    /** This component's state object, or {@link ComponentRuntimeState#STATELESS}. */
    ComponentRuntimeState state();

    /** Current simulation time, in picoseconds. */
    long time();

    int deltaCycle();
}
