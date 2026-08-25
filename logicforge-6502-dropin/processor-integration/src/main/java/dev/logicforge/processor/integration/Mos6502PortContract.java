package dev.logicforge.processor.integration;

/**
 * Compact simulator-facing port order for the transistor 6502 behavior.
 *
 * <p>This is deliberately separate from the physical DIP-40 package metadata. The circuit
 * model can initially use buses for efficient simulation and a later package renderer can
 * fan ADDRESS/DATA out to the physical A0..A15/D0..D7 pins without changing the silicon
 * model.</p>
 */
public final class Mos6502PortContract {
    // ComponentContext input order (all canRead() ports in declaration order)
    public static final int IN_CLK = 0;
    public static final int IN_RESET_N = 1;
    public static final int IN_IRQ_N = 2;
    public static final int IN_NMI_N = 3;
    public static final int IN_RDY = 4;
    public static final int IN_SO_N = 5;
    public static final int IN_DATA = 6;      // 8-bit INOUT
    public static final int INPUT_COUNT = 7;

    // ComponentContext output order (all canDrive() ports in declaration order)
    public static final int OUT_DATA = 0;     // 8-bit INOUT
    public static final int OUT_ADDRESS = 1;  // 16-bit
    public static final int OUT_RW = 2;
    public static final int OUT_SYNC = 3;
    public static final int OUT_PHI1 = 4;
    public static final int OUT_PHI2 = 5;
    public static final int OUTPUT_COUNT = 6;

    private Mos6502PortContract() { }
}
