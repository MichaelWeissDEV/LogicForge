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

        // IRET: the mirror image of the interrupt entry sequence below. Entry pushes
        // PC_HIGH, PC_LOW, FLAGS (FLAGS topmost/most recent); IRET pops in the reverse
        // order - FLAGS, then PC_LOW, then PC_HIGH - restoring FLAGS from the popped byte's
        // low nibble and unconditionally re-enabling interrupts as it jumps back (a
        // deliberately simple convention: interrupts are always disabled for the whole
        // handler and always re-enabled on return, rather than round-tripping the enable
        // bit through the stack frame).
        set(code, Lf8Isa.IRET, 1, SP_INCREMENT);
        set(code, Lf8Isa.IRET, 2, ADDRESS_FROM_SP, MEMORY_READ, FLAGS_FROM_DATA, FLAGS_LOAD);
        set(code, Lf8Isa.IRET, 3, SP_INCREMENT);
        set(code, Lf8Isa.IRET, 4, ADDRESS_FROM_SP, MEMORY_READ, MAR_LOW_LOAD);
        set(code, Lf8Isa.IRET, 5, SP_INCREMENT);
        set(code, Lf8Isa.IRET, 6, ADDRESS_FROM_SP, MEMORY_READ, MAR_HIGH_LOAD);
        set(code, Lf8Isa.IRET, 7, PC_LOAD, IE_LOAD, IE_DATA);

        branch(code, Lf8Isa.JZ, ZERO_FLAG, true);
        branch(code, Lf8Isa.JNZ, ZERO_FLAG, false);
        branch(code, Lf8Isa.JC, CARRY_FLAG, true);
        branch(code, Lf8Isa.JNC, CARRY_FLAG, false);
        branch(code, Lf8Isa.JN, NEGATIVE_FLAG, true);
        branch(code, Lf8Isa.JNN, NEGATIVE_FLAG, false);
        for (int step = 1; step < MICROSTEPS; step++) {
            set(code, Lf8Isa.HLT, step, HALT);
        }

        // Interrupt entry sequence. Occupies the IRQ_TAKEN half of the flags address space
        // (flags 16..31) uniformly across every possible (stale) opcode value, for every
        // microstep - this MUST run last, after every per-opcode set()/setAlu()/branch()
        // call above, because those calls each write across the FULL flags range (0..31,
        // now that FLAG_SLOTS is 32), including the upper half, for their own opcode. Since
        // IRQ_TAKEN can only ever be latched at microstep 0 and stays frozen until this
        // sequence's own IRQ_ACK, no real instruction's own steps are ever reachable while
        // IRQ_TAKEN is set - this just has to be the final, unconditional word for that
        // whole address range so nothing above it can leak through by opcode coincidence.
        //
        // Stack frame pushed (SP grows down): high address -> low address is
        // PC_HIGH, PC_LOW, FLAGS (FLAGS on top / most recently pushed) - see IRET above for
        // the matching pop order. PC is left untouched (no PC_INCREMENT): nothing was
        // fetched or consumed this cycle, so PC already holds the correct return address.
        // The entry sequence spans the CPU's entire MICROSTEPS budget (0..7) with no idle
        // gap: IRQ_ACK fires on the very last step so the latch is still 1 (and therefore
        // still routing through this same uniform definition, safe from stale-opcode
        // leakage) for every step of the sequence, and only clears on the edge that leaves
        // the sequence for the handler's own first fetch.
        setIrqEntry(code, 0, word(MEMORY_WRITE, ADDRESS_FROM_SP, SP_DECREMENT, PC_HIGH_TO_DATA));
        setIrqEntry(code, 1, word(MEMORY_WRITE, ADDRESS_FROM_SP, SP_DECREMENT, PC_LOW_TO_DATA));
        setIrqEntry(code, 2, word(MEMORY_WRITE, ADDRESS_FROM_SP, SP_DECREMENT, FLAGS_TO_DATA));
        setIrqEntry(code, 3, word(IE_LOAD));
        setIrqEntry(code, 4, word(VECTOR_LOW_TO_DATA, MAR_LOW_LOAD));
        setIrqEntry(code, 5, word(VECTOR_HIGH_TO_DATA, MAR_HIGH_LOAD));
        setIrqEntry(code, 6, word(PC_LOAD));
        setIrqEntry(code, 7, word(IRQ_ACK));
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

    /**
     * Writes {@code value} for every opcode (0..255) at the given microstep, restricted to
     * the IRQ_TAKEN half of the flags address space (flags 16..31) - the interrupt entry
     * sequence, uniform regardless of whatever opcode happens to be stale in IR.
     */
    private static void setIrqEntry(long[] contents, int step, long value) {
        int irqTakenBase = FLAG_SLOTS / 2;
        for (int opcode = 0; opcode < 256; opcode++) {
            for (int flags = irqTakenBase; flags < FLAG_SLOTS; flags++) {
                contents[address(opcode, flags, step)] = value;
            }
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
