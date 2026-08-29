package dev.logicforge.ui.command;

import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.document.CircuitDocument;
import java.util.List;

/** Renames a chip's reference designator (e.g. {@code U1} -> {@code U7}). */
public final class SetChipReferenceDesignatorCommand extends ReplaceChipsCommand {

    public SetChipReferenceDesignatorCommand(CircuitDocument document, ChipInstance before, String designator) {
        super(document, List.of(before), List.of(before.withReferenceDesignator(designator)));
    }

    @Override
    public String name() {
        return "Rename chip";
    }
}
