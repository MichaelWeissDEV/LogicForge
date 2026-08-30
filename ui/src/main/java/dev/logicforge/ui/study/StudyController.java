package dev.logicforge.ui.study;

import dev.logicforge.processor.lf8.runtime.Lf8RuntimeProbe;
import dev.logicforge.processor.lf8.runtime.ClockControl;
import dev.logicforge.processor.lf8.runtime.Breakpoint;
import dev.logicforge.processor.lf8.runtime.BreakpointEngine;
import dev.logicforge.processor.lf8.runtime.MemoryReadBreakpoint;
import dev.logicforge.processor.lf8.runtime.MemoryWriteBreakpoint;
import dev.logicforge.processor.lf8.runtime.PcBreakpoint;

import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.simulation.Simulation;
import dev.logicforge.ui.edit.CircuitEditor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import dev.logicforge.compiler.RuntimeInstancePath;

/** Navigation and simulation controls for a Study window. */
public final class StudyController {

    private static final int MAX_INSTRUCTION_EDGES = 4096;

    private final StudyTarget target;
    private final CircuitEditor editor = new CircuitEditor(ComponentRegistry.standard());
    private final Deque<java.util.UUID> forwardInstances = new ArrayDeque<>();
    private final BreakpointEngine breakpointEngine = new BreakpointEngine();
    private BreakpointEngine.BreakpointHit lastHit;

    public StudyController(StudyTarget target) {
        this.target = target;
        if (target instanceof StudyTarget.LiveInstance live) {
            editor.attachRuntime(live.project(), live.compilation(), live.simulation(),
                    live.session(), live.initialCircuitName(), live.initialInstancePath(),
                    live.selectedComponent());
        } else if (target instanceof StudyTarget.ReferenceImplementation reference) {
            editor.setProject(reference.project(), false);
            editor.openCircuit(reference.circuitName());
            editor.setRunning(false);
        }
    }

    public StudyTarget target() {
        return target;
    }

    public CircuitEditor editor() {
        return editor;
    }

    public boolean isLive() {
        return target instanceof StudyTarget.LiveInstance;
    }

    /** Probe for the concrete CPU containing (or selected in) the current Study context. */
    public Optional<Lf8RuntimeProbe> lf8Probe() {
        if (!(target instanceof StudyTarget.LiveInstance live)) {
            return Optional.empty();
        }
        Optional<RuntimeInstancePath> cpuPath = editor.activeInstancePath()
                .map(RuntimeInstancePath::parse)
                .flatMap(path -> Lf8RuntimeProbe.findContainingCpu(live.project(), path));
        if (cpuPath.isEmpty() && editor.activeInstancePath().isPresent()
                && editor.selection().components().size() == 1) {
            var selected = editor.document().component(
                    editor.selection().components().iterator().next());
            if (selected.isPresent()
                    && selected.get().definitionId().equals(
                    dev.logicforge.circuit.document.SubcircuitSupport.definitionId(
                            dev.logicforge.processor.lf8.Lf8CircuitFactory.CPU_CIRCUIT))) {
                cpuPath = Optional.of(RuntimeInstancePath.parse(
                        editor.activeInstancePath().orElseThrow()).child(selected.get().id()));
            }
        }
        if (cpuPath.isEmpty() && editor.activeInstancePath().isPresent()) {
            // A circuit with exactly one CPU is unambiguous even when nothing is selected.
            // Multiple CPUs deliberately produce no probe until the user selects/descends.
            var cpus = editor.document().components().stream()
                    .filter(component -> component.definitionId().equals(
                            dev.logicforge.circuit.document.SubcircuitSupport.definitionId(
                                    dev.logicforge.processor.lf8.Lf8CircuitFactory.CPU_CIRCUIT)))
                    .toList();
            if (cpus.size() == 1) {
                cpuPath = Optional.of(RuntimeInstancePath.parse(
                        editor.activeInstancePath().orElseThrow()).child(cpus.getFirst().id()));
            }
        }
        return cpuPath.map(path -> new Lf8RuntimeProbe(live.project(), live.compilation(),
                live.simulation(), path));
    }

    // ------------------------------------------------------------------ breakpoints

    /** Every breakpoint set so far, regardless of which CPU it targets or its enabled state. */
    public List<Breakpoint> breakpoints() {
        return breakpointEngine.breakpoints();
    }

    public boolean canAddBreakpoint() {
        return lf8Probe().isPresent();
    }

    public void addPcBreakpoint(int address) {
        addBreakpoint(cpuPath -> new PcBreakpoint(cpuPath, address));
    }

    public void addMemoryReadBreakpoint(int firstAddress, int lastAddress) {
        addBreakpoint(cpuPath -> new MemoryReadBreakpoint(cpuPath, firstAddress, lastAddress, true));
    }

    public void addMemoryWriteBreakpoint(int firstAddress, int lastAddress) {
        addBreakpoint(cpuPath -> new MemoryWriteBreakpoint(cpuPath, firstAddress, lastAddress, true));
    }

    private void addBreakpoint(java.util.function.Function<RuntimeInstancePath, Breakpoint> factory) {
        RuntimeInstancePath cpuPath = lf8Probe().map(Lf8RuntimeProbe::cpuPath).orElse(null);
        if (cpuPath == null) {
            return;
        }
        List<Breakpoint> updated = new ArrayList<>(breakpointEngine.breakpoints());
        updated.add(factory.apply(cpuPath));
        breakpointEngine.setBreakpoints(updated);
    }

    public void removeBreakpoint(Breakpoint breakpoint) {
        List<Breakpoint> updated = new ArrayList<>(breakpointEngine.breakpoints());
        updated.remove(breakpoint);
        breakpointEngine.setBreakpoints(updated);
        if (lastHit != null && lastHit.breakpoint().equals(breakpoint)) {
            lastHit = null;
        }
    }

    public void setBreakpointEnabled(Breakpoint breakpoint, boolean enabled) {
        List<Breakpoint> updated = new ArrayList<>(breakpointEngine.breakpoints());
        int index = updated.indexOf(breakpoint);
        if (index < 0) {
            return;
        }
        Breakpoint replacement = switch (breakpoint) {
            case PcBreakpoint pc -> new PcBreakpoint(pc.cpuPath(), pc.address(), enabled);
            case MemoryReadBreakpoint read -> new MemoryReadBreakpoint(
                    read.cpuPath(), read.firstAddress(), read.lastAddress(), enabled);
            case MemoryWriteBreakpoint write -> new MemoryWriteBreakpoint(
                    write.cpuPath(), write.firstAddress(), write.lastAddress(), enabled);
        };
        updated.set(index, replacement);
        breakpointEngine.setBreakpoints(updated);
    }

    /** The breakpoint (if any) that just fired, until the next {@link #checkBreakpoints()} clears it. */
    public Optional<BreakpointEngine.BreakpointHit> lastBreakpointHit() {
        return Optional.ofNullable(lastHit);
    }

    /** Upper bound on clock edges one Study refresh tick pumps — keeps "Run" UI-responsive. */
    private static final int MAX_EDGES_PER_TICK = 2_000;

    /**
     * Drives the LF-8 CPU's own clock forward while "Run" is active and checks breakpoints
     * after every single edge — an LF-8 circuit's {@code CLK} is an ordinary manually-driven
     * switch (see {@code ClockControl}), not a scheduled {@code source.clock}, so nothing
     * else in the simulator advances it on its own; without this, toggling "Run" on an LF-8
     * target would sit idle forever. Reuses the existing headless {@code BreakpointEngine}
     * exactly the way a fired logic analyzer trigger reuses {@code SimulationSession} — the
     * engine only ever decides *whether* a condition matched, this method decides what to do
     * about it. Stops on a breakpoint hit, on the CPU halting, or after {@link
     * #MAX_EDGES_PER_TICK} edges (so a UI frame can never be blocked indefinitely); the next
     * tick picks up exactly where this one left off.
     */
    public void advanceWhileRunning() {
        if (!isLive() || !editor.isRunning()) {
            return;
        }
        Lf8RuntimeProbe probe = lf8Probe().orElse(null);
        Simulation simulation = editor.simulation().orElse(null);
        if (probe == null || simulation == null) {
            return;
        }
        var clock = dev.logicforge.processor.lf8.runtime.ClockControl
                .discover(live().compilation(), simulation, probe.cpuPath()).orElse(null);
        if (clock == null) {
            return;
        }
        boolean stop = false;
        for (int edge = 0; edge < MAX_EDGES_PER_TICK && !stop; edge++) {
            if (!clock.stepActiveEdge()) {
                stop = true;
                break;
            }
            var hit = breakpointEngine.evaluate(probe);
            if (hit.isPresent()) {
                lastHit = hit.orElseThrow();
                stop = true;
            } else if (halted(probe)) {
                stop = true;
            }
        }
        editor.simulationSession().ifPresent(
                dev.logicforge.simulation.SimulationSession::simulationChanged);
        if (stop) {
            editor.setRunning(false);
        }
    }

    private static boolean halted(Lf8RuntimeProbe probe) {
        return probe.cpuPort("HALT").map(value -> value.singleBit()
                == dev.logicforge.logic.LogicState.ONE).orElse(false);
    }

    public void descend(ComponentInstance instance) {
        editor.openSubcircuit(instance);
        forwardInstances.clear();
    }

    public void back() {
        if (!editor.canNavigateBack()) {
            return;
        }
        // The current child was reached from the instance whose UUID is the last path segment.
        editor.activeInstancePath().flatMap(StudyController::lastUuid).ifPresent(forwardInstances::push);
        editor.navigateBack();
    }

    public void forward() {
        if (forwardInstances.isEmpty()) {
            return;
        }
        java.util.UUID instanceId = forwardInstances.pop();
        editor.document().component(instanceId).ifPresent(editor::openSubcircuit);
    }

    public boolean canBack() {
        return editor.canNavigateBack();
    }

    public boolean canForward() {
        return !forwardInstances.isEmpty();
    }

    public java.util.List<String> breadcrumbs() {
        return editor.navigationLabels();
    }

    public void navigateToBreadcrumb(int index) {
        int current = breadcrumbs().size() - 1;
        if (index < 0 || index > current) {
            throw new IllegalArgumentException("Breadcrumb index is outside the current path");
        }
        for (int depth = current; depth > index; depth--) {
            back();
        }
    }

    public void setRunning(boolean running) {
        editor.setRunning(running);
    }

    public void stepEvent() {
        editor.setRunning(false);
        editor.step();
    }

    public boolean stepClock() {
        editor.setRunning(false);
        Simulation simulation = editor.simulation().orElse(null);
        var probe = lf8Probe().orElse(null);
        if (probe == null || simulation == null) {
            return false;
        }
        var clock = ClockControl.discover(live().compilation(), simulation, probe.cpuPath());
        if (clock.isEmpty()) {
            return false;
        }
        if (!clock.orElseThrow().stepActiveEdge()) {
            return false;
        }
        editor.simulationSession().ifPresent(
                dev.logicforge.simulation.SimulationSession::simulationChanged);
        return true;
    }

    private StudyTarget.LiveInstance live() {
        return (StudyTarget.LiveInstance) target;
    }

    /** Advances only through real clock input changes until the next LF-8 boundary. */
    public boolean stepInstruction() {
        editor.setRunning(false);
        Simulation simulation = editor.simulation().orElse(null);
        var probe = lf8Probe().orElse(null);
        if (probe == null || simulation == null) {
            return false;
        }
        if (probe.microstep().isEmpty()) {
            return false;
        }
        for (int edge = 0; edge < MAX_INSTRUCTION_EDGES; edge++) {
            if (!stepClock()) {
                return false;
            }
            var value = probe.microstep().orElseThrow().toUnsignedLong();
            if (value.isPresent() && value.getAsLong() == 0) {
                return true;
            }
        }
        return false;
    }

    private static java.util.Optional<java.util.UUID> lastUuid(String path) {
        int separator = path.lastIndexOf('/');
        String segment = separator < 0 ? path : path.substring(separator + 1);
        try {
            return java.util.Optional.of(java.util.UUID.fromString(segment));
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
    }
}
