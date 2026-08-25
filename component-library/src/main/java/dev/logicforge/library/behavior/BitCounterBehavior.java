package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;
import java.util.OptionalLong;

/**
 * Counts bits of A: how many leading (MSB-side) or trailing (LSB-side) zeros precede the
 * first set bit, or how many bits are set in total (population count / Hamming weight). If
 * A is not fully defined, COUNT is X — a count over an unknown value is itself unknown.
 *
 * <p>Ports are {@code A} in, {@code COUNT} out. COUNT ranges 0..width inclusive, so it is
 * one bit wider than the minimum needed for {@code width - 1} alone.
 *
 * @param width the bus width of A
 * @param kind which count to produce
 */
public record BitCounterBehavior(BitWidth width, Kind kind) implements ComponentBehavior {

    public enum Kind {
        LEADING_ZEROS, TRAILING_ZEROS, POPULATION_COUNT
    }

    /** Bits needed to represent a count from 0 up to and including {@code width} itself. */
    public static int countWidth(int width) {
        return ShiftBehavior.shiftAmountWidth(width);
    }

    @Override
    public void evaluate(ComponentContext context) {
        OptionalLong a = context.readInput(0).toUnsignedLong();
        int countWidth = countWidth(width.bits());
        if (a.isEmpty()) {
            context.driveOutput(0, LogicVector.repeat(dev.logicforge.logic.LogicState.UNKNOWN, countWidth));
            return;
        }
        long value = a.getAsLong();
        int bits = width.bits();
        int count = switch (kind) {
            case LEADING_ZEROS -> leadingZeros(value, bits);
            case TRAILING_ZEROS -> trailingZeros(value, bits);
            case POPULATION_COUNT -> Long.bitCount(value);
        };
        context.driveOutput(0, LogicVector.fromUnsignedLong(count, countWidth));
    }

    private static int leadingZeros(long value, int bits) {
        for (int i = bits - 1; i >= 0; i--) {
            if (((value >>> i) & 1L) != 0) {
                return bits - 1 - i;
            }
        }
        return bits;
    }

    private static int trailingZeros(long value, int bits) {
        for (int i = 0; i < bits; i++) {
            if (((value >>> i) & 1L) != 0) {
                return i;
            }
        }
        return bits;
    }

    @Override
    public ComponentRuntimeState createState() {
        return ComponentRuntimeState.STATELESS;
    }
}
