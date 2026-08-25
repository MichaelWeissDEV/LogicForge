package dev.logicforge.ui.edit;

import dev.logicforge.analyzer.SignalRecorder;
import dev.logicforge.analyzer.SignalTrace;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortSlice;
import dev.logicforge.simulation.Simulation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Bridges the editor to a {@link SignalRecorder}: which signals the user asked to watch,
 * kept as stable {@link PortReference}s, and the recorder currently attached to whatever
 * simulation the editor happens to have.
 *
 * <p>A structural edit gives the editor a brand new {@link Simulation} with renumbered
 * nets, so watched signals cannot be tracked by net id across recompiles. Re-resolving
 * each watched port through {@link CircuitEditor#netOf(PortReference)} on every editor
 * change is what keeps a watch list meaningful across edits — the same pattern the canvas
 * already uses to show live port values.
 */
public final class LogicAnalyzerController {

    /** A signal the user chose to watch, identified the way the rest of the editor does. */
    public record WatchedSignal(PortEndpoint reference, String label) {
    }

    private final CircuitEditor editor;
    private final List<WatchedSignal> watched = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    private Simulation attachedSimulation;
    private SignalRecorder recorder;

    public LogicAnalyzerController(CircuitEditor editor) {
        this.editor = editor;
        editor.addChangeListener(this::resync);
        resync();
    }

    public void addSignal(PortEndpoint reference, String label) {
        if (isWatching(reference)) {
            return;
        }
        watched.add(new WatchedSignal(reference, label));
        resync();
    }

    public void addSignal(PortReference reference, String label) {
        addSignal(PortEndpoint.whole(reference), label);
    }

    public void removeSignal(PortEndpoint reference) {
        if (watched.removeIf(signal -> signal.reference().equals(reference))) {
            resync();
        }
    }

    public boolean isWatching(PortEndpoint reference) {
        return watched.stream().anyMatch(signal -> signal.reference().equals(reference));
    }

    public List<WatchedSignal> watchedSignals() {
        return List.copyOf(watched);
    }

    public Optional<SignalTrace> traceFor(PortEndpoint reference) {
        if (recorder == null) {
            return Optional.empty();
        }
        java.util.OptionalInt netId = editor.netOf(reference);
        if (netId.isEmpty()) {
            return Optional.empty();
        }
        Optional<SignalTrace> trace = recorder.trace(netId.getAsInt());
        if (trace.isPresent() && reference.slice() instanceof PortSlice.Bit bit
                && trace.get().width().bits() > 1) {
            return Optional.of(trace.get().bit(bit.index()));
        }
        return trace;
    }

    public boolean isCapturing() {
        return recorder != null && recorder.isCapturing();
    }

    public void setCapturing(boolean capturing) {
        if (recorder != null) {
            recorder.setCapturing(capturing);
        }
        notifyListeners();
    }

    public void clear() {
        if (recorder != null) {
            recorder.clear();
        }
        notifyListeners();
    }

    public OptionalLong currentTime() {
        return attachedSimulation == null ? OptionalLong.empty() : OptionalLong.of(attachedSimulation.time());
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void resync() {
        Simulation current = editor.simulation().orElse(null);
        if (current != attachedSimulation) {
            if (recorder != null) {
                recorder.detach();
            }
            recorder = current == null ? null : new SignalRecorder(current);
            attachedSimulation = current;
        }
        if (recorder != null) {
            Set<Integer> desiredNets = new LinkedHashSet<>();
            for (WatchedSignal signal : watched) {
                editor.netOf(signal.reference()).ifPresent(desiredNets::add);
            }
            for (SignalTrace trace : recorder.traces()) {
                if (!desiredNets.contains(trace.netId())) {
                    recorder.unwatch(trace.netId());
                }
            }
            for (WatchedSignal signal : watched) {
                editor.netOf(signal.reference())
                        .ifPresent(netId -> recorder.watch(netId, signal.label()));
            }
        }
        notifyListeners();
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }
}
