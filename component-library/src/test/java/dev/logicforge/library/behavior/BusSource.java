package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;
import dev.logicforge.simulation.InputSourceState;

/**
 * A settable source of any bit width, driven through {@code Simulation.setInput}. What
 * {@code source.toggle} is for single-bit tests, this is for bus-width ones: DATA on a
 * register, ADDRESS on a memory, and so on.
 */
final class BusSource implements ComponentBehavior {

    private final BitWidth width;

    BusSource(BitWidth width) {
        this.width = width;
    }

    @Override
    public void evaluate(ComponentContext context) {
        context.driveOutput(0, ((State) context.state()).value());
    }

    @Override
    public ComponentRuntimeState createState() {
        return new State(width);
    }

    private static final class State implements InputSourceState {

        private final BitWidth width;
        private LogicVector value;

        State(BitWidth width) {
            this.width = width;
            this.value = LogicVector.repeat(LogicState.ZERO, width);
        }

        @Override
        public LogicVector value() {
            return value;
        }

        @Override
        public void setValue(LogicVector newValue) {
            this.value = newValue.requireWidth(width);
        }

        @Override
        public void reset() {
            this.value = LogicVector.repeat(LogicState.ZERO, width);
        }
    }
}
