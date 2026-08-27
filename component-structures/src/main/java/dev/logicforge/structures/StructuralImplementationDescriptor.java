package dev.logicforge.structures;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.CircuitProject;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Describes one parameter-constrained structural implementation of a real component. */
public record StructuralImplementationDescriptor(
        String targetDefinitionId,
        ImplementationLevel level,
        ParameterMatcher matcher,
        Supplier<CircuitProject> projectFactory,
        String name,
        String description,
        List<String> tags) {

    public StructuralImplementationDescriptor {
        targetDefinitionId = requireText(targetDefinitionId, "targetDefinitionId");
        level = Objects.requireNonNull(level, "level");
        matcher = Objects.requireNonNull(matcher, "matcher");
        projectFactory = Objects.requireNonNull(projectFactory, "projectFactory");
        name = requireText(name, "name");
        description = Objects.requireNonNullElse(description, "");
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public boolean supports(ParameterValues parameters) {
        return matcher.matches(Objects.requireNonNull(parameters, "parameters"));
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
