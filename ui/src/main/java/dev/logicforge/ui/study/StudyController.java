package dev.logicforge.ui.study;

import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.logic.LogicState;
import dev.logicforge.simulation.Simulation;
import dev.logicforge.ui.edit.CircuitEditor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import dev.logicforge.compiler.RuntimeInstancePath;

/** Navigation and simulation controls for a Study window. */
public final class StudyController {

    private static final int MAX_INSTRUCTION_EDGES = 4096;

    private final StudyTarget target;
    private final CircuitEditor editor = new CircuitEditor(ComponentRegistry.standard());
    private final Deque<java.util.UUID> forwardInstances = new ArrayDeque<>();

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
        var clock = probe.inputSource("CLK");
        if (clock.isEmpty()) {
            return false;
        }
        simulation.setInput(clock.getAsInt(), LogicState.ZERO);
        simulation.runUntilStableAtCurrentTime();
        simulation.setInput(clock.getAsInt(), LogicState.ONE);
        simulation.runUntilStableAtCurrentTime();
        editor.simulationSession().ifPresent(
                dev.logicforge.simulation.SimulationSession::simulationChanged);
        return true;
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
