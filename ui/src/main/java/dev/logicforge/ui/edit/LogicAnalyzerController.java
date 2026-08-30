package dev.logicforge.ui.edit;

import dev.logicforge.analyzer.AnalyzerSignalBinding;
import dev.logicforge.analyzer.SignalRecorder;
import dev.logicforge.analyzer.SignalTrace;
import dev.logicforge.analyzer.TriggerCondition;
import dev.logicforge.analyzer.TriggerEngine;
import dev.logicforge.circuit.document.ElectricalEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortSlice;
import dev.logicforge.compiler.ResolvedSignal;
import dev.logicforge.compiler.RuntimeInstancePath;
import dev.logicforge.simulation.Simulation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    /**
     * A signal the user chose to watch, identified the way the rest of the editor does.
     * {@code instancePath} is captured at the moment the watch is added — the same reason
     * {@code ComponentViewTarget} exists for a long-lived Memory view: resolution must stay
     * pinned to that one hierarchy instance, never drift onto whatever the editor's ambient
     * current view happens to be later. {@code hierarchyPath} is the combined path, kept for
     * display and as a resolution fallback for signals that predate an instance's own path
     * entry (see {@link #resolveBinding}).
     */
    public record WatchedSignal(
            ElectricalEndpoint reference, Optional<String> instancePath, String hierarchyPath, String label) {
    }

    private final CircuitEditor editor;
    private final List<WatchedSignal> watched = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    private Simulation attachedSimulation;
    private SignalRecorder recorder;
    private TriggerEngine triggerEngine;
    private WatchedSignal armedSignal;
    private TriggerCondition armedCondition;

    public LogicAnalyzerController(CircuitEditor editor) {
        this.editor = editor;
        editor.addChangeListener(this::resync);
        resync();
    }

    /** {@code false} while viewing a definition with no concrete running instance selected. */
    public boolean canWatch() {
        return editor.activeInstancePath().isPresent();
    }

    public void addSignal(ElectricalEndpoint reference, String label) {
        Optional<String> path = hierarchyPath(reference);
        if (path.isEmpty()) {
            return;
        }
        if (watched.stream().anyMatch(signal -> signal.hierarchyPath().equals(path.get()))) {
            return;
        }
        watched.add(new WatchedSignal(reference, editor.activeInstancePath(), path.get(), label));
        resync();
    }

    public void addSignal(PortEndpoint reference, String label) {
        addSignal(new ElectricalEndpoint.ComponentEndpoint(reference), label);
    }

    public void addSignal(PortReference reference, String label) {
        addSignal(PortEndpoint.whole(reference), label);
    }

    public void removeSignal(ElectricalEndpoint reference) {
        if (watched.removeIf(signal -> signal.reference().equals(reference))) {
            resync();
        }
    }

    public void removeSignal(WatchedSignal watchedSignal) {
        if (watched.remove(watchedSignal)) {
            resync();
        }
    }

    public boolean isWatching(ElectricalEndpoint reference) {
        return watched.stream().anyMatch(signal -> signal.reference().equals(reference));
    }

    public List<WatchedSignal> watchedSignals() {
        return List.copyOf(watched);
    }

    /** Stable source identity used for Analyzer -> Study/canvas navigation. */
    public Optional<SignalLocation> location(WatchedSignal signal) {
        return signal.instancePath().map(RuntimeInstancePath::parse)
                .map(path -> new SignalLocation(path, signal.reference()));
    }

    public record SignalLocation(RuntimeInstancePath parentPath, ElectricalEndpoint endpoint) {
    }

    public Optional<SignalTrace> traceFor(ElectricalEndpoint reference) {
        return watched.stream().filter(signal -> signal.reference().equals(reference)).findFirst()
                .flatMap(this::traceFor);
    }

    public Optional<SignalTrace> traceFor(PortEndpoint reference) {
        return traceFor(new ElectricalEndpoint.ComponentEndpoint(reference));
    }

    public Optional<SignalTrace> traceFor(WatchedSignal signal) {
        if (recorder == null) {
            return Optional.empty();
        }
        return resolveBinding(signal).flatMap(recorder::trace);
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

    // ------------------------------------------------------------------ trigger

    /**
     * Arms the trigger on one watched signal: the shared simulation run-control (the same
     * one the toolbar's Run/Pause button and Study window drive) is paused the instant
     * {@code condition} matches — see {@link TriggerEngine} for why that wiring lives here,
     * in the UI layer, rather than inside the trigger engine itself.
     */
    public void armTrigger(WatchedSignal signal, TriggerCondition condition) {
        if (!watched.contains(signal)) {
            throw new IllegalArgumentException("Not a watched signal: " + signal);
        }
        armedSignal = signal;
        armedCondition = condition;
        applyTrigger();
        notifyListeners();
    }

    public void disarmTrigger() {
        armedSignal = null;
        armedCondition = null;
        if (triggerEngine != null) {
            triggerEngine.disarm();
        }
        notifyListeners();
    }

    public TriggerEngine.Status triggerStatus() {
        return triggerEngine == null ? TriggerEngine.Status.DISARMED : triggerEngine.status();
    }

    public Optional<WatchedSignal> armedSignal() {
        return Optional.ofNullable(armedSignal);
    }

    public Optional<TriggerCondition> armedCondition() {
        return Optional.ofNullable(armedCondition);
    }

    public OptionalLong triggerTime() {
        return triggerEngine == null ? OptionalLong.empty() : triggerEngine.triggerTime();
    }

    /** Re-arms after a fire, keeping the same signal and condition — "run again". */
    public void rearmTrigger() {
        if (armedSignal == null || triggerEngine == null) {
            return;
        }
        applyTrigger();
        notifyListeners();
    }

    /** (Re-)binds the trigger engine to the currently armed signal's freshly resolved net(s). */
    private void applyTrigger() {
        if (triggerEngine == null || armedSignal == null || armedCondition == null) {
            return;
        }
        resolveBinding(armedSignal).ifPresentOrElse(
                binding -> triggerEngine.arm(binding, armedCondition),
                triggerEngine::disarm);
    }

    private void resync() {
        Simulation current = editor.simulation().orElse(null);
        if (current != attachedSimulation) {
            if (recorder != null) {
                recorder.detach();
            }
            if (triggerEngine != null) {
                triggerEngine.detach();
            }
            recorder = current == null ? null : new SignalRecorder(current);
            triggerEngine = current == null ? null : new TriggerEngine(current);
            if (triggerEngine != null) {
                // Called from inside Simulation's onNetChanged dispatch (TriggerEngine is a
                // SimulationObserver too). Simulation.setRunning(false) only flips a flag —
                // it never touches the event queue or the observer list — so this is safe to
                // call re-entrantly; it never causes a recompile (only a structural edit
                // does), so resync() below never sees attachedSimulation change as a result
                // of firing, and never re-enters addObserver/removeObserver mid-dispatch.
                triggerEngine.addFireListener(() -> editor.setRunning(false));
            }
            attachedSimulation = current;
        }
        if (recorder != null) {
            Map<WatchedSignal, AnalyzerSignalBinding> desired = new LinkedHashMap<>();
            for (WatchedSignal signal : watched) {
                resolveBinding(signal).ifPresent(binding -> desired.put(signal, binding));
            }
            Set<AnalyzerSignalBinding> desiredBindings = new LinkedHashSet<>(desired.values());
            for (SignalTrace trace : recorder.traces()) {
                if (!desiredBindings.contains(trace.binding())) {
                    recorder.unwatch(trace.binding());
                }
            }
            desired.forEach((signal, binding) -> recorder.watch(binding, signal.label()));
        }
        if (armedSignal != null && !watched.contains(armedSignal)) {
            // The armed watch itself was removed; there is nothing left to trigger on.
            armedSignal = null;
            armedCondition = null;
            if (triggerEngine != null) {
                triggerEngine.disarm();
            }
        } else if (triggerEngine != null && triggerEngine.status() != TriggerEngine.Status.TRIGGERED) {
            applyTrigger();
        }
        notifyListeners();
    }

    /**
     * Resolves a watched signal to the runtime net(s) that carry its value, pinned to the
     * hierarchy instance captured when the watch was added — never the editor's ambient
     * current view, which changes as the user navigates (see {@code
     * HierarchyRuntimeContext}). This is what lets a watch cover a whole port, a single bit,
     * or a range that spans several independent nets on a bit-mode port, all uniformly: the
     * compiler's {@link ResolvedSignal} does the actual multi-net reconstruction, and this
     * method only translates it into the analyzer's own {@link AnalyzerSignalBinding}
     * vocabulary (the analyzer module has no dependency on the compiler, by design). A watch
     * whose instance genuinely disappeared (the subcircuit was removed, or recompilation no
     * longer has that path) still correctly goes unresolved rather than silently re-binding
     * to a different instance's signal of the same local shape.
     */
    private Optional<AnalyzerSignalBinding> resolveBinding(WatchedSignal signal) {
        return editor.signalAt(signal.instancePath(), signal.reference()).map(this::toBinding);
    }

    private AnalyzerSignalBinding toBinding(ResolvedSignal resolved) {
        return switch (resolved) {
            case ResolvedSignal.VectorNet vector ->
                    new AnalyzerSignalBinding.Vector(vector.netId(), vector.offset(), vector.width());
            case ResolvedSignal.ScalarNet scalar -> new AnalyzerSignalBinding.Scalar(scalar.netId());
            case ResolvedSignal.BitVector bits -> new AnalyzerSignalBinding.Bits(bits.nets());
        };
    }

    private Optional<String> hierarchyPath(ElectricalEndpoint endpoint) {
        if (endpoint instanceof ElectricalEndpoint.ComponentEndpoint component) {
            return hierarchyPath(component.port());
        }
        if (endpoint instanceof ElectricalEndpoint.ChipPinEndpoint chipPin) {
            return editor.activeInstancePath().map(path -> path + "/" + chipPin.chipInstanceId()
                    + ".pin" + chipPin.physicalPinNumber());
        }
        return Optional.empty();
    }

    private Optional<String> hierarchyPath(PortEndpoint endpoint) {
        String suffix = switch (endpoint.slice()) {
            case PortSlice.Whole ignored -> endpoint.portName();
            case PortSlice.Bit bit -> endpoint.portName() + "[" + bit.index() + "]";
            case PortSlice.Range range -> endpoint.portName() + "[" + range.msb()
                    + ":" + range.lsb() + "]";
        };
        return editor.activeInstancePath().map(path -> path + "/" + endpoint.componentId() + "." + suffix);
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }
}
