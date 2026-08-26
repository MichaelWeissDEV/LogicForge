package dev.logicforge.processor.lf8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.HashSet;
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
        assertEquals(2, Lf8Isa.LDI_R0.length());
        assertEquals(1, Lf8Isa.ADD_R0_R1.length());
        assertEquals(3, Lf8Isa.STORE_R0.length());
        assertEquals(3, Lf8Isa.JMP.length());
    }

    @Test
    void microcodeUsesCanonicalInstructionOpcodes() {
        int[] words = Lf8Microcode.words();
        assertEquals(Lf8ControlSignal.ALU_LOAD.mask(),
                words[Lf8Microcode.address(Lf8Isa.ADD_R0_R1.opcode(), 1)]);
        assertEquals(Lf8ControlSignal.HALT.mask(),
                words[Lf8Microcode.address(Lf8Isa.HLT.opcode(), 1)]);
    }
}
