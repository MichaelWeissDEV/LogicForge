package dev.logicforge.analyzer;

import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.Simulation;
import dev.logicforge.simulation.SimulationObserver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Watches a chosen set of nets in a {@link Simulation} and records their history as
 * {@link SignalTrace}s — the headless half of the logic analyzer. It hooks into the
 * simulation purely as a {@link SimulationObserver}: it never touches simulation state,
 * so watching a signal has no effect on the circuit it is attached to.
 *
 * <p>Several nets can be watched at once, in the order they were added, which is the order
 * a waveform view should list them in.
 */
public final class SignalRecorder implements SimulationObserver {

    private final Simulation simulation;
    private final Map<Integer, SignalTrace> traces = new LinkedHashMap<>();
    private boolean capturing = true;

    public SignalRecorder(Simulation simulation) {
        this.simulation = simulation;
        simulation.addObserver(this);
    }

    /** The simulation this recorder is attached to. */
    public Simulation simulation() {
        return simulation;
    }

    /**
     * Starts watching {@code netId}, seeding its trace with the value the net holds right
     * now. Watching a net that is already watched just returns its existing trace.
     */
    public SignalTrace watch(int netId, String label) {
        SignalTrace existing = traces.get(netId);
        if (existing != null) {
            return existing;
        }
        SignalTrace trace = new SignalTrace(netId, label, simulation.circuit().net(netId).width());
        trace.record(simulation.time(), simulation.deltaCycle(), simulation.readNet(netId));
        traces.put(netId, trace);
        return trace;
    }

    /** Stops watching a net; its recorded history is discarded. */
    public void unwatch(int netId) {
        traces.remove(netId);
    }

    public boolean isWatching(int netId) {
        return traces.containsKey(netId);
    }

    public Optional<SignalTrace> trace(int netId) {
        return Optional.ofNullable(traces.get(netId));
    }

    /** Every watched trace, in the order the nets were added. */
    public List<SignalTrace> traces() {
        return List.copyOf(traces.values());
    }

    /**
     * Erases every trace's recorded history and re-seeds it with the net's current value,
     * without changing which nets are watched.
     */
    public void clear() {
        for (SignalTrace trace : traces.values()) {
            trace.clear();
            trace.record(simulation.time(), simulation.deltaCycle(), simulation.readNet(trace.netId()));
        }
    }

    /**
     * Starts or stops capturing. While stopped, net changes are not recorded. Resuming
     * re-seeds every trace with the net's current value, so a gap in capture never leaves
     * a trace claiming a value the net no longer holds.
     */
    public void setCapturing(boolean capturing) {
        boolean resuming = capturing && !this.capturing;
        this.capturing = capturing;
        if (resuming) {
            for (SignalTrace trace : traces.values()) {
                trace.record(simulation.time(), simulation.deltaCycle(), simulation.readNet(trace.netId()));
            }
        }
    }

    public boolean isCapturing() {
        return capturing;
    }

    /** Detaches from the simulation; this recorder receives no further updates. */
    public void detach() {
        simulation.removeObserver(this);
    }

    @Override
    public void onNetChanged(int netId, LogicVector previous, LogicVector current, long time, int deltaCycle) {
        if (!capturing) {
            return;
        }
        SignalTrace trace = traces.get(netId);
        if (trace != null) {
            trace.record(time, deltaCycle, current);
        }
    }

    @Override
    public void onReset() {
        for (SignalTrace trace : traces.values()) {
            trace.clear();
            trace.record(simulation.time(), simulation.deltaCycle(), simulation.readNet(trace.netId()));
        }
    }
}
