package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;

/** Generic asynchronous SRAM engine for active-low DIP controls and optional CS2. */
public record PackagedSramBehavior(BitWidth addressWidth, BitWidth dataWidth,
                                   boolean dualChipSelect) implements ComponentBehavior {
    private static final int ADDRESS = 0;
    private static final int WE_N = 1;
    private static final int OE_N = 2;
    private static final int CS_N = 3;
    private static final int CS2 = 4;
    private static final int DATA_IN = 5;

    @Override
    public void evaluate(ComponentContext context) {
        RamState state = (RamState) context.state();
        LogicState csN = gate(context, CS_N);
        LogicState cs2 = dualChipSelect ? gate(context, CS2) : LogicState.ONE;
        LogicState weN = gate(context, WE_N);
        LogicState oeN = gate(context, OE_N);
        boolean selected = csN == LogicState.ZERO && cs2 == LogicState.ONE;
        boolean definitelyOff = csN == LogicState.ONE || cs2 == LogicState.ZERO;
        AddressPossibilities addresses = AddressPossibilities.resolve(
                context.readInput(ADDRESS), state.wordCount());
        if (selected && weN == LogicState.ZERO) {
            LogicVector data = LogicOperations.asGateInput(context.readInput(DATA_IN));
            if (addresses.isSingle()) {
                state.write(addresses.singleAddress(), data);
            } else {
                addresses.forEach(address -> state.mergeWord(address, data));
            }
        }
        LogicVector z = LogicVector.repeat(LogicState.HIGH_IMPEDANCE, dataWidth);
        LogicVector output;
        if (definitelyOff || selected && weN == LogicState.ZERO || selected && oeN == LogicState.ONE) {
            output = z;
        } else if (selected && weN == LogicState.ONE && oeN == LogicState.ZERO) {
            output = state.readPossible(addresses);
        } else {
            output = StatefulControlPolicy.merge(z, state.readPossible(addresses));
        }
        context.driveOutput(0, output);
    }

    private static LogicState gate(ComponentContext context, int input) {
        return LogicOperations.asGateInput(context.readInput(input).singleBit());
    }

    @Override
    public ComponentRuntimeState createState() {
        return new RamState(addressWidth.bits(), dataWidth);
    }
}
