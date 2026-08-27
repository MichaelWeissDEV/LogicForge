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
        if (reset == LogicState.ONE) {
            state.ram.reset();
            state.output = LogicVector.repeat(LogicState.ZERO, dataWidth);
        } else if (reset == LogicState.UNKNOWN) {
            state.output = LogicVector.repeat(LogicState.UNKNOWN, dataWidth);
        } else if (state.lastClock == LogicState.ZERO && clock == LogicState.ONE) {
            var address = context.readInput(0).toUnsignedLong();
            LogicState write = LogicOperations.asGateInput(context.readInput(2).singleBit());
            if (address.isEmpty() || !write.isDefined()) {
                state.output = LogicVector.repeat(LogicState.UNKNOWN, dataWidth);
            } else if (write == LogicState.ONE) {
                state.ram.write((int) address.getAsLong(),
                        LogicOperations.asGateInput(context.readInput(1)));
            } else {
                state.output = state.ram.read((int) address.getAsLong());
            }
        }
        state.lastClock = clock;
        context.driveOutput(0, state.output);
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
