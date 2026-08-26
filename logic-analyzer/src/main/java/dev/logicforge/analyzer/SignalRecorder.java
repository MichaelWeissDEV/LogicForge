package dev.logicforge.analyzer;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.Simulation;
import dev.logicforge.simulation.SimulationObserver;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Watches a chosen set of signals in a {@link Simulation} and records their history as
 * {@link SignalTrace}s — the headless half of the logic analyzer. It hooks into the
 * simulation purely as a {@link SimulationObserver}: it never touches simulation state,
 * so watching a signal has no effect on the circuit it is attached to.
 *
 * <p>A watched signal is an {@link AnalyzerSignalBinding}: usually one net, but a range or
 * whole read of a bit-mode port can span several independent ones (see {@code
 * CircuitCompiler}'s per-port {@code bitMode} tracking — a port is either entirely one net
 * or entirely bit-mode, never mixed). Whichever underlying net changes, the binding's full
 * logical value is recomputed and recorded, so a multi-net signal's trace is exactly as
 * accurate as a single-net one. Several signals can be watched at once, in the order they
 * were added, which is the order a waveform view should list them in.
 */
public final class SignalRecorder implements SimulationObserver {

    private final Simulation simulation;
    private final Map<AnalyzerSignalBinding, SignalTrace> traces = new LinkedHashMap<>();
    private final Map<Integer, Set<AnalyzerSignalBinding>> watchersByNet = new LinkedHashMap<>();
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
     * Starts watching a whole net, seeding its trace with the value it holds right now.
     * Watching a net that is already watched just returns its existing trace. Equivalent to
     * {@code watch(AnalyzerSignalBinding.Vector.whole(netId, ...), label)}.
     */
    public SignalTrace watch(int netId, String label) {
        return watch(wholeNet(netId), label);
    }

    /**
     * Starts watching an arbitrary binding — one net, a slice of one net, or several
     * independent bit nets reconstructed together — seeding its trace with the value it
     * holds right now. Watching a binding that is already watched (by structural equality,
     * e.g. the same net and slice) just returns its existing trace.
     */
    public SignalTrace watch(AnalyzerSignalBinding binding, String label) {
        SignalTrace existing = traces.get(binding);
        if (existing != null) {
            return existing;
        }
        SignalTrace trace = new SignalTrace(binding, label, BitWidth.of(binding.width()));
        trace.record(simulation.time(), simulation.deltaCycle(), binding.read(simulation));
        traces.put(binding, trace);
        for (int netId : binding.netIds()) {
            watchersByNet.computeIfAbsent(netId, ignored -> new LinkedHashSet<>()).add(binding);
        }
        return trace;
    }

    /** Stops watching a net; its recorded history is discarded. */
    public void unwatch(int netId) {
        unwatch(wholeNet(netId));
    }

    /** Stops watching a binding; its recorded history is discarded. */
    public void unwatch(AnalyzerSignalBinding binding) {
        if (traces.remove(binding) == null) {
            return;
        }
        for (int netId : binding.netIds()) {
            Set<AnalyzerSignalBinding> watchers = watchersByNet.get(netId);
            if (watchers != null) {
                watchers.remove(binding);
                if (watchers.isEmpty()) {
                    watchersByNet.remove(netId);
                }
            }
        }
    }

    public boolean isWatching(int netId) {
        return isWatching(wholeNet(netId));
    }

    public boolean isWatching(AnalyzerSignalBinding binding) {
        return traces.containsKey(binding);
    }

    public Optional<SignalTrace> trace(int netId) {
        return trace(wholeNet(netId));
    }

    public Optional<SignalTrace> trace(AnalyzerSignalBinding binding) {
        return Optional.ofNullable(traces.get(binding));
    }

    /** Every watched trace, in the order the signals were added. */
    public List<SignalTrace> traces() {
        return List.copyOf(traces.values());
    }

    /**
     * Erases every trace's recorded history and re-seeds it with its binding's current
     * value, without changing which signals are watched.
     */
    public void clear() {
        for (Map.Entry<AnalyzerSignalBinding, SignalTrace> entry : traces.entrySet()) {
            SignalTrace trace = entry.getValue();
            trace.clear();
            trace.record(simulation.time(), simulation.deltaCycle(), entry.getKey().read(simulation));
        }
    }

    /**
     * Starts or stops capturing. While stopped, net changes are not recorded. Resuming
     * re-seeds every trace with its binding's current value, so a gap in capture never
     * leaves a trace claiming a value the signal no longer holds.
     */
    public void setCapturing(boolean capturing) {
        boolean resuming = capturing && !this.capturing;
        this.capturing = capturing;
        if (resuming) {
            for (Map.Entry<AnalyzerSignalBinding, SignalTrace> entry : traces.entrySet()) {
                entry.getValue().record(simulation.time(), simulation.deltaCycle(),
                        entry.getKey().read(simulation));
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

    private AnalyzerSignalBinding.Vector wholeNet(int netId) {
        return AnalyzerSignalBinding.Vector.whole(netId, simulation.circuit().net(netId).width().bits());
    }

    @Override
    public void onNetChanged(int netId, LogicVector previous, LogicVector current, long time, int deltaCycle) {
        if (!capturing) {
            return;
        }
        Set<AnalyzerSignalBinding> dependents = watchersByNet.get(netId);
        if (dependents == null) {
            return;
        }
        for (AnalyzerSignalBinding binding : List.copyOf(dependents)) {
            SignalTrace trace = traces.get(binding);
            if (trace != null) {
                trace.record(time, deltaCycle, binding.read(simulation));
            }
        }
    }

    @Override
    public void onReset() {
        for (Map.Entry<AnalyzerSignalBinding, SignalTrace> entry : traces.entrySet()) {
            SignalTrace trace = entry.getValue();
            trace.clear();
            trace.record(simulation.time(), simulation.deltaCycle(), entry.getKey().read(simulation));
        }
    }
}
