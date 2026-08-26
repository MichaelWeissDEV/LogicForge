package dev.logicforge.processor.lf8;

import static dev.logicforge.processor.lf8.Lf8Instruction.Flag.CARRY;
import static dev.logicforge.processor.lf8.Lf8Instruction.Flag.NEGATIVE;
import static dev.logicforge.processor.lf8.Lf8Instruction.Flag.OVERFLOW;
import static dev.logicforge.processor.lf8.Lf8Instruction.Flag.ZERO;
import static dev.logicforge.processor.lf8.Lf8Instruction.OperandForm.ADDRESS16;
import static dev.logicforge.processor.lf8.Lf8Instruction.OperandForm.IMMEDIATE8;
import static dev.logicforge.processor.lf8.Lf8Instruction.OperandForm.REGISTER;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Canonical definition of the currently executable LF-8 prototype ISA. */
public final class Lf8Isa {

    private static final Set<Lf8Instruction.Flag> ARITHMETIC_FLAGS =
            Set.of(ZERO, CARRY, NEGATIVE, OVERFLOW);

    public static final Lf8Instruction NOP = instruction("NOP", 0x00, List.of(), Set.of());
    public static final Lf8Instruction LDI = instruction("LDI", 0x01,
            List.of(REGISTER, IMMEDIATE8), Set.of());
    public static final Lf8Instruction MOV = instruction("MOV", 0x02,
            List.of(REGISTER, REGISTER), Set.of());
    public static final Lf8Instruction ADD = instruction("ADD", 0x03,
            List.of(REGISTER, REGISTER), ARITHMETIC_FLAGS);
    public static final Lf8Instruction SUB = instruction("SUB", 0x04,
            List.of(REGISTER, REGISTER), ARITHMETIC_FLAGS);
    public static final Lf8Instruction LOAD = instruction("LOAD", 0x05,
            List.of(REGISTER, ADDRESS16), Set.of());
    public static final Lf8Instruction STORE = instruction("STORE", 0x06,
            List.of(REGISTER, ADDRESS16), Set.of());
    public static final Lf8Instruction JMP = instruction("JMP", 0x07,
            List.of(ADDRESS16), Set.of());
    public static final Lf8Instruction JZ = instruction("JZ", 0x08,
            List.of(ADDRESS16), Set.of());
    public static final Lf8Instruction JNZ = instruction("JNZ", 0x09,
            List.of(ADDRESS16), Set.of());
    public static final Lf8Instruction JC = instruction("JC", 0x0a,
            List.of(ADDRESS16), Set.of());
    public static final Lf8Instruction JNC = instruction("JNC", 0x0b,
            List.of(ADDRESS16), Set.of());
    public static final Lf8Instruction JN = instruction("JN", 0x0c,
            List.of(ADDRESS16), Set.of());
    public static final Lf8Instruction JNN = instruction("JNN", 0x0d,
            List.of(ADDRESS16), Set.of());
    public static final Lf8Instruction AND = instruction("AND", 0x0e,
            List.of(REGISTER, REGISTER), Set.of(ZERO, NEGATIVE));
    public static final Lf8Instruction OR = instruction("OR", 0x0f,
            List.of(REGISTER, REGISTER), Set.of(ZERO, NEGATIVE));
    public static final Lf8Instruction XOR = instruction("XOR", 0x10,
            List.of(REGISTER, REGISTER), Set.of(ZERO, NEGATIVE));
    public static final Lf8Instruction CMP = instruction("CMP", 0x11,
            List.of(REGISTER, REGISTER), ARITHMETIC_FLAGS);
    public static final Lf8Instruction INC = instruction("INC", 0x12,
            List.of(REGISTER), ARITHMETIC_FLAGS);
    public static final Lf8Instruction DEC = instruction("DEC", 0x13,
            List.of(REGISTER), ARITHMETIC_FLAGS);
    public static final Lf8Instruction SHL = instruction("SHL", 0x14,
            List.of(REGISTER), Set.of(ZERO, CARRY, NEGATIVE));
    public static final Lf8Instruction SHR = instruction("SHR", 0x15,
            List.of(REGISTER), Set.of(ZERO, CARRY, NEGATIVE));
    public static final Lf8Instruction NOT = instruction("NOT", 0x16,
            List.of(REGISTER), Set.of(ZERO, NEGATIVE));
    public static final Lf8Instruction ROL = instruction("ROL", 0x17,
            List.of(REGISTER), Set.of(ZERO, CARRY, NEGATIVE));
    public static final Lf8Instruction ROR = instruction("ROR", 0x18,
            List.of(REGISTER), Set.of(ZERO, CARRY, NEGATIVE));
    public static final Lf8Instruction NEG = instruction("NEG", 0x19,
            List.of(REGISTER), ARITHMETIC_FLAGS);
    public static final Lf8Instruction PUSH = instruction("PUSH", 0x1a,
            List.of(REGISTER), Set.of());
    public static final Lf8Instruction POP = instruction("POP", 0x1b,
            List.of(REGISTER), Set.of());
    public static final Lf8Instruction CALL = instruction("CALL", 0x1c,
            List.of(ADDRESS16), Set.of());
    public static final Lf8Instruction RET = instruction("RET", 0x1d,
            List.of(), Set.of());
    public static final Lf8Instruction HLT = instruction("HLT", 0xff,
            List.of(), Set.of());

    private static final List<Lf8Instruction> INSTRUCTIONS = List.of(
            NOP, LDI, MOV, ADD, SUB, LOAD, STORE, JMP,
            JZ, JNZ, JC, JNC, JN, JNN,
            AND, OR, XOR, CMP, INC, DEC, SHL, SHR,
            NOT, ROL, ROR, NEG, PUSH, POP, CALL, RET, HLT);
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
