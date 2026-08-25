package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/**
 * A plain (non-priority) encoder: if exactly one of the {@code inputCount} inputs is high,
 * OUT is its binary index. If none or more than one are high, the encoding is ambiguous and
 * OUT is X. {@link PriorityEncoderBehavior} is the variant that resolves multiple active
 * inputs instead of giving up.
 *
 * <p>Ports are {@code IN0..IN(n-1)} in, {@code OUT} out.
 *
 * @param inputCount how many inputs this instance has
 */
public record EncoderBehavior(int inputCount) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        int activeIndex = -1;
        boolean ambiguous = false;
        for (int i = 0; i < inputCount; i++) {
            LogicState value = LogicOperations.asGateInput(context.readInput(i).singleBit());
            if (value == LogicState.ONE) {
                if (activeIndex >= 0) {
                    ambiguous = true;
                }
                activeIndex = i;
            } else if (value != LogicState.ZERO) {
                ambiguous = true;
            }
        }
        int width = MuxBehavior.selectWidth(inputCount);
        if (ambiguous || activeIndex < 0) {
            context.driveOutput(0, LogicVector.repeat(LogicState.UNKNOWN, width));
        } else {
            context.driveOutput(0, LogicVector.fromUnsignedLong(activeIndex, width));
        }
    }
}
