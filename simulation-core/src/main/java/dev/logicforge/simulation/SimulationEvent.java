package dev.logicforge.simulation;

import dev.logicforge.logic.LogicVector;

/**
 * A pending change of one driver's value.
 *
 * <p>Events are ordered by {@code time}, then {@code deltaCycle}, then {@code sequence}.
 * The sequence number makes the order total, so a circuit always produces exactly the same
 * trace — there is no dependence on hash order or on threads anywhere in the simulator.
 *
 * <p>Version 0.1 evaluates combinational logic, so every event happens at {@code time 0}
 * and only the delta cycle advances. The time field is already here because propagation
 * delays, clocks and sequential components will use it.
 */
public record SimulationEvent(
        long time,
        int deltaCycle,
        long sequence,
        int netId,
        int driverId,
        LogicVector value) implements Comparable<SimulationEvent> {

    @Override
    public int compareTo(SimulationEvent other) {
        int byTime = Long.compare(time, other.time);
        if (byTime != 0) {
            return byTime;
        }
        int byDelta = Integer.compare(deltaCycle, other.deltaCycle);
        if (byDelta != 0) {
            return byDelta;
        }
        return Long.compare(sequence, other.sequence);
    }
}
