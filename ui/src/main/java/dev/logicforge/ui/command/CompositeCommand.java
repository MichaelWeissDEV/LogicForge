package dev.logicforge.ui.command;

import java.util.List;

/**
 * Bundles several commands into a single undo step — e.g. moving a mixed selection of
 * components and chips in one drag, where each kind needs its own underlying command but
 * the user only sees one action.
 */
public final class CompositeCommand implements CircuitCommand {

    private final String name;
    private final List<CircuitCommand> commands;

    public CompositeCommand(String name, List<CircuitCommand> commands) {
        this.name = name;
        this.commands = List.copyOf(commands);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void execute() {
        commands.forEach(CircuitCommand::execute);
    }

    @Override
    public void undo() {
        for (int i = commands.size() - 1; i >= 0; i--) {
            commands.get(i).undo();
        }
    }
}
