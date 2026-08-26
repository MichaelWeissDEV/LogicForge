package dev.logicforge.simulation;

/**
 * A read-only, point-in-time snapshot of a {@link Simulation}'s cumulative activity counters.
 * Purely observational: nothing in {@link Simulation} branches on these values, so taking or
 * ignoring a snapshot never changes simulation behavior.
 *
 * <p>Every counter accumulates since the simulation was constructed or last {@link
 * Simulation#reset() reset}. Intended for gauging the cost of gate/transistor-level circuits
 * before optimizing anything — see {@link Simulation#metrics()}.
 *
 * @param eventsProcessed total {@link SimulationEvent}/{@link WakeupEvent} entries dequeued
 *     and applied
 * @param componentEvaluations total {@code ComponentBehavior.evaluate} calls
 * @param netTransitions total nets whose resolved value actually changed (re-resolving to the
 *     same value does not count)
 * @param deltaCycles total delta cycles processed, cumulative across every timestamp
 * @param scheduledWakeups total distinct wakeups actually queued (a duplicate wakeup request
 *     for a time a component already has one pending does not count)
 * @param currentVirtualTime the simulation's current time, in picoseconds
 * @param maxDeltaDepth the highest number of delta cycles any single timestamp has needed to
 *     settle so far — the metric that would have made the {@code deltaCyclesAtCurrentTime}
 *     reset bug visible immediately, since it climbs monotonically on a circuit that merely
 *     runs long rather than one that is actually oscillating
 */
public record SimulationMetrics(
        long eventsProcessed,
        long componentEvaluations,
        long netTransitions,
        long deltaCycles,
        long scheduledWakeups,
        long currentVirtualTime,
        long maxDeltaDepth) {
}
