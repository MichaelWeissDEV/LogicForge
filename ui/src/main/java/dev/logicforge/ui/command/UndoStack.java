package dev.logicforge.ui.command;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/** The undo and redo history of one circuit. */
public final class UndoStack {

    private final Deque<CircuitCommand> done = new ArrayDeque<>();
    private final Deque<CircuitCommand> undone = new ArrayDeque<>();
    private final List<Runnable> listeners = new ArrayList<>();

    /** Runs a command and remembers it. Doing something new discards the redo history. */
    public void execute(CircuitCommand command) {
        command.execute();
        done.push(command);
        undone.clear();
        notifyListeners();
    }

    public void undo() {
        if (done.isEmpty()) {
            return;
        }
        CircuitCommand command = done.pop();
        command.undo();
        undone.push(command);
        notifyListeners();
    }

    public void redo() {
        if (undone.isEmpty()) {
            return;
        }
        CircuitCommand command = undone.pop();
        command.execute();
        done.push(command);
        notifyListeners();
    }

    public boolean canUndo() {
        return !done.isEmpty();
    }

    public boolean canRedo() {
        return !undone.isEmpty();
    }

    public Optional<String> undoName() {
        return done.isEmpty() ? Optional.empty() : Optional.of(done.peek().name());
    }

    public Optional<String> redoName() {
        return undone.isEmpty() ? Optional.empty() : Optional.of(undone.peek().name());
    }

    /** Forgets the history, e.g. after opening another project. */
    public void clear() {
        done.clear();
        undone.clear();
        notifyListeners();
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }
}
