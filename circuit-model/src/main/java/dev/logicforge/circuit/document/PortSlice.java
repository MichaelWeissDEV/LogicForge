package dev.logicforge.circuit.document;

/**
 * Which part of a port a connection endpoint refers to.
 *
 * <p>WHOLE means the entire bus port (the default, backward-compatible case).
 * BIT(index) means one specific bit of a bus port, where index 0 is the LSB.
 */
public sealed interface PortSlice permits PortSlice.Whole, PortSlice.Bit {

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
}
