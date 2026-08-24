package dev.logicforge.logic;

import java.util.Arrays;
import java.util.OptionalLong;

/**
 * An immutable vector of {@link LogicState}s — the value carried by a net.
 *
 * <p>Bit 0 is the least significant bit. Textual forms are written most significant bit
 * first, so {@code LogicVector.of("10")} has {@code getBit(0) == ZERO} and
 * {@code getBit(1) == ONE}.
 *
 * <p>The internal representation (an array of state ordinals) is deliberately hidden
 * behind the accessors so that a more compact backing store can be substituted later
 * without touching callers.
 */
public final class LogicVector {

    private static final LogicVector[] SINGLE_BIT = new LogicVector[LogicState.values().length];

    static {
        for (LogicState state : LogicState.values()) {
            SINGLE_BIT[state.ordinal()] = new LogicVector(new byte[]{(byte) state.ordinal()});
        }
    }

    /** The one-bit vectors, the values used by nearly every gate in a basic circuit. */
    public static final LogicVector ZERO = single(LogicState.ZERO);
    public static final LogicVector ONE = single(LogicState.ONE);
    public static final LogicVector UNKNOWN = single(LogicState.UNKNOWN);
    public static final LogicVector HIGH_IMPEDANCE = single(LogicState.HIGH_IMPEDANCE);

    /** Ordinals of {@link LogicState}, index 0 = least significant bit. */
    private final byte[] bits;

    private LogicVector(byte[] bits) {
        this.bits = bits;
    }

    /** The one-bit vector holding {@code state}. */
    public static LogicVector single(LogicState state) {
        return SINGLE_BIT[state.ordinal()];
    }

    /**
     * Parses a textual vector such as {@code "0101XXZ1"}, most significant bit first.
     */
    public static LogicVector of(String text) {
        if (text.isEmpty()) {
            throw new IllegalArgumentException("A logic vector needs at least one bit");
        }
        byte[] bits = new byte[text.length()];
        for (int i = 0; i < text.length(); i++) {
            LogicState state = LogicState.fromSymbol(text.charAt(text.length() - 1 - i));
            bits[i] = (byte) state.ordinal();
        }
        return new LogicVector(bits);
    }

    /** Builds a vector from states given most significant bit first. */
    public static LogicVector ofMsbFirst(LogicState... states) {
        byte[] bits = new byte[states.length];
        for (int i = 0; i < states.length; i++) {
            bits[i] = (byte) states[states.length - 1 - i].ordinal();
        }
        return validated(bits);
    }

    /** Builds a vector from states given least significant bit first. */
    public static LogicVector ofLsbFirst(LogicState... states) {
        byte[] bits = new byte[states.length];
        for (int i = 0; i < states.length; i++) {
            bits[i] = (byte) states[i].ordinal();
        }
        return validated(bits);
    }

    /** A vector of {@code width} bits all holding {@code state}. */
    public static LogicVector repeat(LogicState state, int width) {
        if (width == 1) {
            return single(state);
        }
        byte[] bits = new byte[width];
        Arrays.fill(bits, (byte) state.ordinal());
        return validated(bits);
    }

    /** A vector of {@code width} bits all holding {@code state}. */
    public static LogicVector repeat(LogicState state, BitWidth width) {
        return repeat(state, width.bits());
    }

    /** Encodes the low {@code width} bits of {@code value} as a fully defined vector. */
    public static LogicVector fromUnsignedLong(long value, int width) {
        if (width > 64) {
            throw new IllegalArgumentException("fromUnsignedLong supports at most 64 bits, got " + width);
        }
        byte[] bits = new byte[width];
        for (int i = 0; i < width; i++) {
            bits[i] = (byte) (((value >>> i) & 1L) == 1L ? LogicState.ONE.ordinal() : LogicState.ZERO.ordinal());
        }
        return validated(bits);
    }

    private static LogicVector validated(byte[] bits) {
        if (bits.length < 1 || bits.length > BitWidth.MAX_BITS) {
            throw new IllegalArgumentException("Vector width must be in 1.." + BitWidth.MAX_BITS
                    + ", was " + bits.length);
        }
        return new LogicVector(bits);
    }

    /** Number of bits in this vector. */
    public int width() {
        return bits.length;
    }

    public BitWidth bitWidth() {
        return BitWidth.of(bits.length);
    }

    /** The bit at {@code index}, counted from the least significant bit. */
    public LogicState getBit(int index) {
        if (index < 0 || index >= bits.length) {
            throw new IndexOutOfBoundsException("Bit " + index + " of a " + bits.length + "-bit vector");
        }
        return LogicState.values()[bits[index]];
    }

    /** Convenience accessor for the common one-bit case. */
    public LogicState singleBit() {
        if (bits.length != 1) {
            throw new WidthMismatchException(1, bits.length);
        }
        return LogicState.values()[bits[0]];
    }

    /** A copy of this vector with the bit at {@code index} replaced. */
    public LogicVector withBit(int index, LogicState state) {
        if (getBit(index) == state) {
            return this;
        }
        byte[] copy = bits.clone();
        copy[index] = (byte) state.ordinal();
        return new LogicVector(copy);
    }

    /** {@code width} bits starting at {@code fromIndex}, counted from the LSB. */
    public LogicVector slice(int fromIndex, int width) {
        if (fromIndex < 0 || width < 1 || fromIndex + width > bits.length) {
            throw new IndexOutOfBoundsException(
                    "slice(" + fromIndex + ", " + width + ") of a " + bits.length + "-bit vector");
        }
        return new LogicVector(Arrays.copyOfRange(bits, fromIndex, fromIndex + width));
    }

    /**
     * Concatenates two vectors. {@code this} becomes the more significant part, so
     * {@code a.concat(b).toBinaryString()} equals
     * {@code a.toBinaryString() + b.toBinaryString()}.
     */
    public LogicVector concat(LogicVector lessSignificant) {
        byte[] combined = new byte[bits.length + lessSignificant.bits.length];
        System.arraycopy(lessSignificant.bits, 0, combined, 0, lessSignificant.bits.length);
        System.arraycopy(bits, 0, combined, lessSignificant.bits.length, bits.length);
        return validated(combined);
    }

    /** {@code true} if every bit is {@code 0} or {@code 1}. */
    public boolean isFullyDefined() {
        for (byte bit : bits) {
            if (!LogicState.values()[bit].isDefined()) {
                return false;
            }
        }
        return true;
    }

    /** {@code true} if no bit is driven. */
    public boolean isHighImpedance() {
        for (byte bit : bits) {
            if (bit != (byte) LogicState.HIGH_IMPEDANCE.ordinal()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The unsigned numeric value, present only if the vector is fully defined and at most
     * 64 bits wide.
     */
    public OptionalLong toUnsignedLong() {
        if (bits.length > 64 || !isFullyDefined()) {
            return OptionalLong.empty();
        }
        long value = 0;
        for (int i = 0; i < bits.length; i++) {
            if (bits[i] == (byte) LogicState.ONE.ordinal()) {
                value |= 1L << i;
            }
        }
        return OptionalLong.of(value);
    }

    /** Throws {@link WidthMismatchException} unless this vector is {@code expected} bits wide. */
    public LogicVector requireWidth(int expected) {
        if (bits.length != expected) {
            throw new WidthMismatchException(expected, bits.length);
        }
        return this;
    }

    public LogicVector requireWidth(BitWidth expected) {
        return requireWidth(expected.bits());
    }

    /** The vector written most significant bit first, e.g. {@code "0101XXZ1"}. */
    public String toBinaryString() {
        StringBuilder text = new StringBuilder(bits.length);
        for (int i = bits.length - 1; i >= 0; i--) {
            text.append(LogicState.values()[bits[i]].symbol());
        }
        return text.toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LogicVector vector && Arrays.equals(bits, vector.bits);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bits);
    }

    @Override
    public String toString() {
        return toBinaryString();
    }
}
