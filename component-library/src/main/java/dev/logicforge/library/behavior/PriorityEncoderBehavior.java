package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/**
 * A priority encoder: OUT is the index of the highest-indexed input that is high, and VALID
 * is 1 if any input is high at all. Unlike {@link EncoderBehavior}, several active inputs
 * are not an error — the higher index wins, matching how a real priority encoder IC (e.g. a
 * 74148) resolves simultaneous requests. While VALID is 0, OUT is all zero bits rather than
 * X, since "no request" is itself a well defined outcome.
 *
 * <p>Ports are {@code IN0..IN(n-1)} in, {@code OUT, VALID} out.
 *
 * @param inputCount how many inputs this instance has
 */
public record PriorityEncoderBehavior(int inputCount) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        int width = MuxBehavior.selectWidth(inputCount);
        int highestActive = -1;
        boolean unknownAboveHighest = false;
        for (int i = 0; i < inputCount; i++) {
            LogicState value = LogicOperations.asGateInput(context.readInput(i).singleBit());
            if (value == LogicState.ONE) {
                highestActive = i;
                unknownAboveHighest = false;
            } else if (value != LogicState.ZERO && highestActive < i) {
                unknownAboveHighest = true;
            }
        }
        if (highestActive < 0 && !unknownAboveHighest) {
            context.driveOutput(0, LogicVector.repeat(LogicState.ZERO, width));
            context.driveOutput(1, LogicVector.ZERO);
        } else if (unknownAboveHighest) {
            context.driveOutput(0, LogicVector.repeat(LogicState.UNKNOWN, width));
            context.driveOutput(1, LogicVector.UNKNOWN);
        } else {
            context.driveOutput(0, LogicVector.fromUnsignedLong(highestActive, width));
            context.driveOutput(1, LogicVector.ONE);
        }
    }
}
