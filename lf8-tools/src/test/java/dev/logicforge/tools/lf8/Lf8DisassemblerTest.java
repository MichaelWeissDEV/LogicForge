package dev.logicforge.tools.lf8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.tools.lf8.Lf8Disassembler.DisassembledInstruction;
import java.util.List;
import org.junit.jupiter.api.Test;

class Lf8DisassemblerTest {

    @Test
    void decodesEveryOperandShapeUsingCanonicalIsaMetadata() {
        int[] memory = {
                0x01, 0x00, 0x05, // LDI R0, 0x05
                0x03, 0x02, 0x03, // ADD R2, R3
                0x07, 0x00, 0x80, // JMP 0x8000
                0xff,             // HLT
        };
        List<DisassembledInstruction> decoded = Lf8Disassembler.disassemble(memory);

        assertEquals(4, decoded.size());
        assertEquals(0, decoded.get(0).address());
        assertEquals("LDI R0, 0x05", decoded.get(0).text());
        assertEquals(3, decoded.get(1).address());
        assertEquals("ADD R2, R3", decoded.get(1).text());
        assertEquals(6, decoded.get(2).address());
        assertEquals("JMP 0x8000", decoded.get(2).text());
        assertEquals(9, decoded.get(3).address());
        assertEquals("HLT", decoded.get(3).text());
    }

    @Test
    void roundTripsAssembledProgramsBackToEquivalentMnemonics() {
        int[] memory = Lf8Assembler.assemble("""
                start:
                    LDI R0, 5
                    PUSH R0
                    POP R1
                    CALL start
                    RET
                """);
        List<DisassembledInstruction> decoded = Lf8Disassembler.disassemble(memory);
        assertEquals("LDI R0, 0x05", decoded.get(0).text());
        assertEquals("PUSH R0", decoded.get(1).text());
        assertEquals("POP R1", decoded.get(2).text());
        assertEquals("CALL 0x0000", decoded.get(3).text());
        assertEquals("RET", decoded.get(4).text());
    }

    @Test
    void unknownOpcodesAreReportedRatherThanCrashing() {
        int[] memory = {0xaa};
        List<DisassembledInstruction> decoded = Lf8Disassembler.disassemble(memory);
        assertEquals(1, decoded.size());
        assertTrue(decoded.get(0).text().contains("unknown opcode"));
    }

    @Test
    void listingFormatsAddressBytesAndMnemonic() {
        String listing = Lf8Disassembler.listing(new int[]{0x01, 0x00, 0x05});
        assertTrue(listing.startsWith("0000: 01 00 05"));
        assertTrue(listing.contains("LDI R0, 0x05"));
    }
}
