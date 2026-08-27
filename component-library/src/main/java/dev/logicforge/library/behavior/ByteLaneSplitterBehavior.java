package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/** Splits a bus into low-byte-first 8-bit lanes, zero-padding the final partial lane. */
public record ByteLaneSplitterBehavior(int width) implements ComponentBehavior {
    @Override
    public void evaluate(ComponentContext context) {
        LogicVector input = LogicOperations.asGateInput(context.readInput(0));
        int lanes = (width + 7) / 8;
        for (int lane = 0; lane < lanes; lane++) {
            LogicState[] bits = new LogicState[8];
            for (int bit = 0; bit < 8; bit++) {
                int source = lane * 8 + bit;
                bits[bit] = source < width ? input.getBit(source) : LogicState.ZERO;
            }
            context.driveOutput(lane, LogicVector.ofLsbFirst(bits));
        }
    }
}
