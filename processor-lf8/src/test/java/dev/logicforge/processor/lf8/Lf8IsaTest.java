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
        assertEquals(3, Lf8Isa.LDI.length());
        assertEquals(3, Lf8Isa.ADD.length());
        assertEquals(4, Lf8Isa.STORE.length());
        assertEquals(3, Lf8Isa.JMP.length());
    }

    @Test
    void microcodeUsesCanonicalInstructionOpcodes() {
        int[] words = Lf8Microcode.words();
        assertEquals(Lf8ControlSignal.REGISTER_FILE_WRITE.mask()
                        | Lf8ControlSignal.ALU_SOURCE.mask(),
                words[Lf8Microcode.address(Lf8Isa.ADD.opcode(), 3)]);
        assertEquals(Lf8ControlSignal.HALT.mask(),
                words[Lf8Microcode.address(Lf8Isa.HLT.opcode(), 1)]);
    }
}
