package dev.logicforge.processor.lf8;

import static dev.logicforge.processor.lf8.Lf8Instruction.Flag.CARRY;
import static dev.logicforge.processor.lf8.Lf8Instruction.Flag.NEGATIVE;
import static dev.logicforge.processor.lf8.Lf8Instruction.Flag.OVERFLOW;
import static dev.logicforge.processor.lf8.Lf8Instruction.Flag.ZERO;
import static dev.logicforge.processor.lf8.Lf8Instruction.OperandForm.ADDRESS16;
import static dev.logicforge.processor.lf8.Lf8Instruction.OperandForm.FIXED_R0;
import static dev.logicforge.processor.lf8.Lf8Instruction.OperandForm.FIXED_R1;
import static dev.logicforge.processor.lf8.Lf8Instruction.OperandForm.IMMEDIATE8;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Canonical definition of the currently executable LF-8 prototype ISA. */
public final class Lf8Isa {

    private static final Set<Lf8Instruction.Flag> ARITHMETIC_FLAGS =
            Set.of(ZERO, CARRY, NEGATIVE, OVERFLOW);

    public static final Lf8Instruction LDI_R0 = instruction("LDI", 0x01,
            List.of(FIXED_R0, IMMEDIATE8), Set.of());
    public static final Lf8Instruction LDI_R1 = instruction("LDI", 0x02,
            List.of(FIXED_R1, IMMEDIATE8), Set.of());
    public static final Lf8Instruction ADD_R0_R1 = instruction("ADD", 0x03,
            List.of(FIXED_R0, FIXED_R1), ARITHMETIC_FLAGS);
    public static final Lf8Instruction SUB_R0_R1 = instruction("SUB", 0x04,
            List.of(FIXED_R0, FIXED_R1), ARITHMETIC_FLAGS);
    public static final Lf8Instruction MOV_R0_R1 = instruction("MOV", 0x05,
            List.of(FIXED_R0, FIXED_R1), Set.of());
    public static final Lf8Instruction MOV_R1_R0 = instruction("MOV", 0x06,
            List.of(FIXED_R1, FIXED_R0), Set.of());
    public static final Lf8Instruction STORE_R0 = instruction("STORE", 0x07,
            List.of(FIXED_R0, ADDRESS16), Set.of());
    public static final Lf8Instruction LOAD_R0 = instruction("LOAD", 0x08,
            List.of(FIXED_R0, ADDRESS16), Set.of());
    public static final Lf8Instruction JMP = instruction("JMP", 0x09,
            List.of(ADDRESS16), Set.of());
    public static final Lf8Instruction HLT = instruction("HLT", 0xff,
            List.of(), Set.of());

    private static final List<Lf8Instruction> INSTRUCTIONS = List.of(
            LDI_R0, LDI_R1, ADD_R0_R1, SUB_R0_R1, MOV_R0_R1, MOV_R1_R0,
            STORE_R0, LOAD_R0, JMP, HLT);
    private static final Map<Integer, Lf8Instruction> BY_OPCODE = byOpcode();

    private Lf8Isa() {
    }

    public static List<Lf8Instruction> instructions() {
        return INSTRUCTIONS;
    }

    public static Optional<Lf8Instruction> byOpcode(int opcode) {
        return Optional.ofNullable(BY_OPCODE.get(opcode));
    }

    private static Lf8Instruction instruction(String mnemonic, int opcode,
                                              List<Lf8Instruction.OperandForm> operands,
                                              Set<Lf8Instruction.Flag> flags) {
        int length = 1 + operands.stream().mapToInt(Lf8Instruction.OperandForm::encodedBytes).sum();
        return new Lf8Instruction(mnemonic, opcode, operands, length, flags);
    }

    private static Map<Integer, Lf8Instruction> byOpcode() {
        Map<Integer, Lf8Instruction> result = new LinkedHashMap<>();
        for (Lf8Instruction instruction : INSTRUCTIONS) {
            if (result.put(instruction.opcode(), instruction) != null) {
                throw new IllegalStateException("Duplicate LF-8 opcode " + instruction.opcode());
            }
        }
        return Map.copyOf(result);
    }
}
