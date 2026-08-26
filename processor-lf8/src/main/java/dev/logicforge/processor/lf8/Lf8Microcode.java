package dev.logicforge.processor.lf8;

import static dev.logicforge.processor.lf8.Lf8ControlSignal.*;

/** Generates the structural LF-8 control ROM contents from {@link Lf8Isa}. */
public final class Lf8Microcode {

    public static final int MICROSTEPS = 8;
    public static final int FLAG_SLOTS = 4;
    public static final int WORDS = 256 * FLAG_SLOTS * MICROSTEPS;

    private Lf8Microcode() {
    }

    public static int[] words() {
        int[] code = new int[WORDS];
        int fetch = word(PC_INCREMENT, IR_LOAD);
        for (int opcode = 0; opcode < 256; opcode++) {
            code[address(opcode, 0)] = fetch;
        }
        set(code, Lf8Isa.LDI, 1, PC_INCREMENT, DESTINATION_REGISTER_LOAD);
        set(code, Lf8Isa.LDI, 2, PC_INCREMENT, REGISTER_FILE_WRITE);

        for (Lf8Instruction instruction : new Lf8Instruction[]{Lf8Isa.MOV, Lf8Isa.ADD, Lf8Isa.SUB}) {
            set(code, instruction, 1, PC_INCREMENT, DESTINATION_REGISTER_LOAD);
            set(code, instruction, 2, PC_INCREMENT, SOURCE_REGISTER_LOAD);
        }
        set(code, Lf8Isa.MOV, 3, REGISTER_FILE_WRITE, ALTERNATE_SOURCE, MOV_SOURCE);
        set(code, Lf8Isa.ADD, 3, REGISTER_FILE_WRITE, ALU_SOURCE);
        set(code, Lf8Isa.SUB, 3, REGISTER_FILE_WRITE, ALU_SOURCE, ALU_SUBTRACT);

        set(code, Lf8Isa.LOAD, 1, PC_INCREMENT, DESTINATION_REGISTER_LOAD);
        set(code, Lf8Isa.STORE, 1, PC_INCREMENT, SOURCE_REGISTER_LOAD);
        for (Lf8Instruction instruction : new Lf8Instruction[]{Lf8Isa.LOAD, Lf8Isa.STORE}) {
            set(code, instruction, 2, PC_INCREMENT, MAR_LOW_LOAD);
            set(code, instruction, 3, PC_INCREMENT, MAR_HIGH_LOAD);
        }
        set(code, Lf8Isa.LOAD, 4, REGISTER_FILE_WRITE, ALTERNATE_SOURCE, MEMORY_READ);
        set(code, Lf8Isa.STORE, 4, MEMORY_WRITE);

        set(code, Lf8Isa.JMP, 1, PC_INCREMENT, MAR_LOW_LOAD);
        set(code, Lf8Isa.JMP, 2, PC_INCREMENT, MAR_HIGH_LOAD);
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
        return (opcode * FLAG_SLOTS) * MICROSTEPS + microstep;
    }

    private static void set(int[] contents, Lf8Instruction instruction, int step,
                            Lf8ControlSignal... signals) {
        contents[address(instruction.opcode(), step)] = word(signals);
    }

    private static int word(Lf8ControlSignal... signals) {
        int result = 0;
        for (Lf8ControlSignal signal : signals) {
            result |= signal.mask();
        }
        return result;
    }
}
