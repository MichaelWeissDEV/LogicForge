package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;
import java.util.OptionalLong;

/**
 * A generic RAM: while CS is 1, WE is 0 and OE is 1, drives the addressed word onto the
 * bidirectional DATA bus; while CS is 1 and WE is 1, stores whatever is currently on DATA
 * at the addressed word instead of driving it — RAM never fights an external driver during
 * a write. Otherwise (CS inactive, or OE inactive while not writing) DATA floats.
 *
 * <p>Any of CS/WE/OE being undefined, in a combination not covered by the cases above,
 * drives DATA to X rather than guessing which state applies.
 *
 * <p>Ports are {@code ADDRESS, WE, OE, CS} in, {@code DATA} inout.
 *
 * @param addressWidth the bus width of ADDRESS
 * @param dataWidth the bus width of DATA
 */
public record RamBehavior(BitWidth addressWidth, BitWidth dataWidth) implements ComponentBehavior {

    private static final int ADDRESS = 0;
    private static final int WE = 1;
    private static final int OE = 2;
    private static final int CS = 3;
    private static final int DATA_IN = 4;
    private static final int DATA_OUT = 0;

    @Override
    public void evaluate(ComponentContext context) {
        RamState state = (RamState) context.state();
        // Read raw control signals (Z treated as X per gate semantics via asGateInput)
        LogicState cs = LogicOperations.asGateInput(context.readInput(CS).singleBit());
        LogicState we = LogicOperations.asGateInput(context.readInput(WE).singleBit());
        LogicState oe = LogicOperations.asGateInput(context.readInput(OE).singleBit());
        OptionalLong address = context.readInput(ADDRESS).toUnsignedLong();

        // Write: only when all three signals are definitely in write state
        if (cs == LogicState.ONE && we == LogicState.ONE && address.isPresent()) {
            state.write((int) address.getAsLong(), LogicOperations.asGateInput(context.readInput(DATA_IN)));
        }

        // Determine DATA output
        LogicVector output;
        if (cs == LogicState.ZERO) {
            // Definitely disabled — output floats
            output = LogicVector.repeat(LogicState.HIGH_IMPEDANCE, dataWidth);
        } else if (cs == LogicState.ONE && we == LogicState.ONE) {
            // Definitely writing — RAM does not drive the bus during a write
            output = LogicVector.repeat(LogicState.HIGH_IMPEDANCE, dataWidth);
        } else if (cs == LogicState.ONE && we == LogicState.ZERO && oe == LogicState.ONE) {
            // Definitely reading
            output = address.isPresent()
                    ? state.read((int) address.getAsLong())
                    : LogicVector.repeat(LogicState.UNKNOWN, dataWidth);
        } else if (cs == LogicState.ONE && we == LogicState.ZERO && oe == LogicState.ZERO) {
            // Output-enable inactive — RAM doesn't drive
            output = LogicVector.repeat(LogicState.HIGH_IMPEDANCE, dataWidth);
        } else {
            // Ambiguous combination (CS=X, WE=X, OE=X, etc.) — could be driving or not
            output = LogicVector.repeat(LogicState.UNKNOWN, dataWidth);
        }
        context.driveOutput(DATA_OUT, output);
    }

    @Override
    public ComponentRuntimeState createState() {
        return new RamState(addressWidth.bits(), dataWidth);
    }
}
