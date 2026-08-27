package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentDebugSnapshot;
import dev.logicforge.simulation.ComponentRuntimeState;
import java.util.List;
import java.util.Map;

/** Clocked, reusable memory-mapped output register. */
public record OutputPortBehavior(BitWidth width) implements ComponentBehavior {

    private static final int DATA = 0;
    private static final int SELECT = 1;
    private static final int WRITE = 2;
    private static final int CLK = 3;
    private static final int RESET = 4;

    @Override
    public void evaluate(ComponentContext context) {
        State state = (State) context.state();
        LogicState clock = LogicOperations.asGateInput(context.readInput(CLK).singleBit());
        LogicState risingEdge = ClockEdgePolicy.rising(state.lastClock, clock);

        LogicVector normal = state.value;
        if (risingEdge != LogicState.ZERO) {
            LogicState writeSelected = LogicOperations.and(
                    context.readInput(SELECT).singleBit(), context.readInput(WRITE).singleBit());
            LogicVector edgeValue = StatefulControlPolicy.choose(writeSelected, state.value,
                    LogicOperations.asGateInput(context.readInput(DATA)));
            normal = StatefulControlPolicy.choose(risingEdge, state.value, edgeValue);
        }
        LogicVector cleared = LogicVector.repeat(LogicState.ZERO, width);
        state.value = StatefulControlPolicy.choose(
                context.readInput(RESET).singleBit(), normal, cleared);
        state.lastClock = clock;
        context.driveOutput(0, state.value);
    }

    @Override
    public ComponentRuntimeState createState() {
        return new State(width);
    }

    @Override
    public ComponentDebugSnapshot debugSnapshot(ComponentRuntimeState state) {
        return new ComponentDebugSnapshot(Map.of("VALUE", ((State) state).value),
                List.of(), null, Map.of());
    }

    private static final class State implements ComponentRuntimeState {
        private final BitWidth width;
        private LogicVector value;
        private LogicState lastClock;

        private State(BitWidth width) {
            this.width = width;
            reset();
        }

        @Override
        public void reset() {
            value = LogicVector.repeat(LogicState.ZERO, width);
            lastClock = LogicState.UNKNOWN;
        }

        @Override
        public Object snapshot() {
            return new Snapshot(width, value, lastClock);
        }

        @Override
        public void restore(Object snapshot) {
            if (snapshot instanceof Snapshot saved && saved.width().equals(width)) {
                value = saved.value();
                lastClock = saved.lastClock();
            }
        }
    }

    private record Snapshot(BitWidth width, LogicVector value, LogicState lastClock) {
    }
}
