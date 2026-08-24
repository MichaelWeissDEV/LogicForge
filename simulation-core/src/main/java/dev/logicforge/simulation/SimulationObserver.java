package dev.logicforge.simulation;

import dev.logicforge.logic.LogicVector;

/**
 * Watches signal changes. Every value change in the circuit passes through here, which is
 * what a logic analyser, a signal trace or a breakpoint will later hook into.
 *
 * <p>Observers must not modify the simulation.
 */
public interface SimulationObserver {

    void onNetChanged(int netId, LogicVector previous, LogicVector current, long time, int deltaCycle);

    /** Called after the simulation was reset to its initial state. */
    default void onReset() {
    }
}
