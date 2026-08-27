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
        if (reset == LogicState.ONE) {
            state.ram.reset();
        } else {
            LogicState edgeA = ClockEdgePolicy.rising(state.clockA, clockA);
            LogicState edgeB = ClockEdgePolicy.rising(state.clockB, clockB);
            if (edgeA != LogicState.ZERO || edgeB != LogicState.ZERO) {
                PortWrite a = new PortWrite(edgeA,
                        LogicOperations.asGateInput(context.readInput(2).singleBit()),
                        AddressPossibilities.resolve(context.readInput(0), state.ram.wordCount()),
                        LogicOperations.asGateInput(context.readInput(1)));
                PortWrite b = new PortWrite(edgeB,
                        LogicOperations.asGateInput(context.readInput(6).singleBit()),
                        AddressPossibilities.resolve(context.readInput(4), state.ram.wordCount()),
                        LogicOperations.asGateInput(context.readInput(5)));
                applyWrites(state.ram, a, b);
            }
            if (reset == LogicState.UNKNOWN) {
                state.ram.mergeResetState();
            }
        }
        state.clockA = clockA; state.clockB = clockB;
        context.driveOutput(0, read(state, context.readInput(0)));
        context.driveOutput(1, read(state, context.readInput(4)));
    }

    private void applyWrites(RamState ram, PortWrite a, PortWrite b) {
        for (int address = 0; address < ram.wordCount(); address++) {
            boolean aMay = a.mayWrite(address);
            boolean bMay = b.mayWrite(address);
            if (!aMay && !bMay) {
                continue;
            }
            boolean aMust = a.mustWrite(address);
            boolean bMust = b.mustWrite(address);
            LogicVector next = null;
            if (!aMust && !bMust) {
                next = ram.wordAt(address);
            }
            if (aMay && !bMust) {
                next = mergeCandidate(next, a.data());
            }
            if (bMay && !aMust) {
                next = mergeCandidate(next, b.data());
            }
            if (aMay && bMay) {
                LogicVector collision = a.data().equals(b.data()) ? a.data()
                        : LogicVector.repeat(LogicState.UNKNOWN, dataWidth);
                next = mergeCandidate(next, collision);
            }
            ram.write(address, next);
        }
    }

    private static LogicVector mergeCandidate(LogicVector current, LogicVector candidate) {
        return current == null ? candidate : StatefulControlPolicy.merge(current, candidate);
    }

    private LogicVector read(State state, LogicVector address) {
        return state.ram.readPossible(AddressPossibilities.resolve(address, state.ram.wordCount()));
    }

    private record PortWrite(LogicState edge, LogicState writeEnable,
                             AddressPossibilities addresses, LogicVector data) {
        boolean mayWrite(int address) {
            return edge != LogicState.ZERO && writeEnable != LogicState.ZERO
                    && addresses.contains(address);
        }

        boolean mustWrite(int address) {
            return edge == LogicState.ONE && writeEnable == LogicState.ONE
                    && addresses.isCertain(address);
        }
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
