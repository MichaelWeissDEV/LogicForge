package dev.logicforge.ui.command;

import dev.logicforge.circuit.chip.ChipDisplayMode;
import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.document.CircuitDocument;
import java.util.List;

/** Switches a chip between package, symbol and structural presentation. */
public final class SetChipDisplayModeCommand extends ReplaceChipsCommand {

    public SetChipDisplayModeCommand(CircuitDocument document, ChipInstance before, ChipDisplayMode mode) {
        super(document, List.of(before), List.of(before.withDisplayMode(mode)));
    }

    @Override
    public String name() {
        return "Change chip display mode";
    }
}
