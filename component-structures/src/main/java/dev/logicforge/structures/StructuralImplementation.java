package dev.logicforge.structures;

import dev.logicforge.circuit.document.CircuitProject;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Metadata plus a fresh-project factory for one canonical structural implementation. */
public record StructuralImplementation(
        String targetComponentId,
        String name,
        String description,
        ImplementationLevel level,
        Supplier<CircuitProject> projectFactory,
        List<String> tags) {

    public StructuralImplementation {
        targetComponentId = requireText(targetComponentId, "targetComponentId");
        name = requireText(name, "name");
        description = Objects.requireNonNullElse(description, "");
        level = Objects.requireNonNull(level, "level");
        projectFactory = Objects.requireNonNull(projectFactory, "projectFactory");
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public CircuitProject createProject() {
        return projectFactory.get();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
