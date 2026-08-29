package dev.logicforge.ui.command;

import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.document.CircuitDocument;
import java.util.List;

/**
 * Base for every edit that swaps chips for modified copies of themselves — moving,
 * rotating, renaming, changing display mode. The before and after states are captured
 * once, so a whole drag collapses into a single undo step. Mirrors
 * {@link ReplaceComponentsCommand} exactly.
 */
public abstract class ReplaceChipsCommand implements CircuitCommand {

    private final CircuitDocument document;
    private final List<ChipInstance> before;
    private final List<ChipInstance> after;

    protected ReplaceChipsCommand(CircuitDocument document, List<ChipInstance> before,
                                  List<ChipInstance> after) {
        if (before.size() != after.size()) {
            throw new IllegalArgumentException("Every changed chip needs a previous state");
        }
        this.document = document;
        this.before = List.copyOf(before);
        this.after = List.copyOf(after);
    }

    protected CircuitDocument document() {
        return document;
    }

    protected List<ChipInstance> after() {
        return after;
    }

    protected List<ChipInstance> before() {
        return before;
    }

    @Override
    public void execute() {
        after.forEach(document::replaceChip);
    }

    @Override
    public void undo() {
        before.forEach(document::replaceChip);
    }
}
