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
    PC_LOAD(17),
    ADDRESS_FROM_MAR(18),
    FLAGS_LOAD(19),
    FLAGS_PRESERVE_CARRY(20),
    FLAGS_PRESERVE_OVERFLOW(21),
    ALU_CARRY_IN(22),
    ALU_B_ONE(23),
    ADDRESS_FROM_SP(24),
    SP_INCREMENT(25),
    SP_DECREMENT(26),
    PC_LOW_TO_DATA(27),
    PC_HIGH_TO_DATA(28),
    SOURCE_TO_DATA(29),
    /** Control-internal: loads the IE (interrupt-enable) register with IE_DATA. */
    IE_LOAD(30),
    /** Control-internal: the value IE_LOAD captures into the IE register. */
    IE_DATA(31),
    /** Control-internal: forces the IRQ_TAKEN latch back to 0 once an interrupt entry
     *  sequence has staged the handler's address, so the handler's own fetch does not
     *  immediately re-trigger a second entry. */
    IRQ_ACK(32),
    /** Drives the live FLAGS_REGISTER nibble (zero-extended to a byte) onto DATA. */
    FLAGS_TO_DATA(33),
    /** Selects a popped DATA-bus byte's low nibble, instead of the live ALU-derived
     *  flags, as FLAGS_REGISTER's next value; used by IRET. */
    FLAGS_FROM_DATA(34),
    /** Drives the low byte of the fixed interrupt handler entry point onto DATA. */
    VECTOR_LOW_TO_DATA(35),
    /** Drives the high byte of the fixed interrupt handler entry point onto DATA. */
    VECTOR_HIGH_TO_DATA(36);

    private final int bit;

    Lf8ControlSignal(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }

    public long mask() {
        return 1L << bit;
    }

    public static int wordWidth() {
        int highestSignal = java.util.Arrays.stream(values())
                .mapToInt(Lf8ControlSignal::bit).max().orElse(-1);
        int highestField = java.util.Arrays.stream(Lf8ControlField.values())
                .mapToInt(Lf8ControlField::msb).max().orElse(-1);
        return Math.max(highestSignal, highestField) + 1;
    }
}
