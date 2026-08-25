package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;
import java.util.OptionalLong;

/**
 * Signed (two's-complement) overflow detector for an addition or subtraction performed
 * elsewhere — e.g. by a ripple-carry adder built from discrete full adders rather than the
 * built-in ALU. Reusable wherever A, B and the already-computed RESULT are all a wire's
 * bus values, independent of how RESULT was produced.
 *
 * <p>Ports are {@code A, B, RESULT} in, {@code OVERFLOW} out. When {@link #subtract()} is
 * {@code false}, {@code RESULT} is assumed to be {@code A + B}; when {@code true}, it is
 * assumed to be {@code A - B}. Overflow means the result's sign cannot be correct for
 * signed operands of this width.
 *
 * @param width the bus width of A, B and RESULT
 * @param subtract {@code false} for addition (A + B), {@code true} for subtraction (A - B)
 */
public record OverflowDetectorBehavior(BitWidth width, boolean subtract) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        OptionalLong a = context.readInput(0).toUnsignedLong();
        OptionalLong b = context.readInput(1).toUnsignedLong();
        OptionalLong result = context.readInput(2).toUnsignedLong();
        if (a.isEmpty() || b.isEmpty() || result.isEmpty()) {
            context.driveOutput(0, LogicVector.UNKNOWN);
            return;
        }
        long aVal = a.getAsLong();
        long bVal = b.getAsLong();
        long resultVal = result.getAsLong();
        int bits = width.bits();
        long signBit = bits == 64 ? Long.MIN_VALUE : 1L << (bits - 1);

        // ADD: both operands share a sign that the result doesn't.
        // SUB (A - B): A and B differ in sign, and the result doesn't match A's sign.
        boolean overflow = subtract
                ? ((aVal ^ bVal) & (aVal ^ resultVal) & signBit) != 0
                : ((aVal ^ resultVal) & (bVal ^ resultVal) & signBit) != 0;
        context.driveOutput(0, LogicVector.single(LogicState.of(overflow)));
    }

    @Override
    public ComponentRuntimeState createState() {
        return ComponentRuntimeState.STATELESS;
    }
}
