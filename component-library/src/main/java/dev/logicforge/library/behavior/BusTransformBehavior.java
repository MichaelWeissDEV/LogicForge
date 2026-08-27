package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/** Reusable combinational implementation for width-changing and bit-order bus utilities. */
public record BusTransformBehavior(Kind kind, int inputWidth, int outputWidth)
        implements ComponentBehavior {

    public enum Kind { ZERO_EXTEND, SIGN_EXTEND, TRUNCATE, BIT_REVERSE, BYTE_SWAP }

    @Override
    public void evaluate(ComponentContext context) {
        LogicVector input = LogicOperations.asGateInput(context.readInput(0));
        LogicState[] bits = new LogicState[outputWidth];
        for (int bit = 0; bit < outputWidth; bit++) {
            bits[bit] = switch (kind) {
                case ZERO_EXTEND -> bit < inputWidth ? input.getBit(bit) : LogicState.ZERO;
                case SIGN_EXTEND -> bit < inputWidth ? input.getBit(bit)
                        : input.getBit(inputWidth - 1);
                case TRUNCATE -> input.getBit(bit);
                case BIT_REVERSE -> input.getBit(inputWidth - 1 - bit);
                case BYTE_SWAP -> byteSwapBit(input, bit);
            };
        }
        context.driveOutput(0, LogicVector.ofLsbFirst(bits));
    }

    private LogicState byteSwapBit(LogicVector input, int outputBit) {
        int byteCount = (inputWidth + 7) / 8;
        int source = (byteCount - 1 - outputBit / 8) * 8 + outputBit % 8;
        return source < inputWidth ? input.getBit(source) : LogicState.ZERO;
    }
}
