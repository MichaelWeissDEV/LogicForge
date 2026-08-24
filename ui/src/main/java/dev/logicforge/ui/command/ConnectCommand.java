package dev.logicforge.ui.command;

import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.Connection;

/** Draws a wire between two ports. */
public record ConnectCommand(CircuitDocument document, Connection connection) implements CircuitCommand {

    @Override
    public String name() {
        return "Connect";
    }

    @Override
    public void execute() {
        document.addConnection(connection);
    }

    @Override
    public void undo() {
        document.removeConnection(connection.id());
    }
}
