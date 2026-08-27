package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/** Bidirectional tri-state bus transceiver: DIR=1 drives A to B, DIR=0 drives B to A. */
public record BusTransceiverBehavior(BitWidth width) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        LogicState direction = LogicOperations.asGateInput(context.readInput(0).singleBit());
        LogicState enable = LogicOperations.asGateInput(context.readInput(1).singleBit());
        LogicVector z = LogicVector.repeat(LogicState.HIGH_IMPEDANCE, width);
        LogicVector a = context.readInput(2);
        LogicVector b = context.readInput(3);

        LogicVector enabledA = switch (direction) {
            case ZERO -> b;
            case ONE -> z;
            case UNKNOWN, HIGH_IMPEDANCE -> StatefulControlPolicy.merge(b, z);
        };
        LogicVector enabledB = switch (direction) {
            case ZERO -> z;
            case ONE -> a;
            case UNKNOWN, HIGH_IMPEDANCE -> StatefulControlPolicy.merge(z, a);
        };
        LogicVector driveA = StatefulControlPolicy.choose(enable, z, enabledA);
        LogicVector driveB = StatefulControlPolicy.choose(enable, z, enabledB);
        context.driveOutput(0, driveA);
        context.driveOutput(1, driveB);
    }
}
