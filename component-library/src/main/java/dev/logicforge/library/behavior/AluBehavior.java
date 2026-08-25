package dev.logicforge.library.behavior;

import static dev.logicforge.logic.LogicState.ONE;
import static dev.logicforge.logic.LogicState.UNKNOWN;
import static dev.logicforge.logic.LogicState.ZERO;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperation;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;
import java.util.OptionalLong;

/**
 * An N-bit Arithmetic/Logic Unit. On each evaluation, the operation selected by the OP
 * code is applied to A (and B where applicable) and the result plus status flags are driven
 * onto the outputs.
 *
 * <p>Ports (by index):
 * <ul>
 *   <li>INPUT 0: A (width bits)</li>
 *   <li>INPUT 1: B (width bits)</li>
 *   <li>INPUT 2: OP (4 bits — covers operations 0..9, values 10..15 produce X)</li>
 *   <li>INPUT 3: CIN (1 bit, carry/borrow in)</li>
 *   <li>OUTPUT 0: RESULT (width bits)</li>
 *   <li>OUTPUT 1: ZERO (1 if RESULT==0)</li>
 *   <li>OUTPUT 2: CARRY (carry/shift-out bit)</li>
 *   <li>OUTPUT 3: OVERFLOW (signed overflow for ADD/SUB)</li>
 *   <li>OUTPUT 4: NEGATIVE (MSB of RESULT)</li>
 * </ul>
 *
 * <p>Operation codes:
 * <ol start="0">
 *   <li>ADD  — RESULT = A + B + CIN</li>
 *   <li>SUB  — RESULT = A + ~B + CIN (CIN=1 means no borrow)</li>
 *   <li>AND  — RESULT = A AND B (bitwise, X-propagating)</li>
 *   <li>OR   — RESULT = A OR  B (bitwise, X-propagating)</li>
 *   <li>XOR  — RESULT = A XOR B (bitwise, X-propagating)</li>
 *   <li>NOT  — RESULT = NOT A   (bitwise)</li>
 *   <li>SHL  — RESULT = A shifted left 1; MSB shifted out to CARRY</li>
 *   <li>SHR  — RESULT = A logical-shifted right 1; LSB shifted out to CARRY</li>
 *   <li>PASS A — RESULT = A</li>
 *   <li>PASS B — RESULT = B</li>
 *   <li>10..15 — unknown OP: RESULT=X, all flags=X</li>
 * </ol>
 *
 * @param width bus width of A, B and RESULT
 */
public record AluBehavior(BitWidth width) implements ComponentBehavior {

    // Input port indices
    private static final int IN_A   = 0;
    private static final int IN_B   = 1;
    private static final int IN_OP  = 2;
    private static final int IN_CIN = 3;

    // Output port indices
    private static final int OUT_RESULT   = 0;
    private static final int OUT_ZERO     = 1;
    private static final int OUT_CARRY    = 2;
    private static final int OUT_OVERFLOW = 3;
    private static final int OUT_NEGATIVE = 4;

    // Operation codes
    private static final int OP_ADD   = 0;
    private static final int OP_SUB   = 1;
    private static final int OP_AND   = 2;
    private static final int OP_OR    = 3;
    private static final int OP_XOR   = 4;
    private static final int OP_NOT   = 5;
    private static final int OP_SHL   = 6;
    private static final int OP_SHR   = 7;
    private static final int OP_PASSA = 8;
    private static final int OP_PASSB = 9;

    @Override
    public void evaluate(ComponentContext context) {
        // OP must be fully defined to select any operation
        LogicVector opVec = LogicOperations.asGateInput(context.readInput(IN_OP));
        OptionalLong opLong = opVec.toUnsignedLong();
        if (opLong.isEmpty()) {
            driveAllUnknown(context);
            return;
        }
        int op = (int) opLong.getAsLong();

        LogicVector a = LogicOperations.asGateInput(context.readInput(IN_A));
        LogicVector b = LogicOperations.asGateInput(context.readInput(IN_B));

        switch (op) {
            case OP_ADD   -> evaluateAddSub(context, a, b, false);
            case OP_SUB   -> evaluateAddSub(context, a, b, true);
            case OP_AND   -> evaluateLogical(context, LogicOperation.AND, a, b);
            case OP_OR    -> evaluateLogical(context, LogicOperation.OR,  a, b);
            case OP_XOR   -> evaluateLogical(context, LogicOperation.XOR, a, b);
            case OP_NOT   -> evaluateNot(context, a);
            case OP_SHL   -> evaluateShl(context, a);
            case OP_SHR   -> evaluateShr(context, a);
            case OP_PASSA -> evaluatePass(context, a);
            case OP_PASSB -> evaluatePass(context, b);
            default       -> driveAllUnknown(context);
        }
    }

    /**
     * ADD: RESULT = A + B + CIN.
     * SUB: RESULT = A + ~B + CIN (two's-complement subtraction; CIN=1 means no borrow).
     */
    private void evaluateAddSub(ComponentContext context, LogicVector a, LogicVector b, boolean subtract) {
        OptionalLong aLong = a.toUnsignedLong();
        OptionalLong bLong = b.toUnsignedLong();
        LogicState cin = LogicOperations.asGateInput(context.readInput(IN_CIN).singleBit());

        if (aLong.isEmpty() || bLong.isEmpty() || !cin.isDefined()) {
            driveAllUnknown(context);
            return;
        }

        long aVal = aLong.getAsLong();
        long bVal = bLong.getAsLong();
        long cinBit = (cin == ONE) ? 1L : 0L;

        // For SUB: compute A + ~B + CIN
        if (subtract) {
            bVal = ~bVal;
        }

        int bits = width.bits();
        long result;
        boolean carryOut;
        boolean overflow;

        if (bits == 64) {
            // 64-bit path: detect unsigned carry via Long.compareUnsigned
            long step1 = aVal + bVal;
            boolean carry1 = Long.compareUnsigned(step1, aVal) < 0;
            result = step1 + cinBit;
            boolean carry2 = cinBit == 1 && Long.compareUnsigned(result, step1) < 0;
            carryOut = carry1 || carry2;
            long signBit = Long.MIN_VALUE;
            overflow = ((aVal ^ result) & (bVal ^ result) & signBit) != 0;
        } else {
            long mask = (1L << bits) - 1;
            bVal &= mask;
            long signBit = 1L << (bits - 1);
            long rawSum = aVal + bVal + cinBit;
            carryOut = (rawSum >>> bits & 1L) != 0;
            result = rawSum & mask;
            // Signed overflow: when both operands share a sign that differs from result's sign
            overflow = ((aVal ^ result) & (bVal ^ result) & signBit) != 0;
        }

        LogicVector resultVec = LogicVector.fromUnsignedLong(result, bits);
        context.driveOutput(OUT_RESULT,   resultVec);
        context.driveOutput(OUT_ZERO,     zeroFlag(resultVec));
        context.driveOutput(OUT_CARRY,    LogicVector.single(LogicState.of(carryOut)));
        context.driveOutput(OUT_OVERFLOW, LogicVector.single(LogicState.of(overflow)));
        context.driveOutput(OUT_NEGATIVE, LogicVector.single(resultVec.getBit(bits - 1)));
    }

    /** Bitwise logical operation — handles X propagation automatically via LogicOperations. */
    private void evaluateLogical(ComponentContext context, LogicOperation op, LogicVector a, LogicVector b) {
        LogicVector result = LogicOperations.apply(op, a, b);
        context.driveOutput(OUT_RESULT,   result);
        context.driveOutput(OUT_ZERO,     zeroFlag(result));
        context.driveOutput(OUT_CARRY,    LogicVector.ZERO);
        context.driveOutput(OUT_OVERFLOW, LogicVector.ZERO);
        context.driveOutput(OUT_NEGATIVE, LogicVector.single(result.getBit(width.bits() - 1)));
    }

    /** Bitwise NOT A. */
    private void evaluateNot(ComponentContext context, LogicVector a) {
        LogicVector result = LogicOperations.not(a);
        context.driveOutput(OUT_RESULT,   result);
        context.driveOutput(OUT_ZERO,     zeroFlag(result));
        context.driveOutput(OUT_CARRY,    LogicVector.ZERO);
        context.driveOutput(OUT_OVERFLOW, LogicVector.ZERO);
        context.driveOutput(OUT_NEGATIVE, LogicVector.single(result.getBit(width.bits() - 1)));
    }

    /** SHL: A &lt;&lt; 1; MSB of A goes to CARRY, vacated LSB = 0. */
    private void evaluateShl(ComponentContext context, LogicVector a) {
        int bits = width.bits();
        LogicState carry = a.getBit(bits - 1); // MSB is shifted out
        LogicVector result = LogicVector.repeat(ZERO, bits);
        for (int i = 1; i < bits; i++) {
            result = result.withBit(i, a.getBit(i - 1));
        }
        context.driveOutput(OUT_RESULT,   result);
        context.driveOutput(OUT_ZERO,     zeroFlag(result));
        context.driveOutput(OUT_CARRY,    LogicVector.single(carry));
        context.driveOutput(OUT_OVERFLOW, LogicVector.ZERO);
        context.driveOutput(OUT_NEGATIVE, LogicVector.single(result.getBit(bits - 1)));
    }

    /** SHR: A &gt;&gt; 1 (logical); LSB of A goes to CARRY, vacated MSB = 0. */
    private void evaluateShr(ComponentContext context, LogicVector a) {
        int bits = width.bits();
        LogicState carry = a.getBit(0); // LSB is shifted out
        LogicVector result = LogicVector.repeat(ZERO, bits);
        for (int i = 0; i < bits - 1; i++) {
            result = result.withBit(i, a.getBit(i + 1));
        }
        context.driveOutput(OUT_RESULT,   result);
        context.driveOutput(OUT_ZERO,     zeroFlag(result));
        context.driveOutput(OUT_CARRY,    LogicVector.single(carry));
        context.driveOutput(OUT_OVERFLOW, LogicVector.ZERO);
        context.driveOutput(OUT_NEGATIVE, LogicVector.single(result.getBit(bits - 1)));
    }

    /** PASS: drive the given vector through unchanged. */
    private void evaluatePass(ComponentContext context, LogicVector val) {
        int bits = width.bits();
        context.driveOutput(OUT_RESULT,   val);
        context.driveOutput(OUT_ZERO,     zeroFlag(val));
        context.driveOutput(OUT_CARRY,    LogicVector.ZERO);
        context.driveOutput(OUT_OVERFLOW, LogicVector.ZERO);
        context.driveOutput(OUT_NEGATIVE, LogicVector.single(val.getBit(bits - 1)));
    }

    /** Drives X onto all outputs — used for unknown OP or unknown arithmetic operands. */
    private void driveAllUnknown(ComponentContext context) {
        context.driveOutput(OUT_RESULT,   LogicVector.repeat(UNKNOWN, width));
        context.driveOutput(OUT_ZERO,     LogicVector.UNKNOWN);
        context.driveOutput(OUT_CARRY,    LogicVector.UNKNOWN);
        context.driveOutput(OUT_OVERFLOW, LogicVector.UNKNOWN);
        context.driveOutput(OUT_NEGATIVE, LogicVector.UNKNOWN);
    }

    /**
     * Computes the ZERO flag:
     * <ul>
     *   <li>ONE     if every bit is ZERO</li>
     *   <li>ZERO    if any bit is ONE</li>
     *   <li>UNKNOWN if any bit is unknown and none are ONE</li>
     * </ul>
     */
    private static LogicVector zeroFlag(LogicVector result) {
        boolean hasUnknown = false;
        for (int i = 0; i < result.width(); i++) {
            LogicState bit = result.getBit(i);
            if (bit == ONE) {
                return LogicVector.ZERO;
            }
            if (bit != ZERO) {
                hasUnknown = true;
            }
        }
        return hasUnknown ? LogicVector.UNKNOWN : LogicVector.ONE;
    }

    /** This is a combinational component — no persistent state. */
    @Override
    public ComponentRuntimeState createState() {
        return ComponentRuntimeState.STATELESS;
    }
}
