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
import java.util.OptionalLong;

/** A reusable 16-bit reload timer with a four-register, 8-bit bus interface. */
public final class TimerBehavior implements ComponentBehavior {

    public static final int CONTROL_ENABLE = 1;
    public static final int CONTROL_PERIODIC = 1 << 1;
    public static final int CONTROL_IRQ_ENABLE = 1 << 2;
    public static final int STATUS_IRQ_PENDING = 1;

    private static final BitWidth BYTE = BitWidth.of(8);
    private static final BitWidth WORD = BitWidth.of(16);
    private static final int REGISTER_SELECT = 0;
    private static final int SELECT = 1;
    private static final int READ = 2;
    private static final int WRITE = 3;
    private static final int CLK = 4;
    private static final int RESET = 5;
    private static final int DATA_IN = 6;

    @Override
    public void evaluate(ComponentContext context) {
        State state = (State) context.state();
        LogicState reset = LogicOperations.asGateInput(context.readInput(RESET).singleBit());
        if (reset == LogicState.ONE) {
            state.reset();
        }

        LogicState clock = LogicOperations.asGateInput(context.readInput(CLK).singleBit());
        boolean risingEdge = state.lastClock == LogicState.ZERO && clock == LogicState.ONE;
        if (reset != LogicState.ONE && risingEdge) {
            tick(state);
            LogicState selectedWrite = LogicOperations.and(
                    context.readInput(SELECT).singleBit(), context.readInput(WRITE).singleBit());
            if (selectedWrite == LogicState.ONE) {
                write(state, context.readInput(REGISTER_SELECT),
                        LogicOperations.asGateInput(context.readInput(DATA_IN)));
            } else if (selectedWrite == LogicState.UNKNOWN) {
                state.makeUnknown();
            }
        }
        state.lastClock = clock;

        LogicState selectedRead = LogicOperations.and(
                context.readInput(SELECT).singleBit(), context.readInput(READ).singleBit());
        LogicVector floating = LogicVector.repeat(LogicState.HIGH_IMPEDANCE, BYTE);
        LogicVector data = switch (selectedRead) {
            case ZERO -> floating;
            case ONE -> read(state, context.readInput(REGISTER_SELECT));
            case UNKNOWN, HIGH_IMPEDANCE -> LogicVector.repeat(LogicState.UNKNOWN, BYTE);
        };
        context.driveOutput(0, data);
        context.driveOutput(1, LogicVector.single(LogicOperations.and(
                state.pending, state.control.getBit(2))));
    }

    private void tick(State state) {
        LogicState enabled = LogicOperations.asGateInput(state.control.getBit(0));
        if (enabled == LogicState.ZERO) {
            return;
        }
        OptionalLong count = state.counter.toUnsignedLong();
        if (enabled != LogicState.ONE || count.isEmpty()) {
            state.counter = LogicVector.repeat(LogicState.UNKNOWN, WORD);
            state.pending = StatefulControlPolicy.choose(enabled,
                    LogicVector.single(state.pending), LogicVector.ONE).singleBit();
            return;
        }
        if (count.getAsLong() > 1) {
            state.counter = LogicVector.fromUnsignedLong(count.getAsLong() - 1, 16);
            return;
        }

        state.pending = LogicState.ONE;
        LogicState periodic = LogicOperations.asGateInput(state.control.getBit(1));
        if (periodic == LogicState.ONE) {
            state.counter = state.reload;
        } else if (periodic == LogicState.ZERO) {
            state.counter = LogicVector.repeat(LogicState.ZERO, WORD);
            state.control = state.control.withBit(0, LogicState.ZERO);
        } else {
            state.counter = StatefulControlPolicy.merge(
                    LogicVector.repeat(LogicState.ZERO, WORD), state.reload);
            state.control = state.control.withBit(0, LogicState.UNKNOWN);
        }
    }

    private void write(State state, LogicVector registerSelect, LogicVector data) {
        OptionalLong register = LogicOperations.asGateInput(registerSelect).toUnsignedLong();
        if (register.isEmpty()) {
            state.makeUnknown();
            return;
        }
        switch ((int) register.getAsLong()) {
            case 0 -> state.reload = replaceByte(state.reload, data, false);
            case 1 -> state.reload = replaceByte(state.reload, data, true);
            case 2 -> {
                LogicState oldEnable = LogicOperations.asGateInput(state.control.getBit(0));
                LogicState newEnable = LogicOperations.asGateInput(data.getBit(0));
                state.control = data;
                if (oldEnable == LogicState.ZERO && newEnable == LogicState.ONE) {
                    state.counter = state.reload;
                } else if (oldEnable != newEnable
                        && (oldEnable == LogicState.UNKNOWN || newEnable == LogicState.UNKNOWN)) {
                    state.counter = StatefulControlPolicy.merge(state.counter, state.reload);
                }
            }
            case 3 -> state.pending = StatefulControlPolicy.choose(data.getBit(0),
                    LogicVector.single(state.pending), LogicVector.ZERO).singleBit();
            default -> state.makeUnknown();
        }
    }

    private LogicVector read(State state, LogicVector registerSelect) {
        OptionalLong register = LogicOperations.asGateInput(registerSelect).toUnsignedLong();
        if (register.isEmpty()) {
            return LogicVector.repeat(LogicState.UNKNOWN, BYTE);
        }
        return switch ((int) register.getAsLong()) {
            case 0 -> byteOf(state.reload, false);
            case 1 -> byteOf(state.reload, true);
            case 2 -> state.control;
            case 3 -> LogicVector.fromUnsignedLong(state.pending == LogicState.ONE ? 1 : 0, 8)
                    .withBit(0, state.pending);
            default -> LogicVector.repeat(LogicState.UNKNOWN, BYTE);
        };
    }

    private LogicVector replaceByte(LogicVector word, LogicVector data, boolean high) {
        LogicVector result = word;
        int offset = high ? 8 : 0;
        for (int bit = 0; bit < 8; bit++) {
            result = result.withBit(offset + bit, data.getBit(bit));
        }
        return result;
    }

    private LogicVector byteOf(LogicVector word, boolean high) {
        LogicVector result = LogicVector.repeat(LogicState.ZERO, BYTE);
        int offset = high ? 8 : 0;
        for (int bit = 0; bit < 8; bit++) {
            result = result.withBit(bit, word.getBit(offset + bit));
        }
        return result;
    }

    @Override
    public ComponentRuntimeState createState() {
        return new State();
    }

    @Override
    public ComponentDebugSnapshot debugSnapshot(ComponentRuntimeState runtimeState) {
        State state = (State) runtimeState;
        LogicVector status = LogicVector.fromUnsignedLong(0, 8).withBit(0, state.pending);
        return new ComponentDebugSnapshot(Map.of(
                "COUNTER", state.counter,
                "RELOAD", state.reload,
                "CONTROL", state.control,
                "STATUS", status), List.of(), null, Map.of());
    }

    private static final class State implements ComponentRuntimeState {
        private LogicVector counter;
        private LogicVector reload;
        private LogicVector control;
        private LogicState pending;
        private LogicState lastClock;

        @Override
        public void reset() {
            counter = LogicVector.repeat(LogicState.ZERO, WORD);
            reload = LogicVector.repeat(LogicState.ZERO, WORD);
            control = LogicVector.repeat(LogicState.ZERO, BYTE);
            pending = LogicState.ZERO;
            lastClock = LogicState.UNKNOWN;
        }

        private State() {
            reset();
        }

        private void makeUnknown() {
            counter = LogicVector.repeat(LogicState.UNKNOWN, WORD);
            reload = LogicVector.repeat(LogicState.UNKNOWN, WORD);
            control = LogicVector.repeat(LogicState.UNKNOWN, BYTE);
            pending = LogicState.UNKNOWN;
        }

        @Override
        public Object snapshot() {
            return new Snapshot(counter, reload, control, pending, lastClock);
        }

        @Override
        public void restore(Object snapshot) {
            if (snapshot instanceof Snapshot saved) {
                counter = saved.counter();
                reload = saved.reload();
                control = saved.control();
                pending = saved.pending();
                lastClock = saved.lastClock();
            }
        }
    }

    private record Snapshot(LogicVector counter, LogicVector reload, LogicVector control,
                            LogicState pending, LogicState lastClock) {
    }
}
