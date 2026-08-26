package dev.logicforge.processor.lf8;

/** Operation codes shared by LF-8 microcode and the generic hardware ALU. */
public enum Lf8AluOperation {
    ADD(0),
    SUB(1),
    AND(2),
    OR(3),
    XOR(4),
    NOT(5),
    SHL(6),
    SHR(7),
    PASS_A(8),
    PASS_B(9),
    ROL(10),
    ROR(11),
    NEG(12);

    private final int code;

    Lf8AluOperation(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
