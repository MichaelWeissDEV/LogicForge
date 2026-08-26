package dev.logicforge.processor.lf8;

import java.util.List;
import java.util.Set;

/** One LF-8 opcode definition shared by microcode, tools, tests, and documentation. */
public record Lf8Instruction(
        String mnemonic,
        int opcode,
        List<OperandForm> operands,
        int length,
        Set<Flag> flagEffects) {

    public Lf8Instruction {
        if (mnemonic == null || mnemonic.isBlank()) {
            throw new IllegalArgumentException("mnemonic must not be blank");
        }
        if (opcode < 0 || opcode > 0xff) {
            throw new IllegalArgumentException("opcode must fit in one byte");
        }
        operands = List.copyOf(operands);
        flagEffects = Set.copyOf(flagEffects);
        if (length != 1 + operands.stream().mapToInt(OperandForm::encodedBytes).sum()) {
            throw new IllegalArgumentException("instruction length does not match its operands");
        }
    }

    public enum OperandForm {
        FIXED_R0(0), FIXED_R1(0), IMMEDIATE8(1), ADDRESS16(2);

        private final int encodedBytes;

        OperandForm(int encodedBytes) {
            this.encodedBytes = encodedBytes;
        }

        public int encodedBytes() {
            return encodedBytes;
        }
    }

    public enum Flag {
        ZERO, CARRY, NEGATIVE, OVERFLOW
    }
}
