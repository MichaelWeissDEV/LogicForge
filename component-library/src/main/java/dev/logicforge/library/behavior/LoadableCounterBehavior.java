package dev.logicforge.library.behavior;

import static dev.logicforge.logic.LogicState.ONE;
import static dev.logicforge.logic.LogicState.UNKNOWN;
import static dev.logicforge.logic.LogicState.ZERO;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;
import java.util.OptionalLong;

/**
 * A counter that can also be parallel-loaded: on the active clock edge, RESET wins over
 * LOAD, which wins over ENABLE-driven counting, which wins over holding. This is the shape
 * a CPU program counter needs — LOAD lets a jump or branch set an arbitrary next address,
 * while plain ENABLE advances it by one for sequential fetch.
 *
 * <p>Ports are {@code DATA, CLK, ENABLE, LOAD, RESET} in, {@code COUNT, TC} out. {@code TC}
 * (terminal count) is 1 when COUNT is at its maximum value.
 *
 * @param width the bus width of DATA/COUNT
 * @param risingEdge {@code true} to trigger on 0-&gt;1, {@code false} to trigger on 1-&gt;0
 */
public record LoadableCounterBehavior(BitWidth width, boolean risingEdge) implements ComponentBehavior {

    private static final int DATA = 0;
    private static final int CLK = 1;
    private static final int ENABLE = 2;
    private static final int LOAD = 3;
    private static final int RESET = 4;

    @Override
    public void evaluate(ComponentContext context) {
        VectorRegisterState state = (VectorRegisterState) context.state();
        LogicState clock = LogicOperations.asGateInput(context.readInput(CLK).singleBit());
        boolean edge = risingEdge
                ? state.lastClock == ZERO && clock == ONE
                : state.lastClock == ONE && clock == ZERO;
        LogicState reset = LogicOperations.asGateInput(context.readInput(RESET).singleBit());

        LogicVector normal = state.value;
        if (edge) {
            LogicState load = LogicOperations.asGateInput(context.readInput(LOAD).singleBit());
            LogicState enable = LogicOperations.asGateInput(context.readInput(ENABLE).singleBit());
            LogicVector counted = StatefulControlPolicy.choose(enable, state.value,
                    incremented(state.value));
            normal = StatefulControlPolicy.choose(load, counted,
                    LogicOperations.asGateInput(context.readInput(DATA)));
        }
        state.value = StatefulControlPolicy.choose(reset, normal,
                LogicVector.repeat(ZERO, width));
        state.lastClock = clock;

        context.driveOutput(0, state.value);
        context.driveOutput(1, LogicVector.single(terminalCount(state.value)));
    }

    private LogicVector incremented(LogicVector current) {
        OptionalLong defined = current.toUnsignedLong();
        if (defined.isEmpty()) {
            return LogicVector.repeat(UNKNOWN, width);
        }
        long mask = width.bits() == 64 ? -1L : (1L << width.bits()) - 1;
        return LogicVector.fromUnsignedLong((defined.getAsLong() + 1) & mask, width.bits());
    }

    private LogicState terminalCount(LogicVector count) {
        OptionalLong defined = count.toUnsignedLong();
        if (defined.isEmpty()) {
            return UNKNOWN;
        }
        long mask = width.bits() == 64 ? -1L : (1L << width.bits()) - 1;
        return LogicState.of(defined.getAsLong() == mask);
    }

    @Override
    public ComponentRuntimeState createState() {
        return new VectorRegisterState(width);
    }

    @Override
    public dev.logicforge.simulation.ComponentDebugSnapshot debugSnapshot(ComponentRuntimeState state) {
        return new dev.logicforge.simulation.ComponentDebugSnapshot(
                java.util.Map.of("Count", ((VectorRegisterState) state).value),
                java.util.List.of(), null, java.util.Map.of());
    }
}
