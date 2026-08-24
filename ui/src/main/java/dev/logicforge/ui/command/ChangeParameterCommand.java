package dev.logicforge.ui.command;

import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.component.PortSpec;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Changes one property of a component, for example the number of inputs of a gate.
 *
 * <p>Making a gate narrower removes ports. Wires attached to those ports are removed as
 * part of the same command, so a single undo brings both the ports and their wires back.
 * Ports that keep their name keep their wires — widening a gate never disturbs existing
 * connections.
 */
public final class ChangeParameterCommand implements CircuitCommand {

    private final CircuitDocument document;
    private final ComponentDefinition definition;
    private final ComponentInstance before;
    private final ComponentInstance after;
    private final String parameterName;
    private final List<Connection> removedConnections = new ArrayList<>();

    public ChangeParameterCommand(CircuitDocument document, ComponentDefinition definition,
                                  ComponentInstance before, String parameterKey, Object value) {
        this.document = document;
        this.definition = definition;
        this.before = before;
        this.after = before.withParameters(before.parameters().with(parameterKey, value));
        this.parameterName = definition.parameters().stream()
                .filter(spec -> spec.key().equals(parameterKey))
                .map(spec -> spec.displayName())
                .findFirst()
                .orElse(parameterKey);
    }

    @Override
    public String name() {
        return "Change " + parameterName;
    }

    @Override
    public void execute() {
        Set<String> remainingPorts = definition.ports(after.parameters()).stream()
                .map(PortSpec::name)
                .collect(java.util.stream.Collectors.toSet());

        removedConnections.clear();
        for (Connection connection : document.connectionsOf(before.id())) {
            if (!remainingPorts.contains(portNameOn(connection))) {
                removedConnections.add(connection);
            }
        }
        removedConnections.forEach(connection -> document.removeConnection(connection.id()));
        document.replaceComponent(after);
    }

    @Override
    public void undo() {
        document.replaceComponent(before);
        removedConnections.forEach(document::addConnection);
    }

    /** The port of this component that the wire is attached to. */
    private String portNameOn(Connection connection) {
        return connection.from().componentId().equals(before.id())
                ? connection.from().portName()
                : connection.to().portName();
    }
}
