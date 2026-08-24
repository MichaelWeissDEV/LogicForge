package dev.logicforge.circuit.component;

/** Whether a port reads a net, drives it, or both. */
public enum PortDirection {

    /** Reads the net it is attached to. */
    INPUT,
    /** Drives the net it is attached to. */
    OUTPUT,
    /** Reads and drives — needed later for bidirectional buses. */
    INOUT;

    public boolean canDrive() {
        return this == OUTPUT || this == INOUT;
    }

    public boolean canRead() {
        return this == INPUT || this == INOUT;
    }
}
