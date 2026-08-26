package dev.logicforge.library.behavior;

import static dev.logicforge.logic.LogicState.ONE;
import static dev.logicforge.logic.LogicState.ZERO;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;
import java.util.OptionalLong;

/**
 * A synchronous register file with two independent read ports and one synchronous write
 * port. Both reads are combinational (current stored value on every evaluation); the write
 * is edge-triggered on a rising CLK edge while WRITE_ENABLE is high.
 *
 * <p>Ports (by index):
 * <ul>
 *   <li>INPUT 0: READ_ADDR_A  (addrWidth bits)</li>
 *   <li>INPUT 1: READ_ADDR_B  (addrWidth bits)</li>
 *   <li>INPUT 2: WRITE_ADDR   (addrWidth bits)</li>
 *   <li>INPUT 3: WRITE_DATA   (width bits)</li>
 *   <li>INPUT 4: WRITE_ENABLE (1 bit)</li>
 *   <li>INPUT 5: CLK          (1 bit)</li>
 *   <li>OUTPUT 0: READ_DATA_A (width bits)</li>
 *   <li>OUTPUT 1: READ_DATA_B (width bits)</li>
 * </ul>
 *
 * <p>Address width is the smallest number of bits required to address {@code registerCount}
 * registers: {@code ceil(log2(registerCount))}, with a minimum of 1.
 *
 * @param width         bus width of data ports
 * @param registerCount number of internal registers (2..32)
 */
public record RegisterFileBehavior(BitWidth width, int registerCount) implements ComponentBehavior {

    private static final int IN_READ_ADDR_A  = 0;
    private static final int IN_READ_ADDR_B  = 1;
    private static final int IN_WRITE_ADDR   = 2;
    private static final int IN_WRITE_DATA   = 3;
    private static final int IN_WRITE_ENABLE = 4;
    private static final int IN_CLK          = 5;

    private static final int OUT_READ_DATA_A = 0;
    private static final int OUT_READ_DATA_B = 1;

    /**
     * Returns the address bus width needed for {@code count} registers:
     * {@code ceil(log2(count))}, minimum 1.
     */
    public static int addrBits(int count) {
        return Math.max(1, 32 - Integer.numberOfLeadingZeros(count - 1));
    }

    @Override
    public void evaluate(ComponentContext context) {
        RegisterFileState state = (RegisterFileState) context.state();

        // --- Synchronous write on rising CLK edge while WRITE_ENABLE=1 ---
        LogicState clock = LogicOperations.asGateInput(context.readInput(IN_CLK).singleBit());
        boolean risingEdge = state.lastClock == ZERO && clock == ONE;
        if (risingEdge) {
            LogicState we = LogicOperations.asGateInput(context.readInput(IN_WRITE_ENABLE).singleBit());
            if (we != ZERO) {
                LogicVector address = LogicOperations.asGateInput(context.readInput(IN_WRITE_ADDR));
                LogicVector data = LogicOperations.asGateInput(context.readInput(IN_WRITE_DATA));
                LogicVector[] written = possibleWrite(state.registers, address, data);
                for (int register = 0; register < registerCount; register++) {
                    state.registers[register] = StatefulControlPolicy.choose(
                            we, state.registers[register], written[register]);
                }
            }
        }
        state.lastClock = clock;

        // --- Combinational reads ---
        context.driveOutput(OUT_READ_DATA_A, readRegister(state, context, IN_READ_ADDR_A));
        context.driveOutput(OUT_READ_DATA_B, readRegister(state, context, IN_READ_ADDR_B));
    }

    /** Reads the register at the given address input, returning X for an unknown/out-of-range address. */
    private LogicVector readRegister(RegisterFileState state, ComponentContext context, int addrPort) {
        LogicVector addrVec = LogicOperations.asGateInput(context.readInput(addrPort));
        OptionalLong addrOpt = addrVec.toUnsignedLong();
        if (addrOpt.isEmpty()) {
            return LogicVector.repeat(LogicState.UNKNOWN, width);
        }
        long addr = addrOpt.getAsLong();
        if (addr < 0 || addr >= registerCount) {
            return LogicVector.repeat(LogicState.UNKNOWN, width);
        }
        return state.registers[(int) addr];
    }

    private LogicVector[] possibleWrite(LogicVector[] current, LogicVector address,
                                        LogicVector data) {
        LogicVector[] result = current.clone();
        int encodedAddresses = 1 << address.width();
        int possibleCount = 0;
        for (int candidate = 0; candidate < encodedAddresses; candidate++) {
            if (StatefulControlPolicy.isPossible(address, candidate)) {
                possibleCount++;
            }
        }
        for (int register = 0; register < registerCount; register++) {
            if (!StatefulControlPolicy.isPossible(address, register)) {
                continue;
            }
            result[register] = possibleCount == 1
                    ? data : StatefulControlPolicy.merge(current[register], data);
        }
        return result;
    }

    @Override
    public ComponentRuntimeState createState() {
        return new RegisterFileState(width, registerCount);
    }
}
