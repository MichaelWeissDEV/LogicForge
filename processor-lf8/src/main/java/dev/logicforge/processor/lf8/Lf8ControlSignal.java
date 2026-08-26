package dev.logicforge.processor.lf8;

/** Bit positions in the LF-8 prototype's microcode control word. */
public enum Lf8ControlSignal {
    PC_INCREMENT(0),
    IR_LOAD(1),
    DESTINATION_REGISTER_LOAD(2),
    SOURCE_REGISTER_LOAD(3),
    MAR_LOW_LOAD(4),
    MAR_HIGH_LOAD(5),
    REGISTER_FILE_WRITE(6),
    ALU_SOURCE(7),
    MOV_SOURCE(8),
    ALTERNATE_SOURCE(9),
    MEMORY_WRITE(10),
    MEMORY_READ(11),
    HALT(12),
    ALU_SUBTRACT(13),
    PC_LOAD(14),
    ADDRESS_FROM_MAR(15);

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
