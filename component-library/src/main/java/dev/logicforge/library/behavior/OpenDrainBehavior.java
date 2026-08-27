package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/** Open-drain bus driver: zero pulls low, one releases, and X/Z is conservative X. */
public record OpenDrainBehavior(BitWidth width) implements ComponentBehavior {
    @Override
    public void evaluate(ComponentContext context) {
        LogicVector input = context.readInput(0);
        LogicState[] output = new LogicState[width.bits()];
        for (int bit = 0; bit < output.length; bit++) {
            output[bit] = switch (LogicOperations.asGateInput(input.getBit(bit))) {
                case ZERO -> LogicState.ZERO;
                case ONE -> LogicState.HIGH_IMPEDANCE;
                default -> LogicState.UNKNOWN;
            };
        }
        context.driveOutput(0, LogicVector.ofLsbFirst(output));
    }
}
