package dev.logicforge.processor.lf8;

/** Bit positions in the LF-8 prototype's microcode control word. */
public enum Lf8ControlSignal {
    PC_INCREMENT(0),
    IR_LOAD(1),
    R0_LOAD_IMMEDIATE(2),
    R1_LOAD_IMMEDIATE(3),
    MAR_LOW_LOAD(4),
    MAR_HIGH_LOAD(5),
    ALU_LOAD(6),
    MEMORY_WRITE(7),
    HALT(8),
    ALU_SUBTRACT(9),
    MOVE_R0_FROM_R1(10),
    MOVE_R1_FROM_R0(11),
    LOAD_R0_FROM_MEMORY(12),
    PC_LOAD(13);

    private final int bit;

    Lf8ControlSignal(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }

    public int mask() {
        return 1 << bit;
    }

    public static int wordWidth() {
        return values().length;
    }
}
