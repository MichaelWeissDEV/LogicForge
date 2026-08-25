package dev.logicforge.ui.command;

import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.PortDisplayMode;
import java.util.List;

/** Changes compact/expanded pin presentation without touching the electrical netlist. */
public final class SetPortDisplayModeCommand extends ReplaceComponentsCommand {

    public SetPortDisplayModeCommand(CircuitDocument document, ComponentInstance before,
                                     PortDisplayMode mode) {
        super(document, List.of(before), List.of(before.withPortDisplayMode(mode)));
    }

    @Override
    public String name() {
        return "Change port display";
    }
}
