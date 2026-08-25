package dev.logicforge.simulation;

/**
 * A component's request to be evaluated again at a future time even though none of its
 * inputs changed — what a clock generator uses to schedule its own next edge.
 *
 * @see ComponentContext#scheduleWakeup(long)
 */
public record WakeupEvent(long time, int deltaCycle, long sequence, int componentId) implements TimelineEntry {
}
