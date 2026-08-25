package dev.logicforge.processor.mos6502;

import dev.logicforge.processor.BusAccess;
import dev.logicforge.processor.BusObserver;
import dev.logicforge.processor.ByteBus;
import dev.logicforge.processor.IllegalOpcodeException;
import java.util.Objects;

/**
 * Complete documented-instruction NMOS MOS 6502 functional model.
 *
 * <p>The model implements all 151 documented opcodes, every documented addressing mode,
 * stack/interrupt behavior, the NMOS JMP-indirect page-wrap quirk, page-cross cycle
 * penalties and decimal ADC/SBC. It is intentionally a <em>functional instruction model</em>:
 * {@link #stepInstruction()} executes one complete instruction and returns the documented
 * cycle count. It does not claim cycle-by-cycle external pin timing or dummy-bus accesses.
 * Those belong to the transistor model (or a future dedicated microcycle model).</p>
 *
 * <p>Status bit 5 is presented as one. The B flag is not stored because it is not a physical
 * status latch on the NMOS 6502; it is synthesized only when PHP/BRK push P.</p>
 */
public final class Mos6502 {
    public static final int FLAG_C = 0x01;
    public static final int FLAG_Z = 0x02;
    public static final int FLAG_I = 0x04;
    public static final int FLAG_D = 0x08;
    public static final int FLAG_B = 0x10;
    public static final int FLAG_U = 0x20;
    public static final int FLAG_V = 0x40;
    public static final int FLAG_N = 0x80;

    private final ByteBus bus;
    private BusObserver observer;

    private int a;
    private int x;
    private int y;
    private int sp;
    private int pc;
    private int p;

    private boolean irqAsserted;
    private boolean nmiLineAsserted;
    private boolean nmiPending;
    private boolean soLineAsserted;

    private long instructions;
    private long cycles;
    private long accessOrdinal;

    public Mos6502(ByteBus bus) {
        this(bus, BusObserver.NONE);
    }

    public Mos6502(ByteBus bus, BusObserver observer) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.observer = Objects.requireNonNull(observer, "observer");
        initialiseDeterministically();
    }

    /**
     * Deterministic power-on state for simulation. Real NMOS silicon does not promise
     * deterministic A/X/Y contents; LogicForge does so that tests and saved traces repeat.
     */
    public void initialiseDeterministically() {
        a = x = y = 0;
        sp = 0xfd;
        p = FLAG_U | FLAG_I;
        pc = 0;
        irqAsserted = false;
        nmiLineAsserted = false;
        nmiPending = false;
        soLineAsserted = false;
        instructions = 0;
        cycles = 0;
        accessOrdinal = 0;
    }

    /** Read the reset vector at FFFC/FFFD and enter the normal post-reset state. */
    public void reset() {
        sp = 0xfd;
        p = FLAG_U | FLAG_I;
        nmiPending = false;
        pc = readWord(0xfffc);
        cycles += 7;
    }

    /** Execute exactly one instruction, or one pending hardware interrupt entry. */
    public int stepInstruction() {
        if (nmiPending) {
            nmiPending = false;
            serviceInterrupt(0xfffa);
            cycles += 7;
            return 7;
        }
        if (irqAsserted && !flag(FLAG_I)) {
            serviceInterrupt(0xfffe);
            cycles += 7;
            return 7;
        }

        int opcodeAddress = pc;
        int opcode = fetchByte();
        int used = execute(opcode, opcodeAddress);
        instructions++;
        cycles += used;
        return used;
    }

    private int execute(int opcode, int opcodeAddress) {
        return switch (opcode) {
            // BRK / control flow / interrupts
            case 0x00 -> brk();
            case 0x20 -> jsr();
            case 0x40 -> rti();
            case 0x60 -> rts();
            case 0x4c -> { pc = fetchWord(); yield 3; }
            case 0x6c -> { pc = readWordIndirectBug(fetchWord()); yield 5; }

            // Conditional branches
            case 0x10 -> branch(!flag(FLAG_N)); // BPL
            case 0x30 -> branch(flag(FLAG_N));  // BMI
            case 0x50 -> branch(!flag(FLAG_V)); // BVC
            case 0x70 -> branch(flag(FLAG_V));  // BVS
            case 0x90 -> branch(!flag(FLAG_C)); // BCC
            case 0xb0 -> branch(flag(FLAG_C));  // BCS
            case 0xd0 -> branch(!flag(FLAG_Z)); // BNE
            case 0xf0 -> branch(flag(FLAG_Z));  // BEQ

            // Flag instructions
            case 0x18 -> { setFlag(FLAG_C, false); yield 2; } // CLC
            case 0x38 -> { setFlag(FLAG_C, true);  yield 2; } // SEC
            case 0x58 -> { setFlag(FLAG_I, false); yield 2; } // CLI
            case 0x78 -> { setFlag(FLAG_I, true);  yield 2; } // SEI
            case 0xb8 -> { setFlag(FLAG_V, false); yield 2; } // CLV
            case 0xd8 -> { setFlag(FLAG_D, false); yield 2; } // CLD
            case 0xf8 -> { setFlag(FLAG_D, true);  yield 2; } // SED

            // NOP
            case 0xea -> 2;

            // LDA
            case 0xa9 -> { a = fetchByte(); setNZ(a); yield 2; }
            case 0xa5 -> { a = read(zp()); setNZ(a); yield 3; }
            case 0xb5 -> { a = read(zpX()); setNZ(a); yield 4; }
            case 0xad -> { a = read(abs()); setNZ(a); yield 4; }
            case 0xbd -> { IndexedAddress q = absX(); a = read(q.address()); setNZ(a); yield 4 + b(q.pageCrossed()); }
            case 0xb9 -> { IndexedAddress q = absY(); a = read(q.address()); setNZ(a); yield 4 + b(q.pageCrossed()); }
            case 0xa1 -> { a = read(indX()); setNZ(a); yield 6; }
            case 0xb1 -> { IndexedAddress q = indY(); a = read(q.address()); setNZ(a); yield 5 + b(q.pageCrossed()); }

            // LDX
            case 0xa2 -> { x = fetchByte(); setNZ(x); yield 2; }
            case 0xa6 -> { x = read(zp()); setNZ(x); yield 3; }
            case 0xb6 -> { x = read(zpY()); setNZ(x); yield 4; }
            case 0xae -> { x = read(abs()); setNZ(x); yield 4; }
            case 0xbe -> { IndexedAddress q = absY(); x = read(q.address()); setNZ(x); yield 4 + b(q.pageCrossed()); }

            // LDY
            case 0xa0 -> { y = fetchByte(); setNZ(y); yield 2; }
            case 0xa4 -> { y = read(zp()); setNZ(y); yield 3; }
            case 0xb4 -> { y = read(zpX()); setNZ(y); yield 4; }
            case 0xac -> { y = read(abs()); setNZ(y); yield 4; }
            case 0xbc -> { IndexedAddress q = absX(); y = read(q.address()); setNZ(y); yield 4 + b(q.pageCrossed()); }

            // STA
            case 0x85 -> { write(zp(), a); yield 3; }
            case 0x95 -> { write(zpX(), a); yield 4; }
            case 0x8d -> { write(abs(), a); yield 4; }
            case 0x9d -> { write(absX().address(), a); yield 5; }
            case 0x99 -> { write(absY().address(), a); yield 5; }
            case 0x81 -> { write(indX(), a); yield 6; }
            case 0x91 -> { write(indY().address(), a); yield 6; }

            // STX
            case 0x86 -> { write(zp(), x); yield 3; }
            case 0x96 -> { write(zpY(), x); yield 4; }
            case 0x8e -> { write(abs(), x); yield 4; }

            // STY
            case 0x84 -> { write(zp(), y); yield 3; }
            case 0x94 -> { write(zpX(), y); yield 4; }
            case 0x8c -> { write(abs(), y); yield 4; }

            // Transfers
            case 0xaa -> { x = a; setNZ(x); yield 2; } // TAX
            case 0xa8 -> { y = a; setNZ(y); yield 2; } // TAY
            case 0xba -> { x = sp; setNZ(x); yield 2; } // TSX
            case 0x8a -> { a = x; setNZ(a); yield 2; } // TXA
            case 0x9a -> { sp = x; yield 2; }          // TXS
            case 0x98 -> { a = y; setNZ(a); yield 2; } // TYA

            // Stack
            case 0x48 -> { push(a); yield 3; } // PHA
            case 0x08 -> { push(statusForPush(true)); yield 3; } // PHP
            case 0x68 -> { a = pull(); setNZ(a); yield 4; } // PLA
            case 0x28 -> { restoreStatus(pull()); yield 4; } // PLP

            // AND
            case 0x29 -> { and(fetchByte()); yield 2; }
            case 0x25 -> { and(read(zp())); yield 3; }
            case 0x35 -> { and(read(zpX())); yield 4; }
            case 0x2d -> { and(read(abs())); yield 4; }
            case 0x3d -> { IndexedAddress q = absX(); and(read(q.address())); yield 4 + b(q.pageCrossed()); }
            case 0x39 -> { IndexedAddress q = absY(); and(read(q.address())); yield 4 + b(q.pageCrossed()); }
            case 0x21 -> { and(read(indX())); yield 6; }
            case 0x31 -> { IndexedAddress q = indY(); and(read(q.address())); yield 5 + b(q.pageCrossed()); }

            // ORA
            case 0x09 -> { ora(fetchByte()); yield 2; }
            case 0x05 -> { ora(read(zp())); yield 3; }
            case 0x15 -> { ora(read(zpX())); yield 4; }
            case 0x0d -> { ora(read(abs())); yield 4; }
            case 0x1d -> { IndexedAddress q = absX(); ora(read(q.address())); yield 4 + b(q.pageCrossed()); }
            case 0x19 -> { IndexedAddress q = absY(); ora(read(q.address())); yield 4 + b(q.pageCrossed()); }
            case 0x01 -> { ora(read(indX())); yield 6; }
            case 0x11 -> { IndexedAddress q = indY(); ora(read(q.address())); yield 5 + b(q.pageCrossed()); }

            // EOR
            case 0x49 -> { eor(fetchByte()); yield 2; }
            case 0x45 -> { eor(read(zp())); yield 3; }
            case 0x55 -> { eor(read(zpX())); yield 4; }
            case 0x4d -> { eor(read(abs())); yield 4; }
            case 0x5d -> { IndexedAddress q = absX(); eor(read(q.address())); yield 4 + b(q.pageCrossed()); }
            case 0x59 -> { IndexedAddress q = absY(); eor(read(q.address())); yield 4 + b(q.pageCrossed()); }
            case 0x41 -> { eor(read(indX())); yield 6; }
            case 0x51 -> { IndexedAddress q = indY(); eor(read(q.address())); yield 5 + b(q.pageCrossed()); }

            // ADC
            case 0x69 -> { adc(fetchByte()); yield 2; }
            case 0x65 -> { adc(read(zp())); yield 3; }
            case 0x75 -> { adc(read(zpX())); yield 4; }
            case 0x6d -> { adc(read(abs())); yield 4; }
            case 0x7d -> { IndexedAddress q = absX(); adc(read(q.address())); yield 4 + b(q.pageCrossed()); }
            case 0x79 -> { IndexedAddress q = absY(); adc(read(q.address())); yield 4 + b(q.pageCrossed()); }
            case 0x61 -> { adc(read(indX())); yield 6; }
            case 0x71 -> { IndexedAddress q = indY(); adc(read(q.address())); yield 5 + b(q.pageCrossed()); }

            // SBC
            case 0xe9 -> { sbc(fetchByte()); yield 2; }
            case 0xe5 -> { sbc(read(zp())); yield 3; }
            case 0xf5 -> { sbc(read(zpX())); yield 4; }
            case 0xed -> { sbc(read(abs())); yield 4; }
            case 0xfd -> { IndexedAddress q = absX(); sbc(read(q.address())); yield 4 + b(q.pageCrossed()); }
            case 0xf9 -> { IndexedAddress q = absY(); sbc(read(q.address())); yield 4 + b(q.pageCrossed()); }
            case 0xe1 -> { sbc(read(indX())); yield 6; }
            case 0xf1 -> { IndexedAddress q = indY(); sbc(read(q.address())); yield 5 + b(q.pageCrossed()); }

            // CMP
            case 0xc9 -> { compare(a, fetchByte()); yield 2; }
            case 0xc5 -> { compare(a, read(zp())); yield 3; }
            case 0xd5 -> { compare(a, read(zpX())); yield 4; }
            case 0xcd -> { compare(a, read(abs())); yield 4; }
            case 0xdd -> { IndexedAddress q = absX(); compare(a, read(q.address())); yield 4 + b(q.pageCrossed()); }
            case 0xd9 -> { IndexedAddress q = absY(); compare(a, read(q.address())); yield 4 + b(q.pageCrossed()); }
            case 0xc1 -> { compare(a, read(indX())); yield 6; }
            case 0xd1 -> { IndexedAddress q = indY(); compare(a, read(q.address())); yield 5 + b(q.pageCrossed()); }

            // CPX / CPY
            case 0xe0 -> { compare(x, fetchByte()); yield 2; }
            case 0xe4 -> { compare(x, read(zp())); yield 3; }
            case 0xec -> { compare(x, read(abs())); yield 4; }
            case 0xc0 -> { compare(y, fetchByte()); yield 2; }
            case 0xc4 -> { compare(y, read(zp())); yield 3; }
            case 0xcc -> { compare(y, read(abs())); yield 4; }

            // BIT
            case 0x24 -> { bit(read(zp())); yield 3; }
            case 0x2c -> { bit(read(abs())); yield 4; }

            // INC / DEC register
            case 0xe8 -> { x = (x + 1) & 0xff; setNZ(x); yield 2; }
            case 0xc8 -> { y = (y + 1) & 0xff; setNZ(y); yield 2; }
            case 0xca -> { x = (x - 1) & 0xff; setNZ(x); yield 2; }
            case 0x88 -> { y = (y - 1) & 0xff; setNZ(y); yield 2; }

            // INC memory
            case 0xe6 -> { inc(zp()); yield 5; }
            case 0xf6 -> { inc(zpX()); yield 6; }
            case 0xee -> { inc(abs()); yield 6; }
            case 0xfe -> { inc(absX().address()); yield 7; }

            // DEC memory
            case 0xc6 -> { dec(zp()); yield 5; }
            case 0xd6 -> { dec(zpX()); yield 6; }
            case 0xce -> { dec(abs()); yield 6; }
            case 0xde -> { dec(absX().address()); yield 7; }

            // ASL accumulator / memory
            case 0x0a -> { a = aslValue(a); yield 2; }
            case 0x06 -> { rmw(zp(), this::aslValue); yield 5; }
            case 0x16 -> { rmw(zpX(), this::aslValue); yield 6; }
            case 0x0e -> { rmw(abs(), this::aslValue); yield 6; }
            case 0x1e -> { rmw(absX().address(), this::aslValue); yield 7; }

            // LSR
            case 0x4a -> { a = lsrValue(a); yield 2; }
            case 0x46 -> { rmw(zp(), this::lsrValue); yield 5; }
            case 0x56 -> { rmw(zpX(), this::lsrValue); yield 6; }
            case 0x4e -> { rmw(abs(), this::lsrValue); yield 6; }
            case 0x5e -> { rmw(absX().address(), this::lsrValue); yield 7; }

            // ROL
            case 0x2a -> { a = rolValue(a); yield 2; }
            case 0x26 -> { rmw(zp(), this::rolValue); yield 5; }
            case 0x36 -> { rmw(zpX(), this::rolValue); yield 6; }
            case 0x2e -> { rmw(abs(), this::rolValue); yield 6; }
            case 0x3e -> { rmw(absX().address(), this::rolValue); yield 7; }

            // ROR
            case 0x6a -> { a = rorValue(a); yield 2; }
            case 0x66 -> { rmw(zp(), this::rorValue); yield 5; }
            case 0x76 -> { rmw(zpX(), this::rorValue); yield 6; }
            case 0x6e -> { rmw(abs(), this::rorValue); yield 6; }
            case 0x7e -> { rmw(absX().address(), this::rorValue); yield 7; }

            default -> throw new IllegalOpcodeException(opcode, opcodeAddress);
        };
    }

    private int brk() {
        // BRK has a padding/signature byte; the stacked PC points past it.
        pc = (pc + 1) & 0xffff;
        push((pc >>> 8) & 0xff);
        push(pc & 0xff);
        push(statusForPush(true));
        setFlag(FLAG_I, true);
        pc = readWord(0xfffe);
        return 7;
    }

    private int jsr() {
        int target = fetchWord();
        int returnAddress = (pc - 1) & 0xffff;
        push((returnAddress >>> 8) & 0xff);
        push(returnAddress & 0xff);
        pc = target;
        return 6;
    }

    private int rts() {
        int lo = pull();
        int hi = pull();
        pc = (((hi << 8) | lo) + 1) & 0xffff;
        return 6;
    }

    private int rti() {
        restoreStatus(pull());
        int lo = pull();
        int hi = pull();
        pc = (hi << 8) | lo;
        return 6;
    }

    private void serviceInterrupt(int vector) {
        push((pc >>> 8) & 0xff);
        push(pc & 0xff);
        push(statusForPush(false));
        setFlag(FLAG_I, true);
        pc = readWord(vector);
    }

    private int branch(boolean condition) {
        int displacement = (byte) fetchByte();
        if (!condition) {
            return 2;
        }
        int oldPc = pc;
        pc = (pc + displacement) & 0xffff;
        return 3 + b((oldPc & 0xff00) != (pc & 0xff00));
    }

    private void adc(int value) {
        value &= 0xff;
        int oldA = a;
        int carryIn = flag(FLAG_C) ? 1 : 0;
        int binary = oldA + value + carryIn;
        int binaryResult = binary & 0xff;

        if (!flag(FLAG_D)) {
            setFlag(FLAG_C, binary > 0xff);
            setFlag(FLAG_V, ((~(oldA ^ value) & (oldA ^ binaryResult)) & 0x80) != 0);
            a = binaryResult;
            setNZ(a);
            return;
        }

        // NMOS decimal mode. N/V/Z reflect the binary/intermediate ALU path, not the final
        // BCD-corrected result. This is deliberate and differs from CMOS 65C02 behavior.
        int low = (oldA & 0x0f) + (value & 0x0f) + carryIn;
        int carryToHigh = 0;
        if (low > 9) {
            low = (low - 10) & 0x0f;
            carryToHigh = 1;
        }
        int high = (oldA >>> 4) + (value >>> 4) + carryToHigh;
        boolean intermediateNegative = (high & 0x08) != 0;
        boolean decimalCarry = high > 9;
        if (decimalCarry) {
            high = (high - 10) & 0x0f;
        }

        setFlag(FLAG_N, intermediateNegative);
        setFlag(FLAG_V, (((oldA & 0x80) != 0) ^ intermediateNegative)
                && (((value & 0x80) != 0) ^ intermediateNegative));
        setFlag(FLAG_Z, binaryResult == 0);
        setFlag(FLAG_C, decimalCarry);
        a = ((high << 4) | low) & 0xff;
    }

    private void sbc(int value) {
        value &= 0xff;
        int oldA = a;
        int borrow = flag(FLAG_C) ? 0 : 1;
        int binary = oldA - value - borrow;
        int binaryResult = binary & 0xff;
        boolean overflow = (((oldA ^ binaryResult) & (oldA ^ value)) & 0x80) != 0;

        if (!flag(FLAG_D)) {
            a = binaryResult;
            setFlag(FLAG_C, binary >= 0);
            setFlag(FLAG_V, overflow);
            setNZ(a);
            return;
        }

        int low = (oldA & 0x0f) - (value & 0x0f) - borrow;
        int high = (oldA >>> 4) - (value >>> 4);
        if (low < 0) {
            low -= 6;
            high--;
        }
        if (high < 0) {
            high -= 6;
        }
        a = ((high << 4) | (low & 0x0f)) & 0xff;

        // NMOS flags are sourced from the binary ALU path in decimal subtraction.
        setFlag(FLAG_C, binary >= 0);
        setFlag(FLAG_V, overflow);
        setFlag(FLAG_Z, binaryResult == 0);
        setFlag(FLAG_N, (binaryResult & 0x80) != 0);
    }

    private void and(int value) {
        a &= value;
        a &= 0xff;
        setNZ(a);
    }

    private void ora(int value) {
        a = (a | value) & 0xff;
        setNZ(a);
    }

    private void eor(int value) {
        a = (a ^ value) & 0xff;
        setNZ(a);
    }

    private void compare(int register, int value) {
        int difference = (register & 0xff) - (value & 0xff);
        setFlag(FLAG_C, difference >= 0);
        setFlag(FLAG_Z, (difference & 0xff) == 0);
        setFlag(FLAG_N, (difference & 0x80) != 0);
    }

    private void bit(int value) {
        value &= 0xff;
        setFlag(FLAG_Z, (a & value) == 0);
        setFlag(FLAG_N, (value & 0x80) != 0);
        setFlag(FLAG_V, (value & 0x40) != 0);
    }

    private void inc(int address) {
        int value = (read(address) + 1) & 0xff;
        write(address, value);
        setNZ(value);
    }

    private void dec(int address) {
        int value = (read(address) - 1) & 0xff;
        write(address, value);
        setNZ(value);
    }

    private int aslValue(int value) {
        value &= 0xff;
        setFlag(FLAG_C, (value & 0x80) != 0);
        int result = (value << 1) & 0xff;
        setNZ(result);
        return result;
    }

    private int lsrValue(int value) {
        value &= 0xff;
        setFlag(FLAG_C, (value & 0x01) != 0);
        int result = value >>> 1;
        setNZ(result);
        return result;
    }

    private int rolValue(int value) {
        value &= 0xff;
        int carryIn = flag(FLAG_C) ? 1 : 0;
        setFlag(FLAG_C, (value & 0x80) != 0);
        int result = ((value << 1) | carryIn) & 0xff;
        setNZ(result);
        return result;
    }

    private int rorValue(int value) {
        value &= 0xff;
        int carryIn = flag(FLAG_C) ? 0x80 : 0;
        setFlag(FLAG_C, (value & 0x01) != 0);
        int result = ((value >>> 1) | carryIn) & 0xff;
        setNZ(result);
        return result;
    }

    private void rmw(int address, ByteUnaryOperator operation) {
        int result = operation.apply(read(address));
        write(address, result);
    }

    private int zp() {
        return fetchByte();
    }

    private int zpX() {
        return (fetchByte() + x) & 0xff;
    }

    private int zpY() {
        return (fetchByte() + y) & 0xff;
    }

    private int abs() {
        return fetchWord();
    }

    private IndexedAddress absX() {
        int base = fetchWord();
        int address = (base + x) & 0xffff;
        return new IndexedAddress(address, pageCrossed(base, address));
    }

    private IndexedAddress absY() {
        int base = fetchWord();
        int address = (base + y) & 0xffff;
        return new IndexedAddress(address, pageCrossed(base, address));
    }

    private int indX() {
        int pointer = (fetchByte() + x) & 0xff;
        int lo = read(pointer);
        int hi = read((pointer + 1) & 0xff);
        return (hi << 8) | lo;
    }

    private IndexedAddress indY() {
        int pointer = fetchByte();
        int lo = read(pointer);
        int hi = read((pointer + 1) & 0xff);
        int base = (hi << 8) | lo;
        int address = (base + y) & 0xffff;
        return new IndexedAddress(address, pageCrossed(base, address));
    }

    private int fetchByte() {
        int value = read(pc);
        pc = (pc + 1) & 0xffff;
        return value;
    }

    private int fetchWord() {
        int lo = fetchByte();
        int hi = fetchByte();
        return (hi << 8) | lo;
    }

    private int readWord(int address) {
        int lo = read(address);
        int hi = read((address + 1) & 0xffff);
        return (hi << 8) | lo;
    }

    /** Original NMOS 6502 JMP ($xxFF) reads the high byte from $xx00, not $(xx+1)00. */
    private int readWordIndirectBug(int pointer) {
        int lo = read(pointer);
        int highAddress = (pointer & 0xff00) | ((pointer + 1) & 0x00ff);
        int hi = read(highAddress);
        return (hi << 8) | lo;
    }

    private void push(int value) {
        write(0x0100 | sp, value);
        sp = (sp - 1) & 0xff;
    }

    private int pull() {
        sp = (sp + 1) & 0xff;
        return read(0x0100 | sp);
    }

    private int statusForPush(boolean breakBit) {
        return (p | FLAG_U | (breakBit ? FLAG_B : 0)) & 0xff;
    }

    private void restoreStatus(int value) {
        p = (value | FLAG_U) & ~FLAG_B & 0xff;
    }

    private int read(int address) {
        int a16 = address & 0xffff;
        int value = bus.read(a16) & 0xff;
        observer.onAccess(new BusAccess(accessOrdinal++, BusAccess.Type.READ, a16, value));
        return value;
    }

    private void write(int address, int value) {
        int a16 = address & 0xffff;
        int v8 = value & 0xff;
        bus.write(a16, v8);
        observer.onAccess(new BusAccess(accessOrdinal++, BusAccess.Type.WRITE, a16, v8));
    }

    private void setNZ(int value) {
        value &= 0xff;
        setFlag(FLAG_Z, value == 0);
        setFlag(FLAG_N, (value & 0x80) != 0);
    }

    private boolean flag(int mask) {
        return (p & mask) != 0;
    }

    private void setFlag(int mask, boolean set) {
        if (set) {
            p |= mask;
        } else {
            p &= ~mask;
        }
        p = (p | FLAG_U) & ~FLAG_B & 0xff;
    }

    private static boolean pageCrossed(int base, int address) {
        return (base & 0xff00) != (address & 0xff00);
    }

    private static int b(boolean value) {
        return value ? 1 : 0;
    }

    /** IRQ is active-low electrically; this API uses the semantic asserted state. */
    public void setIrqAsserted(boolean asserted) {
        irqAsserted = asserted;
    }

    /** NMI is edge-sensitive. A false->true asserted transition latches one NMI. */
    public void setNmiAsserted(boolean asserted) {
        if (asserted && !nmiLineAsserted) {
            nmiPending = true;
        }
        nmiLineAsserted = asserted;
    }

    /** SO is active-low and sets V on its asserted edge. */
    public void setSoAsserted(boolean asserted) {
        if (asserted && !soLineAsserted) {
            setFlag(FLAG_V, true);
        }
        soLineAsserted = asserted;
    }

    public void setBusObserver(BusObserver observer) {
        this.observer = Objects.requireNonNull(observer, "observer");
    }

    public Mos6502Snapshot snapshot() {
        return new Mos6502Snapshot(a, x, y, sp, pc, status(), instructions, cycles,
                irqAsserted, nmiPending);
    }

    public int a() { return a; }
    public int x() { return x; }
    public int y() { return y; }
    public int sp() { return sp; }
    public int pc() { return pc; }
    public int status() { return (p | FLAG_U) & ~FLAG_B & 0xff; }
    public long instructions() { return instructions; }
    public long cycles() { return cycles; }

    // Explicit debugger/test setters. Values are masked to the physical register width.
    public void setA(int value) { a = value & 0xff; }
    public void setX(int value) { x = value & 0xff; }
    public void setY(int value) { y = value & 0xff; }
    public void setSp(int value) { sp = value & 0xff; }
    public void setPc(int value) { pc = value & 0xffff; }
    public void setStatus(int value) { restoreStatus(value); }

    public boolean carry() { return flag(FLAG_C); }
    public boolean zero() { return flag(FLAG_Z); }
    public boolean interruptDisable() { return flag(FLAG_I); }
    public boolean decimal() { return flag(FLAG_D); }
    public boolean overflow() { return flag(FLAG_V); }
    public boolean negative() { return flag(FLAG_N); }

    private record IndexedAddress(int address, boolean pageCrossed) { }

    @FunctionalInterface
    private interface ByteUnaryOperator {
        int apply(int value);
    }
}
