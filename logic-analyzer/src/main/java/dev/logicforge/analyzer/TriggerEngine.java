package dev.logicforge.analyzer;

import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.Simulation;
import dev.logicforge.simulation.SimulationObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Watches one {@link AnalyzerSignalBinding} for a {@link TriggerCondition} and reports when
 * it fires — the headless half of the analyzer's trigger, independent of any UI.
 *
 * <p>It observes the simulation directly, the same push-based, per-delta-cycle mechanism
 * {@link SignalRecorder} already uses (see {@code onNetChanged}) — never a polling sampler,
 * which would silently collapse several delta-cycle transitions at one physical time into
 * whatever value happened to be current when it last sampled. Reading exactly the events
 * the waveform is built from is what makes a trigger delta-cycle accurate: it can fire on a
 * transient glitch that settles back within the same time step.
 *
 * <p>This engine only detects and reports; it never touches simulation or run-control state
 * itself — see {@link #addFireListener(Runnable)}. The caller (typically the UI layer) is
 * responsible for wiring a listener to whatever should happen on fire, e.g. {@code
 * SimulationSession.setRunning(false)}. That keeps this class testable with a bare {@link
 * Simulation} and no session at all, and keeps {@code logic-analyzer} free of any UI
 * dependency.
 */
public final class TriggerEngine implements SimulationObserver {

    /** Where the trigger currently stands, mirroring a real instrument's status light. */
    public enum Status {
        /** No condition armed; {@link #onNetChanged} does nothing. */
        DISARMED,
        /** Watching for {@link #condition()} on {@link #binding()}. */
        ARMED,
        /** {@link #condition()} matched; {@link #triggerTime()} names when. */
        TRIGGERED
    }

    private final Simulation simulation;
    private final List<Runnable> fireListeners = new ArrayList<>();

    private AnalyzerSignalBinding binding;
    private TriggerCondition condition;
    private Status status = Status.DISARMED;
    private LogicVector lastValue;
    private long triggerTime = -1;
    private int triggerDeltaCycle = -1;

    public TriggerEngine(Simulation simulation) {
        this.simulation = Objects.requireNonNull(simulation, "simulation");
        simulation.addObserver(this);
    }

    /** Arms the trigger on a fresh binding/condition, replacing whatever was armed before. */
    public void arm(AnalyzerSignalBinding binding, TriggerCondition condition) {
        this.binding = Objects.requireNonNull(binding, "binding");
        this.condition = Objects.requireNonNull(condition, "condition");
        this.lastValue = binding.read(simulation);
        this.status = Status.ARMED;
        this.triggerTime = -1;
        this.triggerDeltaCycle = -1;
    }

    /**
     * Re-arms with the same binding and condition after a trigger fired — the "run again"
     * gesture on a real instrument. Re-seeds the tracked value first, so a value that
     * changed while the engine sat triggered does not read as a spurious edge.
     */
    public void rearm() {
        if (binding == null || condition == null) {
            throw new IllegalStateException("Nothing has been armed yet");
        }
        arm(binding, condition);
    }

    /** Stops watching; {@link #onNetChanged} becomes a no-op until armed again. */
    public void disarm() {
        this.status = Status.DISARMED;
        this.binding = null;
        this.condition = null;
        this.triggerTime = -1;
        this.triggerDeltaCycle = -1;
    }

    public Status status() {
        return status;
    }

    public Optional<AnalyzerSignalBinding> binding() {
        return Optional.ofNullable(binding);
    }

    public Optional<TriggerCondition> condition() {
        return Optional.ofNullable(condition);
    }

    /** The simulation time the trigger fired at, if it has. */
    public OptionalLong triggerTime() {
        return status == Status.TRIGGERED ? OptionalLong.of(triggerTime) : OptionalLong.empty();
    }

    /** The delta cycle within {@link #triggerTime()} the trigger fired at, if it has. */
    public OptionalInt triggerDeltaCycle() {
        return status == Status.TRIGGERED ? OptionalInt.of(triggerDeltaCycle) : OptionalInt.empty();
    }

    /** Notified once, on the JavaFX-free thread the simulation runs on, when the trigger fires. */
    public void addFireListener(Runnable listener) {
        fireListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeFireListener(Runnable listener) {
        fireListeners.remove(listener);
    }

    /** Detaches from the simulation; this engine receives no further updates. */
    public void detach() {
        simulation.removeObserver(this);
    }

    @Override
    public void onNetChanged(int netId, LogicVector previous, LogicVector current, long time, int deltaCycle) {
        if (status != Status.ARMED || !binding.netIds().contains(netId)) {
            return;
        }
        LogicVector before = lastValue;
        LogicVector after = binding.read(simulation);
        lastValue = after;
        if (condition.matches(before, after)) {
            status = Status.TRIGGERED;
            triggerTime = time;
            triggerDeltaCycle = deltaCycle;
            for (Runnable listener : List.copyOf(fireListeners)) {
                listener.run();
            }
        }
    }

    @Override
    public void onReset() {
        if (binding != null) {
            lastValue = binding.read(simulation);
        }
    }
}
