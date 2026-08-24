package dev.logicforge.circuit.component;

import dev.logicforge.circuit.geometry.CircuitSize;
import java.util.List;
import java.util.Optional;

/**
 * The kind of a component — "an AND gate", as opposed to the three AND gates placed on the
 * canvas, which are {@link dev.logicforge.circuit.document.ComponentInstance}s.
 *
 * <p>A definition describes what a component <em>is</em>: its identity, its configurable
 * parameters, and the ports and body size that follow from those parameters. It says
 * nothing about how it is drawn (that is the renderer's business) and nothing about how it
 * behaves during simulation (that is a separate behaviour object), so that none of the
 * three grows into an unmanageable class.
 */
public interface ComponentDefinition {

    /** Stable identifier stored in project files, e.g. {@code "logic.and"}. */
    String id();

    String displayName();

    ComponentCategory category();

    String description();

    /** Configurable properties; may be empty. */
    List<ParameterSpec<?>> parameters();

    /**
     * The ports of an instance configured with {@code values}, in a stable order.
     * Port names are the connection identity, so a parameter change that keeps a port's
     * name keeps its connections.
     */
    List<PortSpec> ports(ParameterValues values);

    /** The size of the component body, unrotated. */
    CircuitSize bodySize(ParameterValues values);

    default ParameterValues defaultParameters() {
        return ParameterValues.defaultsOf(parameters());
    }

    default Optional<PortSpec> port(ParameterValues values, String portName) {
        return ports(values).stream().filter(port -> port.name().equals(portName)).findFirst();
    }

    /** Optional keywords that make the component easier to find in the palette search. */
    default List<String> searchKeywords() {
        return List.of();
    }
}
