package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/** Digital GPIO bank with per-bit direction: 1 drives DATA_OUT, 0 releases the pin. */
public record GpioBehavior(BitWidth width) implements ComponentBehavior {
    @Override
    public void evaluate(ComponentContext context) {
        LogicVector data = context.readInput(0);
        LogicVector direction = LogicOperations.asGateInput(context.readInput(1));
        LogicState[] drive = new LogicState[width.bits()];
        for (int bit = 0; bit < drive.length; bit++) {
            drive[bit] = switch (direction.getBit(bit)) {
                case ZERO -> LogicState.HIGH_IMPEDANCE;
                case ONE -> data.getBit(bit);
                default -> data.getBit(bit) == LogicState.HIGH_IMPEDANCE
                        ? LogicState.HIGH_IMPEDANCE : LogicState.UNKNOWN;
            };
        }
        context.driveOutput(0, LogicVector.ofLsbFirst(drive));
        context.driveOutput(1, context.readInput(2));
    }
}
