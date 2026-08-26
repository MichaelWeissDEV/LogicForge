package dev.logicforge.processor.lf8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Lf8IsaTest {

    @Test
    void opcodesAreUniqueAndRoundTripThroughCanonicalLookup() {
        HashSet<Integer> opcodes = new HashSet<>();
        for (Lf8Instruction instruction : Lf8Isa.instructions()) {
            opcodes.add(instruction.opcode());
            assertSame(instruction, Lf8Isa.byOpcode(instruction.opcode()).orElseThrow());
        }
        assertEquals(Lf8Isa.instructions().size(), opcodes.size());
    }

    @Test
    void instructionLengthsAreDerivedFromOperandEncoding() {
        assertEquals(3, Lf8Isa.LDI.length());
        assertEquals(3, Lf8Isa.ADD.length());
        assertEquals(4, Lf8Isa.STORE.length());
        assertEquals(3, Lf8Isa.JMP.length());
        assertEquals(2, Lf8Isa.INC.length());
    }

    @Test
    void canonicalMetadataDefinesSelectiveFlagEffects() {
        assertEquals(Set.of(Lf8Instruction.Flag.ZERO, Lf8Instruction.Flag.NEGATIVE),
                Lf8Isa.XOR.flagEffects());
        assertEquals(Set.of(Lf8Instruction.Flag.ZERO, Lf8Instruction.Flag.CARRY,
                        Lf8Instruction.Flag.NEGATIVE), Lf8Isa.SHL.flagEffects());
        assertEquals(Set.of(), Lf8Isa.LOAD.flagEffects(),
                "loads and moves deliberately preserve every condition code");
    }

    @Test
    void microcodeUsesCanonicalInstructionOpcodes() {
        long[] words = Lf8Microcode.words();
        assertEquals(Lf8ControlSignal.REGISTER_FILE_WRITE.mask()
                        | Lf8ControlSignal.ALU_SOURCE.mask()
                        | Lf8ControlSignal.FLAGS_LOAD.mask()
                        | Lf8ControlField.ALU_OP.encode(Lf8AluOperation.ADD.code()),
                words[Lf8Microcode.address(Lf8Isa.ADD.opcode(), 3)]);
        assertEquals(Lf8ControlSignal.HALT.mask(),
                words[Lf8Microcode.address(Lf8Isa.HLT.opcode(), 1)]);
    }

    @Test
    void conditionalBranchPagesDependOnStoredFlags() {
        long[] words = Lf8Microcode.words();
        assertEquals(0, words[Lf8Microcode.address(Lf8Isa.JZ.opcode(), 0b0000, 3)]);
        assertEquals(Lf8ControlSignal.PC_LOAD.mask(),
                words[Lf8Microcode.address(Lf8Isa.JZ.opcode(), 0b0001, 3)]);
        assertEquals(Lf8ControlSignal.PC_LOAD.mask(),
                words[Lf8Microcode.address(Lf8Isa.JNC.opcode(), 0b0000, 3)]);
        assertEquals(0, words[Lf8Microcode.address(Lf8Isa.JNC.opcode(), 0b0010, 3)]);
    }
}
