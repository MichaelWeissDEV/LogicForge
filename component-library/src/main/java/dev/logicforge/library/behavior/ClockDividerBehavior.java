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
 * clock or {@code Thread.sleep} involved. CLK_OUT toggles every {@code divisor} active
 * edges of CLK, so its full period is {@code 2 * divisor} input edges (its frequency is
 * {@code input / (2 * divisor)}).
 *
 * <p>Ports are {@code CLK, RESET, ENABLE} in, {@code CLK_OUT} out.
 *
 * @param risingEdge {@code true} to count CLK's 0-&gt;1 edges, {@code false} for 1-&gt;0
 * @param divisor how many active CLK edges each CLK_OUT half-period takes, at least 2
 */
public record ClockDividerBehavior(boolean risingEdge, int divisor) implements ComponentBehavior {

    private static final int CLK = 0;
    private static final int RESET = 1;
    private static final int ENABLE = 2;

    public ClockDividerBehavior {
        if (divisor < 2) {
            throw new IllegalArgumentException("divisor must be at least 2");
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

        if (reset == ONE) {
            state.edgeCount = 0;
            state.output = ZERO;
        } else if (edge) {
            LogicState enable = LogicOperations.asGateInput(context.readInput(ENABLE).singleBit());
            if (enable == ONE) {
                state.edgeCount++;
                if (state.edgeCount >= divisor) {
                    state.edgeCount = 0;
                    state.output = state.output == ONE ? ZERO : ONE;
                }
            } else if (enable != ZERO) {
                state.output = UNKNOWN;
            }
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
                java.util.List.of(), null, java.util.Map.of("Edge count", s.edgeCount));
    }
}
