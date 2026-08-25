package dev.logicforge.circuit.document;

/**
 * Which part of a port a connection endpoint refers to.
 *
 * <p>WHOLE means the entire bus port (the default, backward-compatible case).
 * BIT(index) means one specific bit of a bus port, where index 0 is the LSB.
 * RANGE(msb, lsb) means a contiguous portion of the port.
 */
public sealed interface PortSlice permits PortSlice.Whole, PortSlice.Bit, PortSlice.Range {

    /** The whole port — the default, works for both 1-bit and multi-bit ports. */
    record Whole() implements PortSlice {
        /** Singleton, no state. */
        public static final Whole INSTANCE = new Whole();
    }

    /**
     * A single bit within a bus port.
     *
     * @param index bit index, 0 = LSB
     */
    record Bit(int index) implements PortSlice {
        public Bit {
            if (index < 0) throw new IllegalArgumentException("Bit index must be non-negative");
        }
    }

    /**
     * A contiguous range within a bus, written most-significant bit first.
     *
     * @param msb highest included bit index
     * @param lsb lowest included bit index
     */
    record Range(int msb, int lsb) implements PortSlice {
        public Range {
            if (lsb < 0) {
                throw new IllegalArgumentException("Range LSB must be non-negative");
            }
            if (msb < lsb) {
                throw new IllegalArgumentException("Range MSB must be greater than or equal to LSB");
            }
        }

        public int width() {
            return msb - lsb + 1;
        }
    }
}
