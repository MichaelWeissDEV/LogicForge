package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import java.util.OptionalLong;

/**
 * A logical, arithmetic or rotating shifter: OUT = A shifted (or rotated) by SHIFT
 * positions. A plain shift fills the vacated bits with zero (logical) or A's sign bit
 * (arithmetic right only), and a shift amount at or beyond the width shifts every defined
 * bit out; a rotate wraps the bits shifted off one end back onto the other, so the amount
 * wraps modulo the width instead of clamping.
 *
 * <p>Ports are {@code A, SHIFT} in, {@code OUT} out.
 *
 * @param width the bus width of A and OUT
 * @param direction which way, and whether the right shift is arithmetic or the ends wrap
 */
public record ShiftBehavior(BitWidth width, Direction direction) implements ComponentBehavior {

    public enum Direction {
        LEFT, RIGHT_LOGICAL, RIGHT_ARITHMETIC, ROTATE_LEFT, ROTATE_RIGHT
    }

    /** Bits needed for a shift amount from 0 up to and including {@code width} itself. */
    public static int shiftAmountWidth(int width) {
        return Math.max(1, 32 - Integer.numberOfLeadingZeros(width));
    }

    @Override
    public void evaluate(ComponentContext context) {
        OptionalLong a = context.readInput(0).toUnsignedLong();
        OptionalLong shift = context.readInput(1).toUnsignedLong();
        if (a.isEmpty() || shift.isEmpty()) {
            context.driveOutput(0, LogicVector.repeat(LogicState.UNKNOWN, width));
            return;
        }
        int bits = width.bits();
        long mask = bits == 64 ? -1L : (1L << bits) - 1;
        long value = a.getAsLong();
        long amount = Math.min(shift.getAsLong(), bits);
        long result;
        switch (direction) {
            case LEFT -> result = amount >= 64 ? 0 : (value << amount) & mask;
            case RIGHT_LOGICAL -> result = amount >= 64 ? 0 : (value >>> amount) & mask;
            case RIGHT_ARITHMETIC -> {
                boolean signBit = ((value >>> (bits - 1)) & 1) == 1;
                if (amount >= bits) {
                    result = signBit ? mask : 0;
                } else {
                    long signExtended = signBit ? (value | ~mask) : value;
                    result = (signExtended >> amount) & mask;
                }
            }
            case ROTATE_LEFT -> {
                long rotate = shift.getAsLong() % bits;
                result = ((value << rotate) | (value >>> (bits - rotate))) & mask;
            }
            case ROTATE_RIGHT -> {
                long rotate = shift.getAsLong() % bits;
                result = ((value >>> rotate) | (value << (bits - rotate))) & mask;
            }
            default -> throw new IllegalStateException("Unreachable: " + direction);
        }
        context.driveOutput(0, LogicVector.fromUnsignedLong(result, bits));
    }
}
