package dev.logicforge.simulation;

/**
 * One pending item on the simulation's event queue: either a driver value change
 * ({@link SimulationEvent}) or a component's request to be evaluated again at a future
 * time without any of its inputs changing ({@link WakeupEvent}).
 *
 * <p>Entries are ordered by {@code time}, then {@code deltaCycle}, then {@code sequence}.
 * The sequence number makes the order total, so a circuit always produces exactly the same
 * trace — there is no dependence on hash order or on threads anywhere in the simulator.
 */
public sealed interface TimelineEntry extends Comparable<TimelineEntry>
        permits SimulationEvent, WakeupEvent {

    /** Physical simulation time, in picoseconds, at which this entry takes effect. */
    long time();

    /** Ordering within {@link #time()}: how many delta cycles have already elapsed at it. */
    int deltaCycle();

    /** Strictly increasing counter that makes the ordering total. */
    long sequence();

    @Override
    default int compareTo(TimelineEntry other) {
        int byTime = Long.compare(time(), other.time());
        if (byTime != 0) {
            return byTime;
        }
        int byDelta = Integer.compare(deltaCycle(), other.deltaCycle());
        if (byDelta != 0) {
            return byDelta;
        }
        return Long.compare(sequence(), other.sequence());
    }
}
