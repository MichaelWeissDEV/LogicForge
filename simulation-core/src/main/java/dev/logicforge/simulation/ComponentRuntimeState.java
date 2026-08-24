package dev.logicforge.simulation;

/**
 * Mutable state a component keeps between evaluations.
 *
 * <p>Purely combinational gates have none; a toggle switch remembers what the user set,
 * and later registers, counters and memories will keep their contents here. The simulator
 * owns these objects so that a reset can restore a well defined initial state.
 */
@FunctionalInterface
public interface ComponentRuntimeState {

    /** Shared instance for components whose output only depends on their inputs. */
    ComponentRuntimeState STATELESS = () -> {
    };

    /** Restores the power-on state. */
    void reset();
}
