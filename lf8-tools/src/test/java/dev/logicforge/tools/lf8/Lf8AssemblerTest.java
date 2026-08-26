package dev.logicforge.tools.lf8;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.processor.lf8.Lf8Isa;
import org.junit.jupiter.api.Test;

class Lf8AssemblerTest {

    @Test
    void assemblesTheReadmeStyleLoopExample() {
        String source = """
                start:
                    LDI R0, 5
                    LDI R1, 3
                    ADD R0, R1
                    CMP R0, R2
                    JZ done
                    STORE R0, 0x8000
                    JMP start

                done:
                    HLT
                """;

        int[] expected = {
                Lf8Isa.LDI.opcode(), 0, 5,
                Lf8Isa.LDI.opcode(), 1, 3,
                Lf8Isa.ADD.opcode(), 0, 1,
                Lf8Isa.CMP.opcode(), 0, 2,
                Lf8Isa.JZ.opcode(), 0x16, 0x00,
                Lf8Isa.STORE.opcode(), 0, 0x00, 0x80,
                Lf8Isa.JMP.opcode(), 0x00, 0x00,
                Lf8Isa.HLT.opcode(),
        };

        assertArrayEquals(expected, Lf8Assembler.assemble(source));
    }

    @Test
    void supportsDecimalHexAndBinaryImmediates() {
        int[] program = Lf8Assembler.assemble("""
                LDI R0, 10
                LDI R1, 0x0a
                LDI R2, 0b1010
                """);
        assertEquals(10, program[2]);
        assertEquals(10, program[5]);
        assertEquals(10, program[8]);
    }

    @Test
    void forwardLabelReferencesResolveCorrectly() {
        int[] program = Lf8Assembler.assemble("""
                JMP later
                NOP
                later:
                    HLT
                """);
        assertEquals(Lf8Isa.JMP.opcode(), program[0]);
        assertEquals(4, program[1] | (program[2] << 8), "later: must resolve to address 4");
        assertEquals(Lf8Isa.HLT.opcode(), program[4]);
    }

    @Test
    void commentsAreIgnored() {
        int[] program = Lf8Assembler.assemble("""
                ; a full-line comment
                LDI R0, 5 ; trailing comment
                HLT ; another
                """);
        assertArrayEquals(new int[]{Lf8Isa.LDI.opcode(), 0, 5, Lf8Isa.HLT.opcode()}, program);
    }

    @Test
    void orgMovesTheOutputCursorAndLeavesZeroFilledGaps() {
        int[] program = Lf8Assembler.assemble("""
                .org 0x10
                HLT
                """);
        assertEquals(0x11, program.length);
        assertEquals(0, program[0], "the gap before .org 0x10 must be zero-filled");
        assertEquals(Lf8Isa.HLT.opcode(), program[0x10]);
    }

    @Test
    void byteWordAndAsciiDirectivesEmitRawData() {
        int[] program = Lf8Assembler.assemble("""
                target:
                .byte 1, 0x02, 0b00000011
                .word 0x1234, target
                .ascii "AB"
                """);
        assertArrayEquals(new int[]{
                1, 2, 3,
                0x34, 0x12,
                0x00, 0x00,
                'A', 'B',
        }, program);
    }

    @Test
    void unknownMnemonicIsRejected() {
        var failure = assertThrows(Lf8AssemblyException.class,
                () -> Lf8Assembler.assemble("FROB R0, R1"));
        assertTrue(failure.getMessage().contains("unknown mnemonic"));
        assertEquals(1, failure.line());
    }

    @Test
    void wrongOperandCountIsRejected() {
        var failure = assertThrows(Lf8AssemblyException.class,
                () -> Lf8Assembler.assemble("ADD R0"));
        assertTrue(failure.getMessage().contains("expected 2 operand"));
    }

    @Test
    void registerOutOfRangeIsRejected() {
        var failure = assertThrows(Lf8AssemblyException.class,
                () -> Lf8Assembler.assemble("INC R8"));
        assertTrue(failure.getMessage().contains("invalid register"));
    }

    @Test
    void duplicateLabelIsRejected() {
        var failure = assertThrows(Lf8AssemblyException.class, () -> Lf8Assembler.assemble("""
                loop:
                    NOP
                loop:
                    HLT
                """));
        assertTrue(failure.getMessage().contains("duplicate label"));
    }

    @Test
    void undefinedLabelIsRejected() {
        var failure = assertThrows(Lf8AssemblyException.class,
                () -> Lf8Assembler.assemble("JMP nowhere"));
        assertTrue(failure.getMessage().contains("undefined label"));
    }

    @Test
    void immediateOver255IsRejected() {
        var failure = assertThrows(Lf8AssemblyException.class,
                () -> Lf8Assembler.assemble("LDI R0, 256"));
        assertTrue(failure.getMessage().contains("out of range (0-255)"));
    }

    @Test
    void addressOver65535IsRejected() {
        var failure = assertThrows(Lf8AssemblyException.class,
                () -> Lf8Assembler.assemble("JMP 0x10000"));
        assertTrue(failure.getMessage().contains("out of range (0-65535)"));
    }

    @Test
    void invalidSyntaxReportsLineAndSourceText() {
        var failure = assertThrows(Lf8AssemblyException.class,
                () -> Lf8Assembler.assemble("NOP\nLDI R0,\nHLT"));
        assertEquals(2, failure.line());
        assertTrue(failure.sourceText().contains("LDI R0,"));
    }
}
