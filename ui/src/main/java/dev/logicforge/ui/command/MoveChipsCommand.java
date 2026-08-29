package dev.logicforge.ui.command;

import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.document.CircuitDocument;
import java.util.List;

/** One drag of one or more chips. */
public final class MoveChipsCommand extends ReplaceChipsCommand {

    public MoveChipsCommand(CircuitDocument document, List<ChipInstance> before, List<ChipInstance> after) {
        super(document, before, after);
    }

    @Override
    public String name() {
        return after().size() == 1 ? "Move chip" : "Move chips";
    }
}
