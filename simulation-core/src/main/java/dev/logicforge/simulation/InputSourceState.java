package dev.logicforge.simulation;

import dev.logicforge.logic.LogicVector;

/**
 * State of a component the user drives directly — a toggle switch or a push button.
 *
 * <p>Setting the value is a simulation operation, not a document edit: flipping a switch
 * changes what the circuit does right now but not what is saved. The value a switch starts
 * from after {@link #reset()} is a component parameter and therefore part of the project.
 */
public interface InputSourceState extends ComponentRuntimeState {

    LogicVector value();

    void setValue(LogicVector value);
}
