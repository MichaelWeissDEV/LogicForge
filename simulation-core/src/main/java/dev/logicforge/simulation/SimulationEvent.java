package dev.logicforge.simulation;

import dev.logicforge.logic.LogicVector;

/**
 * A pending change of one driver's value.
 *
 * <p>Combinational logic schedules these at the current time, one delta cycle ahead;
 * {@link ComponentContext#driveOutputAfter} and {@link ComponentContext#driveOutputAt}
 * schedule them at a future physical time instead.
 */
public record SimulationEvent(
        long time,
        int deltaCycle,
        long sequence,
        int netId,
        int driverId,
        LogicVector value) implements TimelineEntry {
}
