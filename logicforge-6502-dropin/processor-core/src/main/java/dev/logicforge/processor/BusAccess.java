package dev.logicforge.processor;

/** One externally visible byte access performed by a functional processor model. */
public record BusAccess(long ordinal, Type type, int address, int value) {
    public enum Type { READ, WRITE }

    public BusAccess {
        address &= 0xffff;
        value &= 0xff;
    }
}
