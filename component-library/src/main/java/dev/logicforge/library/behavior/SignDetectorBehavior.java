package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;

/**
 * NEGATIVE = the most significant bit of A, read as a two's-complement sign bit.
 *
 * <p>Ports are {@code A} in, {@code NEGATIVE} out.
 *
 * @param width the bus width of A
 */
public record SignDetectorBehavior(BitWidth width) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        LogicVector a = LogicOperations.asGateInput(context.readInput(0));
        context.driveOutput(0, LogicVector.single(a.getBit(width.bits() - 1)));
    }

    @Override
    public ComponentRuntimeState createState() {
        return ComponentRuntimeState.STATELESS;
    }
}
