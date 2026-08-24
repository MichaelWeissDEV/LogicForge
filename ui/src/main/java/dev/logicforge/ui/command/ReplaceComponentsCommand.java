package dev.logicforge.ui.command;

import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.ComponentInstance;
import java.util.List;

/**
 * Base for every edit that swaps components for modified copies of themselves — moving,
 * rotating, renaming. The before and after states are captured once, so a whole drag
 * collapses into a single undo step.
 */
public abstract class ReplaceComponentsCommand implements CircuitCommand {

    private final CircuitDocument document;
    private final List<ComponentInstance> before;
    private final List<ComponentInstance> after;

    protected ReplaceComponentsCommand(CircuitDocument document, List<ComponentInstance> before,
                                       List<ComponentInstance> after) {
        if (before.size() != after.size()) {
            throw new IllegalArgumentException("Every changed component needs a previous state");
        }
        this.document = document;
        this.before = List.copyOf(before);
        this.after = List.copyOf(after);
    }

    protected CircuitDocument document() {
        return document;
    }

    protected List<ComponentInstance> after() {
        return after;
    }

    protected List<ComponentInstance> before() {
        return before;
    }

    @Override
    public void execute() {
        after.forEach(document::replaceComponent);
    }

    @Override
    public void undo() {
        before.forEach(document::replaceComponent);
    }
}
