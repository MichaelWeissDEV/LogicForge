package dev.logicforge.processor.lf8;

/** Multi-bit fields packed into the LF-8 microcode control word. */
public enum Lf8ControlField {
    ALU_OP(13, 4);

    private final int lsb;
    private final int width;

    Lf8ControlField(int lsb, int width) {
        this.lsb = lsb;
        this.width = width;
    }

    public int lsb() {
        return lsb;
    }

    public int width() {
        return width;
    }

    public int msb() {
        return lsb + width - 1;
    }

    public long encode(int value) {
        if (value < 0 || value >= (1 << width)) {
            throw new IllegalArgumentException(name() + " value does not fit in " + width + " bits");
        }
        return (long) value << lsb;
    }
}
