package dev.logicforge.ui.command;

import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.document.CircuitDocument;
import java.util.List;

/** Turns chips by a quarter turn. Physical pin numbering never changes, only its world position. */
public final class RotateChipsCommand extends ReplaceChipsCommand {

    public RotateChipsCommand(CircuitDocument document, List<ChipInstance> before, List<ChipInstance> after) {
        super(document, before, after);
    }

    @Override
    public String name() {
        return "Rotate";
    }
}
