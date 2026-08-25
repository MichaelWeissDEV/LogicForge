package dev.logicforge.simulation;

import dev.logicforge.logic.LogicOperation;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;

/**
 * Small behaviours used to exercise the engine on its own, without depending on the
 * component library.
 */
final class TestBehaviors {

    /** Drives a fixed value; models a constant. */
    static ComponentBehavior constant(LogicState value) {
        return context -> context.driveOutput(0, LogicVector.single(value));
    }

    /** Drives whatever its state says; models a toggle switch. */
    static final ComponentBehavior SWITCH = new ComponentBehavior() {

        @Override
        public void evaluate(ComponentContext context) {
            context.driveOutput(0, ((InputSourceState) context.state()).value());
        }

        @Override
        public ComponentRuntimeState createState() {
            return new SwitchState(LogicState.ZERO);
        }
    };

    static ComponentBehavior gate(LogicOperation operation, boolean invert) {
        return context -> {
            LogicState result = operation.identity();
            for (int i = 0; i < context.inputCount(); i++) {
                result = operation.apply(result, context.readInput(i).singleBit());
            }
            context.driveOutput(0, LogicVector.single(invert ? LogicOperations.not(result) : result));
        };
    }

    static final ComponentBehavior NOT =
            context -> context.driveOutput(0,
                    LogicVector.single(LogicOperations.not(context.readInput(0).singleBit())));

    /** ENABLE = 1 passes the input through, otherwise the output floats. */
    static final ComponentBehavior TRI_STATE = context -> {
        LogicState enable = LogicOperations.asGateInput(context.readInput(1).singleBit());
        LogicState value = switch (enable) {
            case ONE -> LogicOperations.asGateInput(context.readInput(0).singleBit());
            case ZERO -> LogicState.HIGH_IMPEDANCE;
            default -> LogicState.UNKNOWN;
        };
        context.driveOutput(0, LogicVector.single(value));
    };

    /**
     * Deliberately non-convergent: treats an undefined input as 0 and inverts, so a
     * feedback loop toggles forever instead of settling on X. Used to check that the
     * delta cycle limit catches runaway propagation.
     */
    static final ComponentBehavior ALWAYS_FLIPPING = context -> {
        LogicState input = context.readInput(0).singleBit();
        LogicState value = input == LogicState.ONE ? LogicState.ZERO : LogicState.ONE;
        context.driveOutput(0, LogicVector.single(value));
    };

    /**
     * A minimal free-running oscillator: toggles its single output every
     * {@code halfPeriod} picoseconds using {@link ComponentContext#scheduleWakeup},
     * without reading any input. Models the shape a clock generator behaviour takes.
     */
    static ComponentBehavior periodicToggle(long halfPeriod) {
        return new ComponentBehavior() {

            @Override
            public void evaluate(ComponentContext context) {
                OscillatorState state = (OscillatorState) context.state();
                long now = context.time();
                if (now >= state.nextEdgeAt) {
                    state.level = state.level == LogicState.ZERO ? LogicState.ONE : LogicState.ZERO;
                    state.nextEdgeAt = now + halfPeriod;
                }
                context.driveOutput(0, LogicVector.single(state.level));
                context.scheduleWakeup(state.nextEdgeAt);
            }

            @Override
            public ComponentRuntimeState createState() {
                return new OscillatorState(halfPeriod);
            }
        };
    }

    static final class OscillatorState implements ComponentRuntimeState {

        private final long halfPeriod;
        private LogicState level;
        private long nextEdgeAt;

        OscillatorState(long halfPeriod) {
            this.halfPeriod = halfPeriod;
            reset();
        }

        @Override
        public void reset() {
            level = LogicState.ZERO;
            nextEdgeAt = halfPeriod;
        }
    }

    static final class SwitchState implements InputSourceState {

        private final LogicState initial;
        private LogicVector value;

        SwitchState(LogicState initial) {
            this.initial = initial;
            this.value = LogicVector.single(initial);
        }

        @Override
        public LogicVector value() {
            return value;
        }

        @Override
        public void setValue(LogicVector newValue) {
            this.value = newValue;
        }

        @Override
        public void reset() {
            this.value = LogicVector.single(initial);
        }
    }

    private TestBehaviors() {
    }
}
