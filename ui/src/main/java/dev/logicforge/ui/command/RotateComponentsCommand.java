package dev.logicforge.ui.command;

import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.ComponentInstance;
import java.util.List;

/** Turns components by a quarter turn. Their ports — and the wires — follow. */
public final class RotateComponentsCommand extends ReplaceComponentsCommand {

    public RotateComponentsCommand(CircuitDocument document, List<ComponentInstance> before,
                                   List<ComponentInstance> after) {
        super(document, before, after);
    }

    @Override
    public String name() {
        return "Rotate";
    }
}
