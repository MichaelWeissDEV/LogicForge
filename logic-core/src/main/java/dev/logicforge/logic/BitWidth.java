package dev.logicforge.logic;

/**
 * The width of a signal in bits. A separate type so that widths cannot be silently
 * confused with other integers, and so width mismatches are caught explicitly.
 */
public record BitWidth(int bits) implements Comparable<BitWidth> {

    /** Upper bound for a single signal. Generous, but keeps accidental huge widths out. */
    public static final int MAX_BITS = 1024;

    private static final BitWidth[] CACHE = new BitWidth[65];

    static {
        for (int i = 1; i < CACHE.length; i++) {
            CACHE[i] = new BitWidth(i);
        }
    }

    /** A single bit — by far the most common width. */
    public static final BitWidth ONE = of(1);

    public BitWidth {
        if (bits < 1 || bits > MAX_BITS) {
            throw new IllegalArgumentException("Bit width must be in 1.." + MAX_BITS + ", was " + bits);
        }
    }

    public static BitWidth of(int bits) {
        if (bits >= 1 && bits < CACHE.length && CACHE[bits] != null) {
            return CACHE[bits];
        }
        return new BitWidth(bits);
    }

    public boolean isSingleBit() {
        return bits == 1;
    }

    @Override
    public int compareTo(BitWidth other) {
        return Integer.compare(bits, other.bits);
    }

    @Override
    public String toString() {
        return bits + (bits == 1 ? " bit" : " bits");
    }
}
