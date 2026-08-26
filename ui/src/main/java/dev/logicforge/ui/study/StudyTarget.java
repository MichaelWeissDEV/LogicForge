package dev.logicforge.ui.study;

import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.compiler.CompilationResult;
import dev.logicforge.simulation.Simulation;
import dev.logicforge.ui.edit.CircuitEditor;

/** Explicitly distinguishes a concrete live runtime from a reference circuit definition. */
public sealed interface StudyTarget permits StudyTarget.LiveInstance,
        StudyTarget.ReferenceImplementation {

    String label();

    static LiveInstance live(CircuitEditor source) {
        return new LiveInstance(source.project(), source.compilation().orElseThrow(
                () -> new IllegalStateException("The project must compile before Study can attach")),
                source.simulation().orElseThrow(
                        () -> new IllegalStateException("The project has no live simulation")),
                "Live " + source.project().name());
    }

    record LiveInstance(CircuitProject project, CompilationResult compilation,
                        Simulation simulation, String label) implements StudyTarget {
        public LiveInstance {
            if (project == null || compilation == null || simulation == null) {
                throw new IllegalArgumentException("A live Study target needs project, compilation and simulation");
            }
            label = label == null || label.isBlank() ? "Live instance" : label;
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
