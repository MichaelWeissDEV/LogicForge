package dev.logicforge.processor.lf8;

import static dev.logicforge.processor.lf8.Lf8ControlSignal.ALU_LOAD;
import static dev.logicforge.processor.lf8.Lf8ControlSignal.ALU_SUBTRACT;
import static dev.logicforge.processor.lf8.Lf8ControlSignal.HALT;
import static dev.logicforge.processor.lf8.Lf8ControlSignal.IR_LOAD;
import static dev.logicforge.processor.lf8.Lf8ControlSignal.LOAD_R0_FROM_MEMORY;
import static dev.logicforge.processor.lf8.Lf8ControlSignal.MAR_HIGH_LOAD;
import static dev.logicforge.processor.lf8.Lf8ControlSignal.MAR_LOW_LOAD;
import static dev.logicforge.processor.lf8.Lf8ControlSignal.MEMORY_WRITE;
import static dev.logicforge.processor.lf8.Lf8ControlSignal.MOVE_R0_FROM_R1;
import static dev.logicforge.processor.lf8.Lf8ControlSignal.MOVE_R1_FROM_R0;
import static dev.logicforge.processor.lf8.Lf8ControlSignal.PC_INCREMENT;
import static dev.logicforge.processor.lf8.Lf8ControlSignal.PC_LOAD;
import static dev.logicforge.processor.lf8.Lf8ControlSignal.R0_LOAD_IMMEDIATE;
import static dev.logicforge.processor.lf8.Lf8ControlSignal.R1_LOAD_IMMEDIATE;

/** Generates the structural LF-8 control ROM contents from {@link Lf8Isa}. */
public final class Lf8Microcode {

    public static final int MICROSTEPS = 4;
    public static final int FLAG_SLOTS = 4;
    public static final int WORDS = 256 * FLAG_SLOTS * MICROSTEPS;

    private Lf8Microcode() {
    }

    public static int[] words() {
        int[] microcode = new int[WORDS];
        int fetch = word(PC_INCREMENT, IR_LOAD);
        for (int opcode = 0; opcode < 256; opcode++) {
            microcode[address(opcode, 0)] = fetch;
        }
        set(microcode, Lf8Isa.LDI_R0, 1, PC_INCREMENT, R0_LOAD_IMMEDIATE);
        set(microcode, Lf8Isa.LDI_R1, 1, PC_INCREMENT, R1_LOAD_IMMEDIATE);
        set(microcode, Lf8Isa.ADD_R0_R1, 1, ALU_LOAD);
        set(microcode, Lf8Isa.SUB_R0_R1, 1, ALU_LOAD, ALU_SUBTRACT);
        set(microcode, Lf8Isa.MOV_R0_R1, 1, MOVE_R0_FROM_R1);
        set(microcode, Lf8Isa.MOV_R1_R0, 1, MOVE_R1_FROM_R0);
        set(microcode, Lf8Isa.STORE_R0, 1, PC_INCREMENT, MAR_LOW_LOAD);
        set(microcode, Lf8Isa.STORE_R0, 2, PC_INCREMENT, MAR_HIGH_LOAD);
        set(microcode, Lf8Isa.STORE_R0, 3, MEMORY_WRITE);
        set(microcode, Lf8Isa.LOAD_R0, 1, PC_INCREMENT, MAR_LOW_LOAD);
        set(microcode, Lf8Isa.LOAD_R0, 2, PC_INCREMENT, MAR_HIGH_LOAD);
        set(microcode, Lf8Isa.LOAD_R0, 3, LOAD_R0_FROM_MEMORY);
        set(microcode, Lf8Isa.JMP, 1, PC_INCREMENT, MAR_LOW_LOAD);
        set(microcode, Lf8Isa.JMP, 2, PC_INCREMENT, MAR_HIGH_LOAD);
        set(microcode, Lf8Isa.JMP, 3, PC_LOAD);
        for (int step = 1; step < MICROSTEPS; step++) {
            set(microcode, Lf8Isa.HLT, step, HALT);
        }
        return microcode;
    }

    public static String contents() {
        int[] words = words();
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(Integer.toHexString(words[i]));
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
