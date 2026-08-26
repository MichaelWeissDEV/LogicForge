package dev.logicforge.processor.lf8;

import static dev.logicforge.processor.lf8.Lf8ControlSignal.*;

/** Generates the structural LF-8 control ROM contents from {@link Lf8Isa}. */
public final class Lf8Microcode {

    public static final int MICROSTEPS = 8;
    /**
     * ROM address bits contributed by the flags field: bits 0-3 are the stored Z/C/N/V flags
     * (as before); bit 4 is IRQ_TAKEN, a synthetic "flag" latched once per instruction
     * boundary that redirects microstep 0 into the interrupt entry sequence instead of a
     * normal fetch. Reusing the flags-indexed ROM addressing scheme already built for
     * conditional branches means every existing instruction's steps 1+ are automatically
     * defined identically across both IRQ_TAKEN values with no extra code, since set()/
     * setAlu()/branch() already loop over the full FLAG_SLOTS range.
     */
    public static final int FLAG_SLOTS = 32;
    public static final int WORDS = 256 * FLAG_SLOTS * MICROSTEPS;
    private static final int ZERO_FLAG = 1;
    private static final int CARRY_FLAG = 1 << 1;
    private static final int NEGATIVE_FLAG = 1 << 2;

    private Lf8Microcode() {
    }

    public static long[] words() {
        long[] code = new long[WORDS];
        long fetch = word(PC_INCREMENT, IR_LOAD, MEMORY_READ);
        for (int opcode = 0; opcode < 256; opcode++) {
            for (int flags = 0; flags < FLAG_SLOTS; flags++) {
                code[address(opcode, flags, 0)] = fetch;
            }
        }
        set(code, Lf8Isa.LDI, 1, PC_INCREMENT, DESTINATION_REGISTER_LOAD, MEMORY_READ);
        set(code, Lf8Isa.LDI, 2, PC_INCREMENT, REGISTER_FILE_WRITE, MEMORY_READ);

        for (Lf8Instruction instruction : new Lf8Instruction[]{
                Lf8Isa.MOV, Lf8Isa.ADD, Lf8Isa.SUB, Lf8Isa.AND, Lf8Isa.OR,
                Lf8Isa.XOR, Lf8Isa.CMP}) {
            set(code, instruction, 1, PC_INCREMENT, DESTINATION_REGISTER_LOAD, MEMORY_READ);
            set(code, instruction, 2, PC_INCREMENT, SOURCE_REGISTER_LOAD, MEMORY_READ);
        }
        set(code, Lf8Isa.MOV, 3, REGISTER_FILE_WRITE, ALTERNATE_SOURCE, MOV_SOURCE);
        setAlu(code, Lf8Isa.ADD, 3, Lf8AluOperation.ADD,
                REGISTER_FILE_WRITE, ALU_SOURCE, FLAGS_LOAD);
        setAlu(code, Lf8Isa.SUB, 3, Lf8AluOperation.SUB,
                REGISTER_FILE_WRITE, ALU_SOURCE, FLAGS_LOAD, ALU_CARRY_IN);
        for (MapEntry logical : new MapEntry[]{
                new MapEntry(Lf8Isa.AND, Lf8AluOperation.AND),
                new MapEntry(Lf8Isa.OR, Lf8AluOperation.OR),
                new MapEntry(Lf8Isa.XOR, Lf8AluOperation.XOR)}) {
            setAlu(code, logical.instruction(), 3, logical.operation(),
                    REGISTER_FILE_WRITE, ALU_SOURCE, FLAGS_LOAD,
                    FLAGS_PRESERVE_CARRY, FLAGS_PRESERVE_OVERFLOW);
        }
        setAlu(code, Lf8Isa.CMP, 3, Lf8AluOperation.SUB, FLAGS_LOAD, ALU_CARRY_IN);

        for (Lf8Instruction instruction : new Lf8Instruction[]{
                Lf8Isa.INC, Lf8Isa.DEC, Lf8Isa.SHL, Lf8Isa.SHR}) {
            set(code, instruction, 1, PC_INCREMENT, DESTINATION_REGISTER_LOAD, MEMORY_READ);
        }
        setAlu(code, Lf8Isa.INC, 2, Lf8AluOperation.ADD,
                REGISTER_FILE_WRITE, ALU_SOURCE, FLAGS_LOAD, ALU_B_ONE);
        setAlu(code, Lf8Isa.DEC, 2, Lf8AluOperation.SUB,
                REGISTER_FILE_WRITE, ALU_SOURCE, FLAGS_LOAD, ALU_CARRY_IN, ALU_B_ONE);
        setAlu(code, Lf8Isa.SHL, 2, Lf8AluOperation.SHL,
                REGISTER_FILE_WRITE, ALU_SOURCE, FLAGS_LOAD, FLAGS_PRESERVE_OVERFLOW);
        setAlu(code, Lf8Isa.SHR, 2, Lf8AluOperation.SHR,
                REGISTER_FILE_WRITE, ALU_SOURCE, FLAGS_LOAD, FLAGS_PRESERVE_OVERFLOW);

        for (Lf8Instruction instruction : new Lf8Instruction[]{
                Lf8Isa.NOT, Lf8Isa.ROL, Lf8Isa.ROR, Lf8Isa.NEG}) {
            set(code, instruction, 1, PC_INCREMENT, DESTINATION_REGISTER_LOAD, MEMORY_READ);
        }
        setAlu(code, Lf8Isa.NOT, 2, Lf8AluOperation.NOT,
                REGISTER_FILE_WRITE, ALU_SOURCE, FLAGS_LOAD,
                FLAGS_PRESERVE_CARRY, FLAGS_PRESERVE_OVERFLOW);
        setAlu(code, Lf8Isa.ROL, 2, Lf8AluOperation.ROL,
                REGISTER_FILE_WRITE, ALU_SOURCE, FLAGS_LOAD, FLAGS_PRESERVE_OVERFLOW);
        setAlu(code, Lf8Isa.ROR, 2, Lf8AluOperation.ROR,
                REGISTER_FILE_WRITE, ALU_SOURCE, FLAGS_LOAD, FLAGS_PRESERVE_OVERFLOW);
        setAlu(code, Lf8Isa.NEG, 2, Lf8AluOperation.NEG,
                REGISTER_FILE_WRITE, ALU_SOURCE, FLAGS_LOAD, ALU_CARRY_IN);

        set(code, Lf8Isa.PUSH, 1, PC_INCREMENT, SOURCE_REGISTER_LOAD, MEMORY_READ);
        set(code, Lf8Isa.PUSH, 2, MEMORY_WRITE, SOURCE_TO_DATA, ADDRESS_FROM_SP, SP_DECREMENT);

        set(code, Lf8Isa.POP, 1, PC_INCREMENT, DESTINATION_REGISTER_LOAD, MEMORY_READ);
        set(code, Lf8Isa.POP, 2, SP_INCREMENT);
        set(code, Lf8Isa.POP, 3, REGISTER_FILE_WRITE, ALTERNATE_SOURCE,
                MEMORY_READ, ADDRESS_FROM_SP);

        set(code, Lf8Isa.LOAD, 1, PC_INCREMENT, DESTINATION_REGISTER_LOAD, MEMORY_READ);
        set(code, Lf8Isa.STORE, 1, PC_INCREMENT, SOURCE_REGISTER_LOAD, MEMORY_READ);
        for (Lf8Instruction instruction : new Lf8Instruction[]{Lf8Isa.LOAD, Lf8Isa.STORE}) {
            set(code, instruction, 2, PC_INCREMENT, MAR_LOW_LOAD, MEMORY_READ);
            set(code, instruction, 3, PC_INCREMENT, MAR_HIGH_LOAD, MEMORY_READ);
        }
        set(code, Lf8Isa.LOAD, 4, REGISTER_FILE_WRITE, ALTERNATE_SOURCE,
                MEMORY_READ, ADDRESS_FROM_MAR);
        set(code, Lf8Isa.STORE, 4, MEMORY_WRITE, SOURCE_TO_DATA, ADDRESS_FROM_MAR);

        set(code, Lf8Isa.JMP, 1, PC_INCREMENT, MAR_LOW_LOAD, MEMORY_READ);
        set(code, Lf8Isa.JMP, 2, PC_INCREMENT, MAR_HIGH_LOAD, MEMORY_READ);
        set(code, Lf8Isa.JMP, 3, PC_LOAD);

        // CALL: read the 2-byte target into MAR (exactly like JMP, and while PC still points
        // past the operand bytes — i.e. at the correct return address), then push PC high
        // before PC low so the low byte ends up on top of the stack, then jump via MAR.
        set(code, Lf8Isa.CALL, 1, PC_INCREMENT, MAR_LOW_LOAD, MEMORY_READ);
        set(code, Lf8Isa.CALL, 2, PC_INCREMENT, MAR_HIGH_LOAD, MEMORY_READ);
        set(code, Lf8Isa.CALL, 3, MEMORY_WRITE, ADDRESS_FROM_SP, SP_DECREMENT, PC_HIGH_TO_DATA);
        set(code, Lf8Isa.CALL, 4, MEMORY_WRITE, ADDRESS_FROM_SP, SP_DECREMENT, PC_LOW_TO_DATA);
        set(code, Lf8Isa.CALL, 5, PC_LOAD);

        // RET: pop PC low then PC high back into MAR (mirroring CALL's push order), then jump
        // via MAR exactly like JMP/CALL. SP must be incremented before each read since the
        // address bus reflects the live SP.COUNT combinationally.
        set(code, Lf8Isa.RET, 1, SP_INCREMENT);
        set(code, Lf8Isa.RET, 2, MAR_LOW_LOAD, MEMORY_READ, ADDRESS_FROM_SP);
        set(code, Lf8Isa.RET, 3, SP_INCREMENT);
        set(code, Lf8Isa.RET, 4, MAR_HIGH_LOAD, MEMORY_READ, ADDRESS_FROM_SP);
        set(code, Lf8Isa.RET, 5, PC_LOAD);

        set(code, Lf8Isa.EI, 1, IE_LOAD, IE_DATA);
        set(code, Lf8Isa.DI, 1, IE_LOAD);

        branch(code, Lf8Isa.JZ, ZERO_FLAG, true);
        branch(code, Lf8Isa.JNZ, ZERO_FLAG, false);
        branch(code, Lf8Isa.JC, CARRY_FLAG, true);
        branch(code, Lf8Isa.JNC, CARRY_FLAG, false);
        branch(code, Lf8Isa.JN, NEGATIVE_FLAG, true);
        branch(code, Lf8Isa.JNN, NEGATIVE_FLAG, false);
        for (int step = 1; step < MICROSTEPS; step++) {
            set(code, Lf8Isa.HLT, step, HALT);
        }
        return code;
    }

    public static String contents() {
        StringBuilder csv = new StringBuilder();
        for (long value : words()) {
            if (!csv.isEmpty()) {
                csv.append(',');
            }
            csv.append(Long.toHexString(value));
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
            throw new IllegalArgumentException("Flags are outside 0.." + (FLAG_SLOTS - 1) + ": " + flags);
        }
        if (microstep < 0 || microstep >= MICROSTEPS) {
            throw new IllegalArgumentException("Microstep is outside 0..7: " + microstep);
        }
        return ((opcode * FLAG_SLOTS) + flags) * MICROSTEPS + microstep;
    }

    private static void set(long[] contents, Lf8Instruction instruction, int step,
                            Lf8ControlSignal... signals) {
        for (int flags = 0; flags < FLAG_SLOTS; flags++) {
            contents[address(instruction.opcode(), flags, step)] = word(signals);
        }
    }

    private static void setAlu(long[] contents, Lf8Instruction instruction, int step,
                               Lf8AluOperation operation, Lf8ControlSignal... signals) {
        long value = word(signals) | Lf8ControlField.ALU_OP.encode(operation.code());
        for (int flags = 0; flags < FLAG_SLOTS; flags++) {
            contents[address(instruction.opcode(), flags, step)] = value;
        }
    }

    private static void branch(long[] contents, Lf8Instruction instruction,
                               int flagMask, boolean branchWhenSet) {
        set(contents, instruction, 1, PC_INCREMENT, MAR_LOW_LOAD, MEMORY_READ);
        set(contents, instruction, 2, PC_INCREMENT, MAR_HIGH_LOAD, MEMORY_READ);
        for (int flags = 0; flags < FLAG_SLOTS; flags++) {
            boolean isSet = (flags & flagMask) != 0;
            contents[address(instruction.opcode(), flags, 3)] =
                    isSet == branchWhenSet ? word(PC_LOAD) : 0;
        }
    }

    private static long word(Lf8ControlSignal... signals) {
        long result = 0;
        for (Lf8ControlSignal signal : signals) {
            result |= signal.mask();
        }
        return result;
    }

    private record MapEntry(Lf8Instruction instruction, Lf8AluOperation operation) {
    }
}
