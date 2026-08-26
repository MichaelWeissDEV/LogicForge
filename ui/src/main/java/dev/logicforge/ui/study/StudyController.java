package dev.logicforge.ui.study;

import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.logic.LogicState;
import dev.logicforge.simulation.Simulation;
import dev.logicforge.ui.edit.CircuitEditor;
import java.util.ArrayDeque;
import java.util.Deque;

/** Navigation and simulation controls for a Study window. */
public final class StudyController {

    private static final int MAX_INSTRUCTION_EDGES = 4096;

    private final StudyTarget target;
    private final CircuitEditor editor = new CircuitEditor(ComponentRegistry.standard());
    private final Deque<java.util.UUID> forwardInstances = new ArrayDeque<>();

    public StudyController(StudyTarget target) {
        this.target = target;
        if (target instanceof StudyTarget.LiveInstance live) {
            editor.attachRuntime(live.project(), live.compilation(), live.simulation());
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

    public void setRunning(boolean running) {
        editor.setRunning(running);
    }

    public void stepEvent() {
        editor.setRunning(false);
        editor.step();
    }

    public boolean stepClock() {
        editor.setRunning(false);
        var compilation = editor.compilation().orElse(null);
        Simulation simulation = editor.simulation().orElse(null);
        if (compilation == null || simulation == null) {
            return false;
        }
        var clock = compilation.componentByLabel("CLK");
        if (clock.isEmpty()) {
            return false;
        }
        simulation.setInput(clock.getAsInt(), LogicState.ZERO);
        simulation.setInput(clock.getAsInt(), LogicState.ONE);
        editor.setRunning(false); // also publishes a view refresh
        return true;
    }

    /** Advances only through real clock input changes until the next LF-8 boundary. */
    public boolean stepInstruction() {
        editor.setRunning(false);
        var compilation = editor.compilation().orElse(null);
        Simulation simulation = editor.simulation().orElse(null);
        if (compilation == null || simulation == null) {
            return false;
        }
        var microstep = compilation.componentByLabel("MICROSTEP");
        if (microstep.isEmpty()) {
            return false;
        }
        for (int edge = 0; edge < MAX_INSTRUCTION_EDGES; edge++) {
            if (!stepClock()) {
                return false;
            }
            var value = simulation.readOutput(microstep.getAsInt(), 0).toUnsignedLong();
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
