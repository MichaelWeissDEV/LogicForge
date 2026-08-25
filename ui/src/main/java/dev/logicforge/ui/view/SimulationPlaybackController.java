package dev.logicforge.ui.view;

import dev.logicforge.ui.edit.CircuitEditor;
import javafx.animation.AnimationTimer;

/**
 * Turns wall-clock time into virtual simulation time while "Run" is active — the one place
 * real time is allowed to matter. It never decides what a clock does; {@code ClockBehavior}
 * is already fully virtual-time driven. This only decides how far to call
 * {@link CircuitEditor#advancePlayback} each frame, at a rate the user picks, so a clock
 * that is free-running is actually visible running instead of sitting at whatever virtual
 * time the last discrete edit left it at.
 *
 * <p>Uses {@link AnimationTimer} purely as a UI frame pump — not as a source of hardware
 * timing. Every frame is bounded to a fixed number of simulation advances
 * ({@link #MAX_ADVANCES_PER_FRAME}), so a fast clock with a huge virtual horizon can never
 * freeze the interface; it simply catches up over more frames.
 */
public final class SimulationPlaybackController {

    /** Caps how much of a fast clock's backlog one frame is allowed to process. */
    private static final int MAX_ADVANCES_PER_FRAME = 20_000;

    /** Virtual picoseconds per wall nanosecond at 1x — 1 real second is 1 virtual second. */
    private static final double REALTIME_PS_PER_WALL_NS = 1_000.0;

    /** Playback rate presets. MAX ignores wall-clock proportionality entirely. */
    public enum Speed {
        SLOW(0.1),
        REALTIME(1.0),
        FAST(10.0),
        MAX(Double.POSITIVE_INFINITY);

        private final double multiplier;

        Speed(double multiplier) {
            this.multiplier = multiplier;
        }

        @Override
        public String toString() {
            return switch (this) {
                case SLOW -> "0.1x";
                case REALTIME -> "1x";
                case FAST -> "10x";
                case MAX -> "Max";
            };
        }
    }

    private final CircuitEditor editor;
    private final AnimationTimer timer;
    private Speed speed = Speed.REALTIME;
    private long lastFrameNanos = -1;

    /**
     * @param editor advancing playback goes through {@link CircuitEditor#advancePlayback},
     *               which already notifies every change listener (canvas, status bar,
     *               analyzer) — nothing further is needed to make a frame visible
     */
    public SimulationPlaybackController(CircuitEditor editor) {
        this.editor = editor;
        this.timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                tick(now);
            }
        };
    }

    public void setSpeed(Speed speed) {
        this.speed = speed;
    }

    public Speed speed() {
        return speed;
    }

    public void start() {
        lastFrameNanos = -1;
        timer.start();
    }

    public void stop() {
        timer.stop();
    }

    private void tick(long nowNanos) {
        if (lastFrameNanos < 0) {
            // First frame after starting: nothing elapsed yet to convert into virtual time.
            lastFrameNanos = nowNanos;
            return;
        }
        long elapsedWallNanos = nowNanos - lastFrameNanos;
        lastFrameNanos = nowNanos;
        if (elapsedWallNanos <= 0) {
            return;
        }

        long targetTime = speed.multiplier == Double.POSITIVE_INFINITY
                ? Long.MAX_VALUE
                : editor.currentTime() + (long) (elapsedWallNanos * REALTIME_PS_PER_WALL_NS * speed.multiplier);
        editor.advancePlayback(targetTime, MAX_ADVANCES_PER_FRAME);
    }
}
