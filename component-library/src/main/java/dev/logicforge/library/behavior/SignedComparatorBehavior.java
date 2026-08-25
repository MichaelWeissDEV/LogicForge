package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;
import java.util.OptionalLong;

/**
 * A two's-complement signed comparator: LT, EQ and GT for {@code A} compared to {@code B},
 * both read as signed values of this component's configured width. If either side is not
 * fully defined, all three outputs are X — a comparison cannot be partially known.
 *
 * <p>Ports are {@code A, B} in, {@code LT, EQ, GT} out.
 *
 * @param width the bus width of A and B
 */
public record SignedComparatorBehavior(BitWidth width) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        OptionalLong a = context.readInput(0).toUnsignedLong();
        OptionalLong b = context.readInput(1).toUnsignedLong();
        if (a.isEmpty() || b.isEmpty()) {
            context.driveOutput(0, LogicVector.UNKNOWN);
            context.driveOutput(1, LogicVector.UNKNOWN);
            context.driveOutput(2, LogicVector.UNKNOWN);
            return;
        }
        int cmp = Long.compare(signExtend(a.getAsLong()), signExtend(b.getAsLong()));
        context.driveOutput(0, LogicVector.single(LogicState.of(cmp < 0)));
        context.driveOutput(1, LogicVector.single(LogicState.of(cmp == 0)));
        context.driveOutput(2, LogicVector.single(LogicState.of(cmp > 0)));
    }

    private long signExtend(long unsignedValue) {
        int bits = width.bits();
        if (bits >= 64) {
            return unsignedValue;
        }
        long signBit = 1L << (bits - 1);
        return (unsignedValue ^ signBit) - signBit;
    }

    @Override
    public ComponentRuntimeState createState() {
        return ComponentRuntimeState.STATELESS;
    }
}
