package dev.logicforge.circuit.document;

/**
 * One end of a {@link Connection}: a port reference plus which part of the port this
 * wire connects to.
 *
 * <p>The vast majority of connections use {@link PortSlice.Whole#INSTANCE}, which is
 * backward-compatible with the old PortReference-only model.
 */
public record PortEndpoint(PortReference port, PortSlice slice) {

    public PortEndpoint {
        if (port == null || slice == null) {
            throw new IllegalArgumentException("PortEndpoint requires a port and a slice");
        }
    }

    /** Convenience: creates a whole-bus endpoint. */
    public static PortEndpoint whole(PortReference port) {
        return new PortEndpoint(port, PortSlice.Whole.INSTANCE);
    }

    /** Convenience: creates a bit endpoint. */
    public static PortEndpoint bit(PortReference port, int bitIndex) {
        return new PortEndpoint(port, new PortSlice.Bit(bitIndex));
    }

    /** Convenience: creates a contiguous range endpoint. */
    public static PortEndpoint range(PortReference port, int msb, int lsb) {
        return new PortEndpoint(port, new PortSlice.Range(msb, lsb));
    }

    /** True if this endpoint refers to the whole bus port. */
    public boolean isWhole() {
        return slice instanceof PortSlice.Whole;
    }

    /** True if this endpoint refers to a single bit. */
    public boolean isBit() {
        return slice instanceof PortSlice.Bit;
    }

    /** True if this endpoint refers to a contiguous range. */
    public boolean isRange() {
        return slice instanceof PortSlice.Range;
    }

    /** Number of bits selected, given the width of the logical port. */
    public int selectedWidth(int portWidth) {
        return switch (slice) {
            case PortSlice.Whole ignored -> portWidth;
            case PortSlice.Bit ignored -> 1;
            case PortSlice.Range range -> range.width();
        };
    }

    /** The component id from the port reference. */
    public java.util.UUID componentId() {
        return port.componentId();
    }

    /** The port name from the port reference. */
    public String portName() {
        return port.portName();
    }

    @Override
    public String toString() {
        return switch (slice) {
            case PortSlice.Whole w -> port.toString();
            case PortSlice.Bit b -> port + "[" + b.index() + "]";
            case PortSlice.Range range -> port + "[" + range.msb() + ":" + range.lsb() + "]";
        };
    }
}
