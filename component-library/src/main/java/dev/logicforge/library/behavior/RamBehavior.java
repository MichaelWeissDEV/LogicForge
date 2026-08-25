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
        LogicState cs = LogicOperations.asGateInput(context.readInput(CS).singleBit());
        LogicState we = LogicOperations.asGateInput(context.readInput(WE).singleBit());
        LogicState oe = LogicOperations.asGateInput(context.readInput(OE).singleBit());
        OptionalLong address = context.readInput(ADDRESS).toUnsignedLong();

        if (cs == LogicState.ONE && we == LogicState.ONE && address.isPresent()) {
            state.write((int) address.getAsLong(), LogicOperations.asGateInput(context.readInput(DATA_IN)));
        }

        boolean reading = cs == LogicState.ONE && we == LogicState.ZERO && oe == LogicState.ONE;
        boolean definitelyNotDriving = cs != LogicState.ONE || we == LogicState.ONE || oe == LogicState.ZERO;
        if (reading && address.isPresent()) {
            context.driveOutput(DATA_OUT, state.read((int) address.getAsLong()));
        } else if (definitelyNotDriving) {
            context.driveOutput(DATA_OUT, LogicVector.repeat(LogicState.HIGH_IMPEDANCE, dataWidth));
        } else {
            context.driveOutput(DATA_OUT, LogicVector.repeat(LogicState.UNKNOWN, dataWidth));
        }
    }

    @Override
    public ComponentRuntimeState createState() {
        return new RamState(addressWidth.bits(), dataWidth);
    }
}
