package dev.logicforge.ui.command;

import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import java.util.List;

/** Inserts previously copied components together with the wires between them. */
public record PasteCommand(CircuitDocument document, List<ComponentInstance> components,
                           List<Connection> connections) implements CircuitCommand {

    public PasteCommand {
        components = List.copyOf(components);
        connections = List.copyOf(connections);
    }

    @Override
    public String name() {
        return "Paste";
    }

    @Override
    public void execute() {
        components.forEach(document::addComponent);
        connections.forEach(document::addConnection);
    }

    @Override
    public void undo() {
        connections.forEach(connection -> document.removeConnection(connection.id()));
        components.forEach(component -> document.removeComponent(component.id()));
    }
}
