package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import java.util.OptionalLong;

/**
 * An N-bit unsigned adder: SUM = A + B + CIN, COUT is the carry out of the top bit. If any
 * operand is not fully defined, both outputs are X — this behaves as a fast whole-value
 * computation (like {@code CounterBehavior}) rather than propagating X bit by bit.
 *
 * <p>Ports are {@code A, B, CIN} in, {@code SUM, COUT} out.
 *
 * @param width the bus width of A, B and SUM
 */
public record AdderBehavior(BitWidth width) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        OptionalLong a = context.readInput(0).toUnsignedLong();
        OptionalLong b = context.readInput(1).toUnsignedLong();
        LogicState cin = context.readInput(2).singleBit();
        if (a.isEmpty() || b.isEmpty() || !cin.isDefined()) {
            context.driveOutput(0, LogicVector.repeat(LogicState.UNKNOWN, width));
            context.driveOutput(1, LogicVector.UNKNOWN);
            return;
        }
        long cinBit = cin == LogicState.ONE ? 1 : 0;
        long sum;
        boolean carryOut;
        if (width.bits() == 64) {
            long step1 = a.getAsLong() + b.getAsLong();
            boolean carry1 = Long.compareUnsigned(step1, a.getAsLong()) < 0;
            sum = step1 + cinBit;
            boolean carry2 = cinBit == 1 && Long.compareUnsigned(sum, step1) < 0;
            carryOut = carry1 || carry2;
        } else {
            long mask = (1L << width.bits()) - 1;
            sum = a.getAsLong() + b.getAsLong() + cinBit;
            carryOut = (sum & ~mask) != 0;
            sum &= mask;
        }
        context.driveOutput(0, LogicVector.fromUnsignedLong(sum, width.bits()));
        context.driveOutput(1, LogicVector.single(LogicState.of(carryOut)));
    }
}
