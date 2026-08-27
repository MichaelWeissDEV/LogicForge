package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentDebugSnapshot;
import dev.logicforge.simulation.ComponentRuntimeState;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Bounded clocked FIFO or LIFO stack with resettable, snapshot-safe storage. */
public record QueueStorageBehavior(Kind kind, BitWidth width, int capacity)
        implements ComponentBehavior {
    public enum Kind { FIFO, STACK }

    @Override
    public void evaluate(ComponentContext context) {
        State state = (State) context.state();
        LogicState clock = LogicOperations.asGateInput(context.readInput(3).singleBit());
        LogicState reset = LogicOperations.asGateInput(context.readInput(4).singleBit());
        if (reset == LogicState.ONE) {
            state.reset();
        } else if (reset == LogicState.UNKNOWN) {
            state.unknown = true;
        } else if (state.lastClock == LogicState.ZERO && clock == LogicState.ONE) {
            LogicState put = LogicOperations.asGateInput(context.readInput(1).singleBit());
            LogicState take = LogicOperations.asGateInput(context.readInput(2).singleBit());
            if (!put.isDefined() || !take.isDefined()) {
                state.unknown = true;
            } else if (kind == Kind.FIFO) {
                fifoEdge(state, put == LogicState.ONE, take == LogicState.ONE,
                        LogicOperations.asGateInput(context.readInput(0)));
            } else {
                stackEdge(state, put == LogicState.ONE, take == LogicState.ONE,
                        LogicOperations.asGateInput(context.readInput(0)));
            }
        }
        state.lastClock = clock;
        LogicVector data = state.unknown ? LogicVector.repeat(LogicState.UNKNOWN, width)
                : state.peek(kind);
        context.driveOutput(0, data);
        context.driveOutput(1, LogicVector.single(state.unknown ? LogicState.UNKNOWN
                : LogicState.of(state.size == 0)));
        context.driveOutput(2, LogicVector.single(state.unknown ? LogicState.UNKNOWN
                : LogicState.of(state.size == capacity)));
        int countWidth = Math.max(1, 32 - Integer.numberOfLeadingZeros(capacity));
        context.driveOutput(3, state.unknown ? LogicVector.repeat(LogicState.UNKNOWN, countWidth)
                : LogicVector.fromUnsignedLong(state.size, countWidth));
    }

    private void fifoEdge(State state, boolean write, boolean read, LogicVector data) {
        if (read && state.size > 0) {
            state.head = (state.head + 1) % capacity;
            state.size--;
        }
        if (write && state.size < capacity) {
            int tail = (state.head + state.size) % capacity;
            state.values[tail] = data;
            state.size++;
        }
    }

    private void stackEdge(State state, boolean push, boolean pop, LogicVector data) {
        if (pop && state.size > 0) {
            state.size--;
        }
        if (push && state.size < capacity) {
            state.values[state.size++] = data;
        }
    }

    @Override public ComponentRuntimeState createState() { return new State(width, capacity); }
    @Override public ComponentDebugSnapshot debugSnapshot(ComponentRuntimeState runtime) {
        State state = (State) runtime;
        return new ComponentDebugSnapshot(Map.of(
                "SIZE", LogicVector.fromUnsignedLong(state.size,
                        Math.max(1, 32 - Integer.numberOfLeadingZeros(capacity))),
                "UNKNOWN", LogicVector.single(LogicState.of(state.unknown))),
                state.logicalValues(kind), null, Map.of("capacity", (long) capacity));
    }

    private static final class State implements ComponentRuntimeState {
        final BitWidth width; final int capacity; LogicVector[] values;
        int head; int size; LogicState lastClock; boolean unknown;
        State(BitWidth width,int capacity){this.width=width;this.capacity=capacity;reset();}
        @Override public void reset(){values=new LogicVector[capacity];
            Arrays.fill(values,LogicVector.repeat(LogicState.ZERO,width));head=0;size=0;
            lastClock=LogicState.UNKNOWN;unknown=false;}
        LogicVector peek(Kind kind){if(size==0)return LogicVector.repeat(LogicState.ZERO,width);
            return kind==Kind.FIFO?values[head]:values[size-1];}
        List<LogicVector> logicalValues(Kind kind){
            java.util.ArrayList<LogicVector> result=new java.util.ArrayList<>(size);
            for(int i=0;i<size;i++)result.add(kind==Kind.FIFO?values[(head+i)%capacity]:values[i]);
            return List.copyOf(result);}
        @Override public Object snapshot(){return new Snapshot(width,capacity,values.clone(),head,size,lastClock,unknown);}
        @Override public void restore(Object v){if(v instanceof Snapshot s&&s.width().equals(width)
                &&s.capacity()==capacity){values=s.values().clone();head=s.head();size=s.size();
            lastClock=s.clock();unknown=s.unknown();}}
    }
    private record Snapshot(BitWidth width,int capacity,LogicVector[] values,int head,int size,
                            LogicState clock,boolean unknown){}
}
