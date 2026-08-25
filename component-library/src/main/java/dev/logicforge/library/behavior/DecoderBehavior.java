package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import java.util.OptionalLong;

/**
 * An n-to-2<sup>n</sup> decoder: while ENABLE is 1, drives exactly the output SEL selects
 * high and every other output low. While disabled, every output is low. An undefined SEL
 * (while enabled) drives every output to X, since which one should be high cannot be known.
 *
 * <p>Ports are {@code SEL, ENABLE} in, {@code OUT0..OUT(2^n-1)} out.
 *
 * @param outputCount how many outputs this instance has ({@code 2^selectBits})
 */
public record DecoderBehavior(int outputCount) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        LogicState enable = LogicOperations.asGateInput(context.readInput(1).singleBit());
        if (enable == LogicState.ZERO) {
            for (int i = 0; i < outputCount; i++) {
                context.driveOutput(i, LogicVector.ZERO);
            }
            return;
        }
        OptionalLong selected = enable == LogicState.ONE
                ? context.readInput(0).toUnsignedLong()
                : java.util.OptionalLong.empty();
        for (int i = 0; i < outputCount; i++) {
            LogicState value = selected.isEmpty()
                    ? LogicState.UNKNOWN
                    : LogicState.of(selected.getAsLong() == i);
            context.driveOutput(i, LogicVector.single(value));
        }
    }
}
