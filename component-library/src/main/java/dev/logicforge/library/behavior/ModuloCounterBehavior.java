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
 * A counter that wraps at an arbitrary modulus rather than at its full bit range: COUNT
 * cycles through {@code 0 .. modulus - 1} and back to 0, on every active clock edge while
 * ENABLE is 1.
 *
 * <p>Ports are {@code CLK, ENABLE, RESET} in, {@code COUNT, TC} out. {@code TC} (terminal
 * count) is 1 when COUNT equals {@code modulus - 1}, the value about to wrap.
 *
 * @param width the bus width of COUNT; must be wide enough to hold {@code modulus - 1}
 * @param risingEdge {@code true} to trigger on 0-&gt;1, {@code false} to trigger on 1-&gt;0
 * @param modulus the wrap point, at least 2
 */
public record ModuloCounterBehavior(BitWidth width, boolean risingEdge, int modulus) implements ComponentBehavior {

    private static final int CLK = 0;
    private static final int ENABLE = 1;
    private static final int RESET = 2;

    public ModuloCounterBehavior {
        if (modulus < 2) {
            throw new IllegalArgumentException("modulus must be at least 2");
        }
        if (width.bits() < 63 && modulus > (1L << width.bits())) {
            throw new IllegalArgumentException(
                    "modulus " + modulus + " does not fit in a " + width.bits() + "-bit counter");
        }
    }

    @Override
    public void evaluate(ComponentContext context) {
        VectorRegisterState state = (VectorRegisterState) context.state();
        LogicState clock = LogicOperations.asGateInput(context.readInput(CLK).singleBit());
        boolean edge = risingEdge
                ? state.lastClock == ZERO && clock == ONE
                : state.lastClock == ONE && clock == ZERO;
        LogicState reset = LogicOperations.asGateInput(context.readInput(RESET).singleBit());

        if (reset == ONE) {
            state.value = LogicVector.repeat(ZERO, width);
        } else if (edge) {
            LogicState enable = LogicOperations.asGateInput(context.readInput(ENABLE).singleBit());
            if (enable == ONE) {
                state.value = incremented(state.value);
            } else if (enable != ZERO) {
                state.value = LogicVector.repeat(UNKNOWN, width);
            }
        }
        state.lastClock = clock;

        context.driveOutput(0, state.value);
        context.driveOutput(1, LogicVector.single(terminalCount(state.value)));
    }

    private LogicVector incremented(LogicVector current) {
        OptionalLong defined = current.toUnsignedLong();
        if (defined.isEmpty()) {
            return LogicVector.repeat(UNKNOWN, width);
        }
        long next = defined.getAsLong() + 1;
        if (next >= modulus) {
            next = 0;
        }
        return LogicVector.fromUnsignedLong(next, width.bits());
    }

    private LogicState terminalCount(LogicVector count) {
        OptionalLong defined = count.toUnsignedLong();
        if (defined.isEmpty()) {
            return UNKNOWN;
        }
        return LogicState.of(defined.getAsLong() == modulus - 1);
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
