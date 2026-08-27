package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;

/** Dual-port RAM with combinational reads and rising-edge writes on two independent clocks.
 * Simultaneous same-address writes store the common value, or X when the values differ. */
public record DualPortRamBehavior(BitWidth addressWidth, BitWidth dataWidth)
        implements ComponentBehavior {
    @Override
    public void evaluate(ComponentContext context) {
        State state = (State) context.state();
        LogicState reset = LogicOperations.asGateInput(context.readInput(8).singleBit());
        LogicState clockA = LogicOperations.asGateInput(context.readInput(3).singleBit());
        LogicState clockB = LogicOperations.asGateInput(context.readInput(7).singleBit());
        boolean unknownReset = reset == LogicState.UNKNOWN;
        if (reset == LogicState.ONE) {
            state.ram.reset();
        } else if (reset == LogicState.ZERO) {
            boolean edgeA = state.clockA == LogicState.ZERO && clockA == LogicState.ONE;
            boolean edgeB = state.clockB == LogicState.ZERO && clockB == LogicState.ONE;
            var addressA = context.readInput(0).toUnsignedLong();
            var addressB = context.readInput(4).toUnsignedLong();
            LogicState weA = LogicOperations.asGateInput(context.readInput(2).singleBit());
            LogicState weB = LogicOperations.asGateInput(context.readInput(6).singleBit());
            if (edgeA && weA == LogicState.ONE && addressA.isPresent()
                    && edgeB && weB == LogicState.ONE && addressB.isPresent()
                    && addressA.getAsLong() == addressB.getAsLong()) {
                LogicVector a = LogicOperations.asGateInput(context.readInput(1));
                LogicVector b = LogicOperations.asGateInput(context.readInput(5));
                state.ram.write((int) addressA.getAsLong(), a.equals(b) ? a
                        : LogicVector.repeat(LogicState.UNKNOWN, dataWidth));
            } else {
                writePort(state, context, edgeA, weA, addressA, 1);
                writePort(state, context, edgeB, weB, addressB, 5);
            }
        }
        state.clockA = clockA; state.clockB = clockB;
        context.driveOutput(0, unknownReset
                ? LogicVector.repeat(LogicState.UNKNOWN, dataWidth)
                : read(state, context.readInput(0)));
        context.driveOutput(1, unknownReset
                ? LogicVector.repeat(LogicState.UNKNOWN, dataWidth)
                : read(state, context.readInput(4)));
    }
    private void writePort(State state, ComponentContext context, boolean edge, LogicState we,
                           java.util.OptionalLong address, int dataInput) {
        if (edge && we == LogicState.ONE && address.isPresent()) {
            state.ram.write((int) address.getAsLong(),
                    LogicOperations.asGateInput(context.readInput(dataInput)));
        } else if (edge && we == LogicState.UNKNOWN && address.isPresent()) {
            state.ram.write((int) address.getAsLong(), LogicVector.repeat(LogicState.UNKNOWN, dataWidth));
        }
    }
    private LogicVector read(State state, LogicVector address) {
        var numeric = address.toUnsignedLong();
        return numeric.isPresent() ? state.ram.read((int) numeric.getAsLong())
                : LogicVector.repeat(LogicState.UNKNOWN, dataWidth);
    }
    @Override public ComponentRuntimeState createState() { return new State(addressWidth, dataWidth); }
    private static final class State implements ComponentRuntimeState {
        final BitWidth addressWidth; final BitWidth dataWidth; final RamState ram;
        LogicState clockA; LogicState clockB;
        State(BitWidth aw, BitWidth dw) { addressWidth=aw; dataWidth=dw; ram=new RamState(aw.bits(),dw); reset(); }
        @Override public void reset() { ram.reset(); clockA=LogicState.UNKNOWN; clockB=LogicState.UNKNOWN; }
        @Override public Object snapshot() { return new Snapshot(addressWidth,dataWidth,ram.snapshot(),clockA,clockB); }
        @Override public void restore(Object v) { if(v instanceof Snapshot s && s.aw().equals(addressWidth)
                && s.dw().equals(dataWidth)){ram.restore(s.ram());clockA=s.a();clockB=s.b();} }
        @Override public dev.logicforge.simulation.MemorySnapshot memorySnapshot(){return ram.memorySnapshot();}
        @Override public dev.logicforge.simulation.MemoryInfo memoryInfo(){return ram.memoryInfo();}
        @Override public dev.logicforge.simulation.MemoryPageSnapshot memoryPage(int s,int c){return ram.memoryPage(s,c);}
        @Override public long memoryRevision(){return ram.memoryRevision();}
        @Override public void writeMemoryWord(int a, LogicVector v){ram.write(a,v);}
    }
    private record Snapshot(BitWidth aw, BitWidth dw, Object ram, LogicState a, LogicState b){}
}
