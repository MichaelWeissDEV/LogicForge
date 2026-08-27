package dev.logicforge.library;

import dev.logicforge.circuit.component.ComponentCategory;
import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.component.ComponentDocumentation;
import dev.logicforge.circuit.component.InputInteraction;
import dev.logicforge.circuit.component.ParameterSpec;
import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.component.PortSpec;
import dev.logicforge.circuit.geometry.CircuitSize;
import java.util.List;

/**
 * The definition of a built-in component. One implementation covers the whole library: the
 * differences between an AND gate and an LED are data (identity, parameters, port layout),
 * not code.
 */
public record LibraryDefinition(
        String id,
        String displayName,
        ComponentCategory category,
        String description,
        List<ParameterSpec<?>> parameters,
        PortLayout portLayout,
        List<String> searchKeywords,
        InputInteraction inputInteraction,
        ComponentDocumentation documentation) implements ComponentDefinition {

    public LibraryDefinition {
        parameters = List.copyOf(parameters);
        searchKeywords = List.copyOf(searchKeywords);
        documentation = documentation == null ? ComponentDocumentation.EMPTY : documentation;
    }

    @Override
    public List<PortSpec> ports(ParameterValues values) {
        return portLayout.ports(values);
    }

    @Override
    public CircuitSize bodySize(ParameterValues values) {
        return portLayout.bodySize(values);
    }

    @Override
    public InputInteraction inputInteraction() {
        return inputInteraction;
    }
}
