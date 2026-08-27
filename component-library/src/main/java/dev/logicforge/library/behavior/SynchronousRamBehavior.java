package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;

/** Single-port synchronous RAM: read or write occurs on each rising clock edge. */
public record SynchronousRamBehavior(BitWidth addressWidth, BitWidth dataWidth)
        implements ComponentBehavior {
    @Override
    public void evaluate(ComponentContext context) {
        State state = (State) context.state();
        LogicState reset = LogicOperations.asGateInput(context.readInput(4).singleBit());
        LogicState clock = LogicOperations.asGateInput(context.readInput(3).singleBit());
        LogicVector normalOutput = state.output;
        LogicState edge = ClockEdgePolicy.rising(state.lastClock, clock);
        if (reset != LogicState.ONE && edge != LogicState.ZERO) {
            AddressPossibilities addresses = AddressPossibilities.resolve(
                    context.readInput(0), state.ram.wordCount());
            LogicState write = LogicOperations.asGateInput(context.readInput(2).singleBit());
            LogicVector data = LogicOperations.asGateInput(context.readInput(1));
            LogicVector readCandidate = state.ram.readPossible(addresses);
            if (write == LogicState.ZERO) {
                normalOutput = StatefulControlPolicy.choose(edge, state.output, readCandidate);
            } else if (write == LogicState.ONE) {
                applyWrite(state.ram, addresses, data, edge == LogicState.ONE);
            } else {
                normalOutput = StatefulControlPolicy.merge(state.output, readCandidate);
                applyWrite(state.ram, addresses, data, false);
            }
        }
        if (reset == LogicState.ONE) {
            state.ram.reset();
            state.output = LogicVector.repeat(LogicState.ZERO, dataWidth);
        } else if (reset == LogicState.UNKNOWN) {
            state.ram.mergeResetState();
            state.output = StatefulControlPolicy.merge(normalOutput,
                    LogicVector.repeat(LogicState.ZERO, dataWidth));
        } else {
            state.output = normalOutput;
        }
        state.lastClock = clock;
        context.driveOutput(0, state.output);
    }

    private static void applyWrite(RamState ram, AddressPossibilities addresses,
                                   LogicVector data, boolean writeCertain) {
        if (writeCertain && addresses.isSingle()) {
            ram.write(addresses.singleAddress(), data);
        } else {
            addresses.forEach(address -> ram.mergeWord(address, data));
        }
    }
    @Override public ComponentRuntimeState createState() { return new State(addressWidth, dataWidth); }

    private static final class State implements ComponentRuntimeState {
        final BitWidth addressWidth; final BitWidth dataWidth; final RamState ram;
        LogicState lastClock; LogicVector output;
        State(BitWidth addressWidth, BitWidth dataWidth) {
            this.addressWidth = addressWidth; this.dataWidth = dataWidth;
            this.ram = new RamState(addressWidth.bits(), dataWidth); reset();
        }
        @Override public void reset() { ram.reset(); lastClock = LogicState.UNKNOWN;
            output = LogicVector.repeat(LogicState.ZERO, dataWidth); }
        @Override public Object snapshot() { return new Snapshot(addressWidth, dataWidth,
                ram.snapshot(), lastClock, output); }
        @Override public void restore(Object value) { if (value instanceof Snapshot saved
                && saved.addressWidth().equals(addressWidth) && saved.dataWidth().equals(dataWidth)) {
            ram.restore(saved.ram()); lastClock = saved.clock(); output = saved.output(); } }
        @Override public dev.logicforge.simulation.MemorySnapshot memorySnapshot() { return ram.memorySnapshot(); }
        @Override public dev.logicforge.simulation.MemoryInfo memoryInfo() { return ram.memoryInfo(); }
        @Override public dev.logicforge.simulation.MemoryPageSnapshot memoryPage(int start, int count) {
            return ram.memoryPage(start, count); }
        @Override public long memoryRevision() { return ram.memoryRevision(); }
        @Override public void writeMemoryWord(int address, LogicVector value) { ram.write(address, value); }
    }
    private record Snapshot(BitWidth addressWidth, BitWidth dataWidth, Object ram,
                            LogicState clock, LogicVector output) {}
}
