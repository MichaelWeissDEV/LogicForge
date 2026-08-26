package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentDebugSnapshot;
import dev.logicforge.simulation.ComponentRuntimeState;
import dev.logicforge.simulation.InputSourceState;
import java.util.List;
import java.util.Map;

/** Combinational memory-mapped input driver that floats when it is not being read. */
public record InputPortBehavior(BitWidth width) implements ComponentBehavior {

    private static final int SELECT = 0;
    private static final int READ = 1;

    @Override
    public void evaluate(ComponentContext context) {
        LogicState readSelected = LogicOperations.and(
                context.readInput(SELECT).singleBit(), context.readInput(READ).singleBit());
        LogicVector floating = LogicVector.repeat(LogicState.HIGH_IMPEDANCE, width);
        LogicVector driven = ((State) context.state()).value();
        LogicVector result = switch (readSelected) {
            case ZERO -> floating;
            case ONE -> driven;
            case UNKNOWN, HIGH_IMPEDANCE -> mergeDrivePossibilities(floating, driven);
        };
        context.driveOutput(0, result);
    }

    @Override
    public ComponentRuntimeState createState() {
        return new State(width);
    }

    @Override
    public ComponentDebugSnapshot debugSnapshot(ComponentRuntimeState state) {
        return new ComponentDebugSnapshot(Map.of("VALUE", ((State) state).value()),
                List.of(), null, Map.of());
    }

    private LogicVector mergeDrivePossibilities(LogicVector floating, LogicVector driven) {
        LogicVector result = floating;
        for (int bit = 0; bit < width.bits(); bit++) {
            if (floating.getBit(bit) != driven.getBit(bit)) {
                result = result.withBit(bit, LogicState.UNKNOWN);
            }
        }
        return result;
    }

    private static final class State implements InputSourceState {
        private final BitWidth width;
        private LogicVector value;

        private State(BitWidth width) {
            this.width = width;
            reset();
        }

        @Override
        public LogicVector value() {
            return value;
        }

        @Override
        public void setValue(LogicVector value) {
            this.value = value.requireWidth(width);
        }

        @Override
        public void reset() {
            value = LogicVector.repeat(LogicState.ZERO, width);
        }

        @Override
        public Object snapshot() {
            return value;
        }

        @Override
        public void restore(Object snapshot) {
            if (snapshot instanceof LogicVector saved && saved.width() == width.bits()) {
                value = saved;
            }
        }
    }
}
