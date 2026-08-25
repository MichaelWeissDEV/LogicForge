package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;
import dev.logicforge.simulation.SimulationTime;

/**
 * A free-running square wave generator, driven entirely by virtual simulation time.
 *
 * <p>On every evaluation it checks whether the current time has reached its next
 * scheduled edge; if so it toggles and computes the next one, then always drives its
 * current level and reschedules its own wakeup. Since it never reads an input, evaluate()
 * is only ever called at reset and by its own wakeups, so there is exactly one mechanism
 * driving it — no risk of two evaluations at the same instant disagreeing about whether to
 * toggle, since after toggling {@code nextEdgeAt} is always strictly after {@code now}.
 *
 * <p>When {@code enabled} is false the clock behaves like a constant source at its initial
 * level: it never schedules a wakeup, so it never oscillates.
 *
 * @param periodPs total period in picoseconds
 * @param dutyPercent percentage (1..99) of the period spent at the high level
 * @param initiallyHigh the level the clock starts at after reset
 * @param enabled whether the clock actually runs
 */
public record ClockBehavior(long periodPs, int dutyPercent, boolean initiallyHigh, boolean enabled)
        implements ComponentBehavior {

    public ClockBehavior {
        if (periodPs < 2) {
            throw new IllegalArgumentException("periodPs must be at least 2, was " + periodPs);
        }
        if (dutyPercent < 1 || dutyPercent > 99) {
            throw new IllegalArgumentException("dutyPercent must be in 1..99, was " + dutyPercent);
        }
    }

    /** Convenience: build from a frequency in Hz instead of a raw period. */
    public static ClockBehavior ofFrequency(long frequencyHz, int dutyPercent, boolean initiallyHigh,
                                            boolean enabled) {
        long periodPs = Math.max(2, SimulationTime.PICOSECONDS_PER_SECOND / Math.max(1, frequencyHz));
        return new ClockBehavior(periodPs, dutyPercent, initiallyHigh, enabled);
    }

    @Override
    public void evaluate(ComponentContext context) {
        LogicState initialLevel = LogicState.of(initiallyHigh);
        if (!enabled) {
            context.driveOutput(0, LogicVector.single(initialLevel));
            return;
        }
        ClockState state = (ClockState) context.state();
        long now = context.time();
        if (now >= state.nextEdgeAt) {
            state.level = state.level == LogicState.ONE ? LogicState.ZERO : LogicState.ONE;
            state.nextEdgeAt = now + phaseLength(state.level);
        }
        context.driveOutput(0, LogicVector.single(state.level));
        context.scheduleWakeup(state.nextEdgeAt);
    }

    @Override
    public ComponentRuntimeState createState() {
        LogicState initialLevel = LogicState.of(initiallyHigh);
        return new ClockState(initialLevel, phaseLength(initialLevel));
    }

    /** How long the clock stays at {@code level} before its next edge, at least 1 ps. */
    private long phaseLength(LogicState level) {
        long highLength = Math.max(1, periodPs * dutyPercent / 100);
        highLength = Math.min(highLength, periodPs - 1);
        long lowLength = periodPs - highLength;
        return level == LogicState.ONE ? highLength : lowLength;
    }

    private static final class ClockState implements ComponentRuntimeState {

        private final LogicState initialLevel;
        private final long initialPhaseLength;
        private LogicState level;
        private long nextEdgeAt;

        ClockState(LogicState initialLevel, long initialPhaseLength) {
            this.initialLevel = initialLevel;
            this.initialPhaseLength = initialPhaseLength;
            reset();
        }

        @Override
        public void reset() {
            level = initialLevel;
            nextEdgeAt = initialPhaseLength;
        }
    }
}
