package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentDebugSnapshot;
import dev.logicforge.simulation.ComponentRuntimeState;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Clocked byte sink retaining a bounded character buffer for educational/MMIO output. */
public record CharacterOutputBehavior(int capacity) implements ComponentBehavior {
    @Override
    public void evaluate(ComponentContext context) {
        State state = (State) context.state();
        LogicState clock = LogicOperations.asGateInput(context.readInput(3).singleBit());
        LogicState reset = LogicOperations.asGateInput(context.readInput(4).singleBit());
        if (reset == LogicState.ONE) {
            state.text.setLength(0);
            state.unknown = false;
        } else if (reset == LogicState.UNKNOWN) {
            // The buffer is a String rather than a four-state vector. Retaining every
            // possible string after an ambiguous reset/write would grow exponentially, so
            // this component deliberately uses one persistent coarse uncertainty marker.
            state.unknown = true;
        } else if (ClockEdgePolicy.rising(state.lastClock, clock) != LogicState.ZERO) {
            LogicState edge = ClockEdgePolicy.rising(state.lastClock, clock);
            LogicState write = LogicOperations.and(context.readInput(1).singleBit(),
                    context.readInput(2).singleBit());
            if (edge == LogicState.UNKNOWN && write != LogicState.ZERO) {
                state.unknown = true;
            } else if (write == LogicState.ONE) {
                var value = context.readInput(0).toUnsignedLong();
                if (value.isPresent()) {
                    if (state.text.length() == capacity) {
                        state.text.deleteCharAt(0);
                    }
                    state.text.append(new String(new byte[]{(byte) value.getAsLong()},
                            StandardCharsets.ISO_8859_1));
                } else {
                    state.unknown = true;
                }
            } else if (write == LogicState.UNKNOWN) {
                state.unknown = true;
            }
        }
        state.lastClock = clock;
    }

    @Override public ComponentRuntimeState createState() { return new State(capacity); }

    @Override
    public ComponentDebugSnapshot debugSnapshot(ComponentRuntimeState runtime) {
        State state = (State) runtime;
        Map<String, LogicVector> values = Map.of(
                "LENGTH", LogicVector.fromUnsignedLong(state.text.length(), 16),
                "UNKNOWN_WRITE", LogicVector.single(LogicState.of(state.unknown)));
        Map<String, Long> counters = Map.of("characters", (long) state.text.length());
        return new ComponentDebugSnapshot(values, List.of(), null, counters,
                Map.of("TEXT", state.unknown ? "<unknown>" : state.text.toString()));
    }

    public static String text(ComponentRuntimeState runtime) { return ((State) runtime).text.toString(); }

    private static final class State implements ComponentRuntimeState {
        private final int capacity;
        private final StringBuilder text = new StringBuilder();
        private LogicState lastClock;
        private boolean unknown;
        State(int capacity) { this.capacity = capacity; reset(); }
        @Override public void reset() { text.setLength(0); lastClock = LogicState.UNKNOWN; unknown = false; }
        @Override public Object snapshot() { return new Snapshot(text.toString(), lastClock, unknown); }
        @Override public void restore(Object value) {
            if (value instanceof Snapshot snapshot) {
                text.setLength(0);
                text.append(snapshot.text(), Math.max(0, snapshot.text().length() - capacity),
                        snapshot.text().length());
                lastClock = snapshot.clock();
                unknown = snapshot.unknown();
            }
        }
    }
    private record Snapshot(String text, LogicState clock, boolean unknown) {}
}
