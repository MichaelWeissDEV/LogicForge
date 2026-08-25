package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/**
 * Splits a bus into its individual bits: output {@code i} is bit {@code i} of BUS (LSB
 * first). Ports are {@code BUS} in, {@code BIT0..BIT(width-1)} out.
 *
 * @param width the bus width of BUS
 */
public record SplitterBehavior(BitWidth width) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        LogicVector bus = context.readInput(0);
        for (int i = 0; i < width.bits(); i++) {
            context.driveOutput(i, LogicVector.single(bus.getBit(i)));
        }
    }
}
