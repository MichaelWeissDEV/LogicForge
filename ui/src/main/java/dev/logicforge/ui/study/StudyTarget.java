package dev.logicforge.ui.study;

import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.compiler.CompilationResult;
import dev.logicforge.simulation.Simulation;
import dev.logicforge.simulation.SimulationSession;
import dev.logicforge.ui.edit.CircuitEditor;
import java.util.Optional;
import java.util.UUID;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.SubcircuitSupport;
import dev.logicforge.structures.StructuralImplementationDescriptor;

/** Explicitly distinguishes a concrete live runtime from a reference circuit definition. */
public sealed interface StudyTarget permits StudyTarget.LiveInstance,
        StudyTarget.ReferenceImplementation {

    String label();

    static LiveInstance live(CircuitEditor source) {
        return new LiveInstance(source.project(), source.compilation().orElseThrow(
                () -> new IllegalStateException("The project must compile before Study can attach")),
                source.simulation().orElseThrow(
                        () -> new IllegalStateException("The project has no live simulation")),
                source.simulationSession().orElseThrow(),
                source.activeCircuitName(), source.activeInstancePath(),
                source.selection().components().size() == 1
                        ? Optional.of(source.selection().components().iterator().next())
                        : Optional.empty(),
                "Live " + source.project().name());
    }

    static LiveInstance inspectInternals(CircuitEditor source, ComponentInstance instance) {
        if (!SubcircuitSupport.isInstanceDefinition(instance.definitionId())) {
            throw new IllegalArgumentException("Inspect Internals requires a subcircuit instance");
        }
        String child = SubcircuitSupport.circuitName(instance.definitionId());
        Optional<String> childPath = source.activeInstancePath()
                .map(path -> path + "/" + instance.id());
        return new LiveInstance(source.project(), source.compilation().orElseThrow(
                () -> new IllegalStateException("The project must compile before Study can attach")),
                source.simulation().orElseThrow(
                        () -> new IllegalStateException("The project has no live simulation")),
                source.simulationSession().orElseThrow(),
                child, childPath, Optional.empty(),
                "Live " + (instance.label().isBlank() ? child : instance.label()));
    }

    static ReferenceImplementation reference(StructuralImplementationDescriptor descriptor) {
        CircuitProject project = descriptor.createProject();
        String circuitName = project.circuitNames().getLast();
        return new ReferenceImplementation(project, circuitName,
                "REFERENCE IMPLEMENTATION — " + descriptor.name());
    }

    record LiveInstance(CircuitProject project, CompilationResult compilation,
                        Simulation simulation, SimulationSession session, String initialCircuitName,
                        Optional<String> initialInstancePath, Optional<UUID> selectedComponent,
                        String label) implements StudyTarget {
        public LiveInstance {
            if (project == null || compilation == null || simulation == null) {
                throw new IllegalArgumentException("A live Study target needs project, compilation and simulation");
            }
            if (session == null || session.simulation() != simulation) {
                throw new IllegalArgumentException("Live Study session must own its simulation");
            }
            initialCircuitName = initialCircuitName == null || initialCircuitName.isBlank()
                    ? CircuitProject.MAIN_CIRCUIT : initialCircuitName;
            initialInstancePath = initialInstancePath == null ? Optional.empty()
                    : initialInstancePath;
            selectedComponent = selectedComponent == null ? Optional.empty() : selectedComponent;
            label = label == null || label.isBlank() ? "Live instance" : label;
        }

        public LiveInstance(CircuitProject project, CompilationResult compilation,
                            Simulation simulation, String label) {
            this(project, compilation, simulation, new SimulationSession(simulation),
                    CircuitProject.MAIN_CIRCUIT,
                    Optional.of(CircuitProject.MAIN_CIRCUIT), Optional.empty(), label);
        }

        public LiveInstance(CircuitProject project, CompilationResult compilation,
                            Simulation simulation, String initialCircuitName,
                            Optional<String> initialInstancePath,
                            Optional<UUID> selectedComponent, String label) {
            this(project, compilation, simulation, new SimulationSession(simulation),
                    initialCircuitName, initialInstancePath, selectedComponent, label);
        }
    }

    record ReferenceImplementation(CircuitProject project, String circuitName,
                                   String label) implements StudyTarget {
        public ReferenceImplementation {
            if (project == null || circuitName == null || circuitName.isBlank()) {
                throw new IllegalArgumentException("A reference Study target needs a project and circuit name");
            }
            label = label == null || label.isBlank() ? "Reference implementation" : label;
        }
    }
}
