package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import java.util.OptionalLong;

/**
 * A combined add/subtract unit: RESULT = A + B when SUB is 0, A - B when SUB is 1, using
 * the classic two's-complement trick of inverting B and using SUB itself as the carry-in.
 * COUT is a carry when adding and the complement of a borrow when subtracting — the same
 * convention a 74181-style ALU uses, so it reads as "1 means no borrow occurred" while
 * subtracting.
 *
 * <p>Ports are {@code A, B, SUB} in, {@code RESULT, COUT} out.
 *
 * @param width the bus width of A, B and RESULT
 */
public record AddSubBehavior(BitWidth width) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        OptionalLong a = context.readInput(0).toUnsignedLong();
        OptionalLong b = context.readInput(1).toUnsignedLong();
        LogicState sub = context.readInput(2).singleBit();
        if (a.isEmpty() || b.isEmpty() || !sub.isDefined()) {
            context.driveOutput(0, LogicVector.repeat(LogicState.UNKNOWN, width));
            context.driveOutput(1, LogicVector.UNKNOWN);
            return;
        }
        long mask = width.bits() == 64 ? -1L : (1L << width.bits()) - 1;
        long bValue = sub == LogicState.ONE ? (~b.getAsLong()) & mask : b.getAsLong();
        long cinBit = sub == LogicState.ONE ? 1 : 0;

        long sum;
        boolean carryOut;
        if (width.bits() == 64) {
            long step1 = a.getAsLong() + bValue;
            boolean carry1 = Long.compareUnsigned(step1, a.getAsLong()) < 0;
            sum = step1 + cinBit;
            boolean carry2 = cinBit == 1 && Long.compareUnsigned(sum, step1) < 0;
            carryOut = carry1 || carry2;
        } else {
            sum = a.getAsLong() + bValue + cinBit;
            carryOut = (sum & ~mask) != 0;
            sum &= mask;
        }
        context.driveOutput(0, LogicVector.fromUnsignedLong(sum, width.bits()));
        context.driveOutput(1, LogicVector.single(LogicState.of(carryOut)));
    }
}
