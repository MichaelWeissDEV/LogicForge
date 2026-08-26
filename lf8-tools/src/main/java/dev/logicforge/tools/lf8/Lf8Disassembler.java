package dev.logicforge.tools.lf8;

import dev.logicforge.processor.lf8.Lf8Instruction;
import dev.logicforge.processor.lf8.Lf8Instruction.OperandForm;
import dev.logicforge.processor.lf8.Lf8Isa;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Decodes an LF-8 memory image back into a mnemonic listing, driven entirely by the same
 * {@link Lf8Isa} opcode table the assembler and microcode use — no separate opcode table.
 */
public final class Lf8Disassembler {

    private Lf8Disassembler() {
    }

    /** One decoded instruction (or, for an unrecognized opcode, a single raw byte). */
    public record DisassembledInstruction(int address, int[] bytes, String text) {
    }

    public static List<DisassembledInstruction> disassemble(int[] memory) {
        List<DisassembledInstruction> result = new ArrayList<>();
        int address = 0;
        while (address < memory.length) {
            int opcode = memory[address] & 0xff;
            Optional<Lf8Instruction> found = Lf8Isa.byOpcode(opcode);
            if (found.isEmpty()) {
                result.add(new DisassembledInstruction(address, new int[]{opcode},
                        String.format("; unknown opcode 0x%02x", opcode)));
                address += 1;
                continue;
            }
            Lf8Instruction instruction = found.get();
            int length = instruction.length();
            if (address + length > memory.length) {
                int[] tail = Arrays.copyOfRange(memory, address, memory.length);
                result.add(new DisassembledInstruction(address, tail,
                        instruction.mnemonic() + "   ; truncated at end of image"));
                break;
            }
            int[] bytes = Arrays.copyOfRange(memory, address, address + length);
            result.add(new DisassembledInstruction(address, bytes, format(instruction, bytes)));
            address += length;
        }
        return result;
    }

    /** Renders {@code disassemble(memory)} as an {@code address: bytes  mnemonic} listing. */
    public static String listing(int[] memory) {
        StringBuilder listing = new StringBuilder();
        for (DisassembledInstruction line : disassemble(memory)) {
            StringBuilder hex = new StringBuilder();
            for (int b : line.bytes()) {
                hex.append(String.format("%02x ", b & 0xff));
            }
            listing.append(String.format("%04x: %-12s%s%n", line.address(), hex.toString().trim(),
                    line.text()));
        }
        return listing.toString();
    }

    private static String format(Lf8Instruction instruction, int[] bytes) {
        List<OperandForm> forms = instruction.operands();
        if (forms.isEmpty()) {
            return instruction.mnemonic();
        }
        StringBuilder text = new StringBuilder(instruction.mnemonic()).append(' ');
        int pos = 1;
        for (int i = 0; i < forms.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            switch (forms.get(i)) {
                case REGISTER -> {
                    text.append('R').append(bytes[pos]);
                    pos += 1;
                }
                case IMMEDIATE8 -> {
                    text.append(String.format("0x%02x", bytes[pos] & 0xff));
                    pos += 1;
                }
                case ADDRESS16 -> {
                    int value = (bytes[pos] & 0xff) | ((bytes[pos + 1] & 0xff) << 8);
                    text.append(String.format("0x%04x", value));
                    pos += 2;
                }
            }
        }
        return text.toString();
    }
}
