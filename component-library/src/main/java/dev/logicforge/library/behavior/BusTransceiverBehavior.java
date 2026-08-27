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
        LogicVector x = LogicVector.repeat(LogicState.UNKNOWN, width);
        LogicVector driveA;
        LogicVector driveB;
        if (enable == LogicState.ZERO) {
            driveA = z;
            driveB = z;
        } else if (enable == LogicState.ONE && direction == LogicState.ONE) {
            driveA = z;
            driveB = LogicOperations.asGateInput(context.readInput(2));
        } else if (enable == LogicState.ONE && direction == LogicState.ZERO) {
            driveA = LogicOperations.asGateInput(context.readInput(3));
            driveB = z;
        } else {
            driveA = x;
            driveB = x;
        }
        context.driveOutput(0, driveA);
        context.driveOutput(1, driveB);
    }
}
