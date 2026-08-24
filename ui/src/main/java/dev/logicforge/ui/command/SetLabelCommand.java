package dev.logicforge.ui.command;

import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.ComponentInstance;
import java.util.List;

/** Renames a component. */
public final class SetLabelCommand extends ReplaceComponentsCommand {

    public SetLabelCommand(CircuitDocument document, ComponentInstance before, String label) {
        super(document, List.of(before), List.of(before.withLabel(label)));
    }

    @Override
    public String name() {
        return "Rename";
    }
}
