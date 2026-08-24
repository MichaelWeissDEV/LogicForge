package dev.logicforge.ui.command;

import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.ComponentInstance;

/** Places a component on the circuit. */
public record AddComponentCommand(CircuitDocument document, ComponentInstance instance)
        implements CircuitCommand {

    @Override
    public String name() {
        return "Add component";
    }

    @Override
    public void execute() {
        document.addComponent(instance);
    }

    @Override
    public void undo() {
        document.removeComponent(instance.id());
    }
}
