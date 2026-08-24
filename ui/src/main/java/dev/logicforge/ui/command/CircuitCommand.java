package dev.logicforge.ui.command;

/**
 * One undoable editing action.
 *
 * <p>Granularity is per user gesture, not per event: dragging five components across the
 * canvas is one command, however many mouse-move events it took.
 */
public interface CircuitCommand {

    /** Short description, shown in the status bar and in undo tooltips. */
    String name();

    void execute();

    void undo();
}
