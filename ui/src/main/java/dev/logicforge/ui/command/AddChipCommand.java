package dev.logicforge.ui.command;

import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.document.CircuitDocument;

/** Places one physical chip package. Mirrors {@link AddComponentCommand} exactly. */
public record AddChipCommand(CircuitDocument document, ChipInstance instance) implements CircuitCommand {

    @Override
    public String name() {
        return "Add chip";
    }

    @Override
    public void execute() {
        document.addChip(instance);
    }

    @Override
    public void undo() {
        document.removeChip(instance.id());
    }
}
