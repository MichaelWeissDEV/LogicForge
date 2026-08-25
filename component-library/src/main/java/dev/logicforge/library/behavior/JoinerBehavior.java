package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/**
 * Assembles a bus from individual bits: BUS bit {@code i} is input {@code i} (LSB first).
 * Ports are {@code BIT0..BIT(width-1)} in, {@code BUS} out.
 *
 * @param width the bus width of BUS
 */
public record JoinerBehavior(BitWidth width) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        LogicState[] bits = new LogicState[width.bits()];
        for (int i = 0; i < width.bits(); i++) {
            bits[i] = context.readInput(i).singleBit();
        }
        context.driveOutput(0, LogicVector.ofLsbFirst(bits));
    }
}
