package dev.logicforge.ui.study;

import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.SubcircuitSupport;
import dev.logicforge.compiler.CompilationResult;
import dev.logicforge.compiler.RuntimeInstancePath;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.processor.lf8.Lf8CircuitFactory;
import dev.logicforge.simulation.ComponentDebugSnapshot;
import dev.logicforge.simulation.InputSourceState;
import dev.logicforge.simulation.Simulation;
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.ArrayList;
import java.util.Map;

/** Instance-scoped LF-8 architectural probe; it never falls back to a global label. */
public final class Lf8RuntimeProbe {

    private final CircuitProject project;
    private final CompilationResult compilation;
    private final Simulation simulation;
    private final RuntimeInstancePath cpuPath;

    public Lf8RuntimeProbe(CircuitProject project, CompilationResult compilation,
                           Simulation simulation, RuntimeInstancePath cpuPath) {
        this.project = java.util.Objects.requireNonNull(project, "project");
        this.compilation = java.util.Objects.requireNonNull(compilation, "compilation");
        this.simulation = java.util.Objects.requireNonNull(simulation, "simulation");
        this.cpuPath = java.util.Objects.requireNonNull(cpuPath, "cpuPath");
        if (cpuPath.instanceIds().isEmpty()) {
            throw new IllegalArgumentException("LF-8 probe must be rooted at a concrete CPU instance");
        }
    }

    public RuntimeInstancePath cpuPath() {
        return cpuPath;
    }

    /** Finds the LF8_CPU ancestor of an arbitrary concrete hierarchy context. */
    public static Optional<RuntimeInstancePath> findContainingCpu(
            CircuitProject project, RuntimeInstancePath context) {
        CircuitDocument document = project.circuit(context.rootCircuitName()).orElse(null);
        if (document == null) {
            return Optional.empty();
        }
        RuntimeInstancePath traversed = RuntimeInstancePath.root(context.rootCircuitName());
        RuntimeInstancePath cpu = null;
        for (var instanceId : context.instanceIds()) {
            ComponentInstance instance = document.component(instanceId).orElse(null);
            if (instance == null || !SubcircuitSupport.isInstanceDefinition(instance.definitionId())) {
                return Optional.empty();
            }
            traversed = traversed.child(instanceId);
            String child = SubcircuitSupport.circuitName(instance.definitionId());
            if (Lf8CircuitFactory.CPU_CIRCUIT.equals(child)) {
                cpu = traversed;
            }
            document = project.circuit(child).orElse(null);
            if (document == null) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(cpu);
    }

    public OptionalInt component(String relativePath) {
        Resolved resolved = resolve(relativePath);
        return compilation.hierarchySourceMap().componentId(resolved.path().toString());
    }

    public Optional<LogicVector> output(String relativePath, int outputIndex) {
        OptionalInt component = component(relativePath);
        return component.isEmpty() ? Optional.empty()
                : Optional.of(simulation.readOutput(component.getAsInt(), outputIndex));
    }

    /** Reads a named port on either a primitive or a hierarchy-only subcircuit instance. */
    public Optional<LogicVector> value(String relativePath, String portName) {
        Resolved resolved = resolve(relativePath);
        OptionalInt net = compilation.hierarchySourceMap()
                .netId(resolved.path() + "." + portName);
        return net.isEmpty() ? Optional.empty()
                : Optional.of(simulation.readNet(net.getAsInt()));
    }

    public Optional<ComponentDebugSnapshot> debugSnapshot(String relativePath) {
        OptionalInt component = component(relativePath);
        return component.isEmpty() ? Optional.empty()
                : Optional.of(simulation.debugSnapshot(component.getAsInt()));
    }

    public Optional<LogicVector> pc() {
        return value("DATAPATH/PC", "COUNT");
    }

    public Optional<LogicVector> sp() {
        return value("DATAPATH/SP", "COUNT");
    }

    public Optional<LogicVector> ir() {
        return value("DATAPATH/IR", "Q");
    }

    public Optional<ComponentDebugSnapshot> registerFile() {
        Optional<ComponentDebugSnapshot> primitive = debugSnapshot("DATAPATH/REGISTER_FILE")
                .filter(snapshot -> !snapshot.registers().isEmpty());
        if (primitive.isPresent()) {
            return primitive;
        }
        ArrayList<LogicVector> registers = new ArrayList<>(8);
        for (int index = 0; index < 8; index++) {
            Optional<LogicVector> value = value(
                    "DATAPATH/REGISTER_FILE/REGISTER_" + index, "Q");
            if (value.isEmpty()) {
                return Optional.empty();
            }
            registers.add(value.get());
        }
        return Optional.of(new ComponentDebugSnapshot(Map.of(), registers, null, Map.of()));
    }

    /** Concrete user-driven source electrically connected to one CPU interface input. */
    public OptionalInt inputSource(String cpuPortName) {
        OptionalInt net = compilation.hierarchySourceMap()
                .netId(cpuPath + "." + cpuPortName);
        if (net.isEmpty()) {
            return OptionalInt.empty();
        }
        int found = -1;
        for (int driverId : compilation.circuit().net(net.getAsInt()).drivers()) {
            int componentId = compilation.circuit().driver(driverId).componentId();
            if (simulation.stateOf(componentId) instanceof InputSourceState) {
                if (found >= 0 && found != componentId) {
                    return OptionalInt.empty();
                }
                found = componentId;
            }
        }
        return found < 0 ? OptionalInt.empty() : OptionalInt.of(found);
    }

    public Optional<LogicVector> microstep() {
        return value("CONTROL/MICROSTEP", "COUNT");
    }

    private Resolved resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || relativePath.startsWith("/")) {
            throw new IllegalArgumentException("LF-8 relative path cannot be blank or absolute");
        }
        CircuitDocument document = project.circuit(Lf8CircuitFactory.CPU_CIRCUIT)
                .orElseThrow(() -> new IllegalArgumentException("Project has no LF8_CPU circuit"));
        RuntimeInstancePath path = cpuPath;
        String[] labels = Arrays.stream(relativePath.split("/"))
                .filter(segment -> !segment.isBlank()).toArray(String[]::new);
        ComponentInstance component = null;
        for (int index = 0; index < labels.length; index++) {
            String label = labels[index];
            component = uniqueLabel(document, label);
            path = path.child(component.id());
            if (index < labels.length - 1) {
                if (!SubcircuitSupport.isInstanceDefinition(component.definitionId())) {
                    throw new IllegalArgumentException("LF-8 path segment is not hierarchical: " + label);
                }
                String childName = SubcircuitSupport.circuitName(component.definitionId());
                document = project.circuit(childName).orElseThrow(() ->
                        new IllegalArgumentException("Missing LF-8 child circuit: " + childName));
            }
        }
        return new Resolved(path, component);
    }

    private static ComponentInstance uniqueLabel(CircuitDocument document, String label) {
        var matches = document.components().stream()
                .filter(component -> label.equals(component.label())).toList();
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Expected one '" + label + "' in "
                    + document.metadata().name() + ", found " + matches.size());
        }
        return matches.getFirst();
    }

    private record Resolved(RuntimeInstancePath path, ComponentInstance instance) {
    }
}
