package dev.logicforge.processor.lf8;

/** Canonical 16-bit address map for an LF-8 computer. */
public final class Lf8MemoryMap {

    public static final int ROM_START = 0x0000;
    public static final int ROM_END = 0x7fff;
    public static final int RAM_START = 0x8000;
    public static final int RAM_END = 0xbfff;
    public static final int MMIO_START = 0xc000;
    public static final int MMIO_END = 0xc0ff;
    public static final int EXPANSION_START = 0xc100;
    public static final int EXPANSION_END = 0xfff7;

    /** Little-endian maskable-interrupt vector (low byte, then high byte). */
    public static final int IRQ_VECTOR = 0xfff8;
    /** Little-endian non-maskable-interrupt vector, reserved until NMI is implemented. */
    public static final int NMI_VECTOR = 0xfffa;
    /** Little-endian reset vector (low byte, then high byte). */
    public static final int RESET_VECTOR = 0xfffc;
    public static final int VECTOR_RESERVED_START = 0xfffe;
    public static final int VECTOR_RESERVED_END = 0xffff;

    public static final int OUTPUT_PORT = 0xc000;
    /** Character sink mirrors writes to the output port and exposes a text debug buffer. */
    public static final int CHARACTER_OUTPUT = OUTPUT_PORT;
    public static final int INPUT_PORT = 0xc001;
    public static final int TIMER_RELOAD_LOW = 0xc010;
    public static final int TIMER_RELOAD_HIGH = 0xc011;
    public static final int TIMER_CONTROL = 0xc012;
    public static final int TIMER_STATUS = 0xc013;

    private Lf8MemoryMap() {
    }
}
