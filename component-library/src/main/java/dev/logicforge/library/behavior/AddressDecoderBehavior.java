package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/** SELECT=1 exactly when {@code (ADDRESS & MASK) == (BASE & MASK)}. */
public record AddressDecoderBehavior(BitWidth width, LogicVector base, LogicVector mask)
        implements ComponentBehavior {

    public AddressDecoderBehavior {
        base = base.requireWidth(width);
        mask = mask.requireWidth(width);
    }

    @Override
    public void evaluate(ComponentContext context) {
        LogicVector address = LogicOperations.asGateInput(context.readInput(0).requireWidth(width));
        boolean relevantUnknown = false;
        for (int bit = 0; bit < width.bits(); bit++) {
            if (mask.getBit(bit) != LogicState.ONE) {
                continue;
            }
            LogicState actual = address.getBit(bit);
            if (!actual.isDefined()) {
                relevantUnknown = true;
            } else if (actual != base.getBit(bit)) {
                context.driveOutput(0, LogicVector.ZERO);
                return;
            }
        }
        context.driveOutput(0, relevantUnknown ? LogicVector.UNKNOWN : LogicVector.ONE);
    }
}
