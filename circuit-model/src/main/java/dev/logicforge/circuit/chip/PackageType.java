package dev.logicforge.circuit.chip;

/** Initially supported through-hole dual-inline package outlines. */
public enum PackageType {
    DIP8(8),
    DIP14(14),
    DIP16(16),
    DIP18(18),
    DIP20(20),
    DIP24(24),
    DIP28(28),
    DIP40(40);

    private final int pinCount;

    PackageType(int pinCount) {
        this.pinCount = pinCount;
    }

    public int pinCount() {
        return pinCount;
    }
}
