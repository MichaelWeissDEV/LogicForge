package dev.logicforge.ui.command;

import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import java.util.List;

/** Inserts previously copied components and chips together with the wires between them. */
public record PasteCommand(CircuitDocument document, List<ComponentInstance> components,
                           List<ChipInstance> chips, List<Connection> connections) implements CircuitCommand {

    public PasteCommand {
        components = List.copyOf(components);
        chips = List.copyOf(chips);
        connections = List.copyOf(connections);
    }

    public PasteCommand(CircuitDocument document, List<ComponentInstance> components,
                        List<Connection> connections) {
        this(document, components, List.of(), connections);
    }

    @Override
    public String name() {
        return "Paste";
    }

    @Override
    public void execute() {
        components.forEach(document::addComponent);
        chips.forEach(document::addChip);
        connections.forEach(document::addConnection);
    }

    @Override
    public void undo() {
        connections.forEach(connection -> document.removeConnection(connection.id()));
        components.forEach(component -> document.removeComponent(component.id()));
        chips.forEach(chip -> document.removeChip(chip.id()));
    }
}
