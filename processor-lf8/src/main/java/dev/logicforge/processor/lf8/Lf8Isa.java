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
    public static final Lf8Instruction HLT = instruction("HLT", 0xff,
            List.of(), Set.of());

    private static final List<Lf8Instruction> INSTRUCTIONS = List.of(
            NOP, LDI, MOV, ADD, SUB, LOAD, STORE, JMP,
            JZ, JNZ, JC, JNC, JN, JNN, HLT);
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
