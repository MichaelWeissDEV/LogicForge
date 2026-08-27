package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/** Fast arithmetic building blocks whose four-state result is X for undefined operands. */
public record ArithmeticExpansionBehavior(Kind kind, BitWidth width) implements ComponentBehavior {
    public enum Kind { BARREL_SHIFT, MULTIPLY_UNSIGNED, ABSOLUTE }

    @Override
    public void evaluate(ComponentContext context) {
        switch (kind) {
            case BARREL_SHIFT -> shift(context);
            case MULTIPLY_UNSIGNED -> multiply(context);
            case ABSOLUTE -> absolute(context);
        }
    }

    private void shift(ComponentContext context) {
        var value = context.readInput(0).toUnsignedLong();
        var amount = context.readInput(1).toUnsignedLong();
        LogicState right = LogicOperations.asGateInput(context.readInput(2).singleBit());
        if (value.isEmpty() || amount.isEmpty() || !right.isDefined()) {
            context.driveOutput(0, LogicVector.repeat(LogicState.UNKNOWN, width));
            return;
        }
        int shift = (int) Math.min(amount.getAsLong(), width.bits());
        long result = right == LogicState.ONE
                ? value.getAsLong() >>> shift : value.getAsLong() << shift;
        context.driveOutput(0, LogicVector.fromUnsignedLong(result, width.bits()));
    }

    private void multiply(ComponentContext context) {
        var a = context.readInput(0).toUnsignedLong();
        var b = context.readInput(1).toUnsignedLong();
        int resultWidth = Math.min(64, width.bits() * 2);
        context.driveOutput(0, a.isPresent() && b.isPresent()
                ? LogicVector.fromUnsignedLong(a.getAsLong() * b.getAsLong(), resultWidth)
                : LogicVector.repeat(LogicState.UNKNOWN, resultWidth));
    }

    private void absolute(ComponentContext context) {
        LogicVector input = LogicOperations.asGateInput(context.readInput(0));
        if (!input.isFullyDefined()) {
            context.driveOutput(0, LogicVector.repeat(LogicState.UNKNOWN, width));
            context.driveOutput(1, LogicVector.UNKNOWN);
            return;
        }
        long raw = input.toUnsignedLong().orElseThrow();
        long signBit = 1L << (width.bits() - 1);
        long mask = width.bits() == 64 ? -1L : (1L << width.bits()) - 1;
        boolean negative = (raw & signBit) != 0;
        long result = negative ? (-raw) & mask : raw;
        context.driveOutput(0, LogicVector.fromUnsignedLong(result, width.bits()));
        context.driveOutput(1, LogicVector.single(
                LogicState.of(negative && raw == signBit)));
    }
}
