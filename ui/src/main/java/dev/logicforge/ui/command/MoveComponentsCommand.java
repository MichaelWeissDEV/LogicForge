package dev.logicforge.ui.command;

import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.ComponentInstance;
import java.util.List;

/** One drag of one or more components. */
public final class MoveComponentsCommand extends ReplaceComponentsCommand {

    public MoveComponentsCommand(CircuitDocument document, List<ComponentInstance> before,
                                 List<ComponentInstance> after) {
        super(document, before, after);
    }

    @Override
    public String name() {
        return after().size() == 1 ? "Move component" : "Move components";
    }
}
