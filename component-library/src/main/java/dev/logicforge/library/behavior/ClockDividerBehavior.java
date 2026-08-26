package dev.logicforge.library.behavior;

import static dev.logicforge.logic.LogicState.ONE;
import static dev.logicforge.logic.LogicState.UNKNOWN;
import static dev.logicforge.logic.LogicState.ZERO;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;

/**
 * Divides CLK down by counting its own active edges in virtual simulation time — no wall
 * clock or {@code Thread.sleep} involved. {@code divideBy=N} means one complete CLK_OUT
 * period spans N active CLK edges, so its average frequency is {@code input / N}. Even
 * divisors have equal half-periods. Odd divisors hold low for one edge longer than high.
 *
 * <p>Ports are {@code CLK, RESET, ENABLE} in, {@code CLK_OUT} out.
 *
 * @param risingEdge {@code true} to count CLK's 0-&gt;1 edges, {@code false} for 1-&gt;0
 * @param divideBy output frequency divisor, at least 2
 */
public record ClockDividerBehavior(boolean risingEdge, int divideBy) implements ComponentBehavior {

    private static final int CLK = 0;
    private static final int RESET = 1;
    private static final int ENABLE = 2;

    public ClockDividerBehavior {
        if (divideBy < 2) {
            throw new IllegalArgumentException("divideBy must be at least 2");
        }
    }

    @Override
    public void evaluate(ComponentContext context) {
        ClockDividerState state = (ClockDividerState) context.state();
        LogicState clock = LogicOperations.asGateInput(context.readInput(CLK).singleBit());
        boolean edge = risingEdge
                ? state.lastClock == ZERO && clock == ONE
                : state.lastClock == ONE && clock == ZERO;
        LogicState reset = LogicOperations.asGateInput(context.readInput(RESET).singleBit());

        Transition normal = new Transition(state.edgeCount, state.edgeCountUnknown, state.output);
        if (edge) {
            LogicState enable = LogicOperations.asGateInput(context.readInput(ENABLE).singleBit());
            if (enable == ONE) {
                normal = advanced(normal);
            } else if (enable != ZERO) {
                Transition active = advanced(normal);
                normal = new Transition(normal.edgeCount(),
                        normal.edgeCountUnknown() || active.edgeCountUnknown()
                                || normal.edgeCount() != active.edgeCount(),
                        StatefulControlPolicy.choose(enable,
                                LogicVector.single(normal.output()),
                                LogicVector.single(active.output())).singleBit());
            }
        }
        if (reset == ONE) {
            state.edgeCount = 0;
            state.edgeCountUnknown = false;
            state.output = ZERO;
        } else if (reset == ZERO) {
            apply(state, normal);
        } else {
            state.edgeCount = 0;
            state.edgeCountUnknown = normal.edgeCountUnknown() || normal.edgeCount() != 0;
            state.output = StatefulControlPolicy.choose(reset,
                    LogicVector.single(normal.output()), LogicVector.ZERO).singleBit();
        }
        state.lastClock = clock;

        context.driveOutput(0, LogicVector.single(state.output));
    }

    @Override
    public ComponentRuntimeState createState() {
        return new ClockDividerState();
    }

    @Override
    public dev.logicforge.simulation.ComponentDebugSnapshot debugSnapshot(ComponentRuntimeState state) {
        ClockDividerState s = (ClockDividerState) state;
        return new dev.logicforge.simulation.ComponentDebugSnapshot(
                java.util.Map.of("Output", LogicVector.single(s.output)),
                java.util.List.of(), null, java.util.Map.of(
                        "Edge count", s.edgeCount,
                        "Edge count uncertain", s.edgeCountUnknown ? 1L : 0L));
    }

    private Transition advanced(Transition current) {
        if (current.edgeCountUnknown() || !current.output().isDefined()) {
            return new Transition(current.edgeCount(), true, UNKNOWN);
        }
        long count = current.edgeCount() + 1;
        int halfPeriod = current.output() == ZERO ? (divideBy + 1) / 2 : divideBy / 2;
        if (count >= halfPeriod) {
            return new Transition(0, false, current.output() == ONE ? ZERO : ONE);
        }
        return new Transition(count, false, current.output());
    }

    private static void apply(ClockDividerState state, Transition transition) {
        state.edgeCount = transition.edgeCount();
        state.edgeCountUnknown = transition.edgeCountUnknown();
        state.output = transition.output();
    }

    private record Transition(long edgeCount, boolean edgeCountUnknown, LogicState output) {
    }
}
