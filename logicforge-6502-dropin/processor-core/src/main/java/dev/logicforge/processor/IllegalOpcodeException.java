package dev.logicforge.processor;

/** Raised by the documented-instruction model for an NMOS 6502 undocumented opcode. */
public final class IllegalOpcodeException extends RuntimeException {
    public IllegalOpcodeException(int opcode, int address) {
        super("Undocumented NMOS 6502 opcode $%02X at $%04X".formatted(opcode & 0xff, address & 0xffff));
    }
}
