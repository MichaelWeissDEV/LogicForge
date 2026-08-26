package dev.logicforge.processor.lf8;

import static dev.logicforge.processor.lf8.Lf8ControlSignal.*;

/** Generates the structural LF-8 control ROM contents from {@link Lf8Isa}. */
public final class Lf8Microcode {

    public static final int MICROSTEPS = 8;
    public static final int FLAG_SLOTS = 16;
    public static final int WORDS = 256 * FLAG_SLOTS * MICROSTEPS;

    private Lf8Microcode() {
    }

    public static int[] words() {
        int[] code = new int[WORDS];
        int fetch = word(PC_INCREMENT, IR_LOAD, MEMORY_READ);
        for (int opcode = 0; opcode < 256; opcode++) {
            for (int flags = 0; flags < FLAG_SLOTS; flags++) {
                code[address(opcode, flags, 0)] = fetch;
            }
        }
        set(code, Lf8Isa.LDI, 1, PC_INCREMENT, DESTINATION_REGISTER_LOAD, MEMORY_READ);
        set(code, Lf8Isa.LDI, 2, PC_INCREMENT, REGISTER_FILE_WRITE, MEMORY_READ);

        for (Lf8Instruction instruction : new Lf8Instruction[]{Lf8Isa.MOV, Lf8Isa.ADD, Lf8Isa.SUB}) {
            set(code, instruction, 1, PC_INCREMENT, DESTINATION_REGISTER_LOAD, MEMORY_READ);
            set(code, instruction, 2, PC_INCREMENT, SOURCE_REGISTER_LOAD, MEMORY_READ);
        }
        set(code, Lf8Isa.MOV, 3, REGISTER_FILE_WRITE, ALTERNATE_SOURCE, MOV_SOURCE);
        set(code, Lf8Isa.ADD, 3, REGISTER_FILE_WRITE, ALU_SOURCE);
        set(code, Lf8Isa.SUB, 3, REGISTER_FILE_WRITE, ALU_SOURCE, ALU_SUBTRACT);

        set(code, Lf8Isa.LOAD, 1, PC_INCREMENT, DESTINATION_REGISTER_LOAD, MEMORY_READ);
        set(code, Lf8Isa.STORE, 1, PC_INCREMENT, SOURCE_REGISTER_LOAD, MEMORY_READ);
        for (Lf8Instruction instruction : new Lf8Instruction[]{Lf8Isa.LOAD, Lf8Isa.STORE}) {
            set(code, instruction, 2, PC_INCREMENT, MAR_LOW_LOAD, MEMORY_READ);
            set(code, instruction, 3, PC_INCREMENT, MAR_HIGH_LOAD, MEMORY_READ);
        }
        set(code, Lf8Isa.LOAD, 4, REGISTER_FILE_WRITE, ALTERNATE_SOURCE,
                MEMORY_READ, ADDRESS_FROM_MAR);
        set(code, Lf8Isa.STORE, 4, MEMORY_WRITE, ADDRESS_FROM_MAR);

        set(code, Lf8Isa.JMP, 1, PC_INCREMENT, MAR_LOW_LOAD, MEMORY_READ);
        set(code, Lf8Isa.JMP, 2, PC_INCREMENT, MAR_HIGH_LOAD, MEMORY_READ);
        set(code, Lf8Isa.JMP, 3, PC_LOAD);
        for (int step = 1; step < MICROSTEPS; step++) {
            set(code, Lf8Isa.HLT, step, HALT);
        }
        return code;
    }

    public static String contents() {
        StringBuilder csv = new StringBuilder();
        for (int value : words()) {
            if (!csv.isEmpty()) {
                csv.append(',');
            }
            csv.append(Integer.toHexString(value));
        }
        return csv.toString();
    }

    public static int address(int opcode, int microstep) {
        return address(opcode, 0, microstep);
    }

    public static int address(int opcode, int flags, int microstep) {
        if (opcode < 0 || opcode > 0xff) {
            throw new IllegalArgumentException("Opcode is outside 0..255: " + opcode);
        }
        if (flags < 0 || flags >= FLAG_SLOTS) {
            throw new IllegalArgumentException("Flags are outside 0..15: " + flags);
        }
        if (microstep < 0 || microstep >= MICROSTEPS) {
            throw new IllegalArgumentException("Microstep is outside 0..7: " + microstep);
        }
        return ((opcode * FLAG_SLOTS) + flags) * MICROSTEPS + microstep;
    }

    private static void set(int[] contents, Lf8Instruction instruction, int step,
                            Lf8ControlSignal... signals) {
        for (int flags = 0; flags < FLAG_SLOTS; flags++) {
            contents[address(instruction.opcode(), flags, step)] = word(signals);
        }
    }

    private static int word(Lf8ControlSignal... signals) {
        int result = 0;
        for (Lf8ControlSignal signal : signals) {
            result |= signal.mask();
        }
        return result;
    }
}
