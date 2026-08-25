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

    /** True if this endpoint refers to the whole bus port. */
    public boolean isWhole() {
        return slice instanceof PortSlice.Whole;
    }

    /** True if this endpoint refers to a single bit. */
    public boolean isBit() {
        return slice instanceof PortSlice.Bit;
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
        };
    }
}
