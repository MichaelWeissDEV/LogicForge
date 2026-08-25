package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import java.util.OptionalLong;

/**
 * An N-bit unsigned subtractor: DIFF = A - B - BIN, BORROW is 1 when the subtraction would
 * go negative. If any operand is not fully defined, both outputs are X.
 *
 * <p>Ports are {@code A, B, BIN} in, {@code DIFF, BORROW} out.
 *
 * @param width the bus width of A, B and DIFF
 */
public record SubtractorBehavior(BitWidth width) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        OptionalLong a = context.readInput(0).toUnsignedLong();
        OptionalLong b = context.readInput(1).toUnsignedLong();
        LogicState borrowIn = context.readInput(2).singleBit();
        if (a.isEmpty() || b.isEmpty() || !borrowIn.isDefined()) {
            context.driveOutput(0, LogicVector.repeat(LogicState.UNKNOWN, width));
            context.driveOutput(1, LogicVector.UNKNOWN);
            return;
        }
        long borrowInBit = borrowIn == LogicState.ONE ? 1 : 0;
        long step1 = a.getAsLong() - b.getAsLong();
        boolean borrow1 = Long.compareUnsigned(a.getAsLong(), b.getAsLong()) < 0;
        long result = step1 - borrowInBit;
        boolean borrow2 = borrowInBit == 1 && step1 == 0;
        boolean borrowOut = borrow1 || borrow2;
        long mask = width.bits() == 64 ? -1L : (1L << width.bits()) - 1;
        result &= mask;
        context.driveOutput(0, LogicVector.fromUnsignedLong(result, width.bits()));
        context.driveOutput(1, LogicVector.single(LogicState.of(borrowOut)));
    }
}
