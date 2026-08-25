package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import java.util.OptionalLong;

/**
 * A 1:N demultiplexer: routes IN onto the output SEL selects; every other output floats
 * (Z), the same way an unused tri-state branch of a bus does. An undefined SEL drives every
 * output to X, since which one should carry the value cannot be known.
 *
 * <p>Ports are {@code IN, SEL} in, {@code OUT0..OUT(n-1)} out.
 *
 * @param dataWidth the bus width of IN and of every output
 * @param outputCount how many outputs this instance has
 */
public record DemuxBehavior(BitWidth dataWidth, int outputCount) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        LogicVector selector = context.readInput(1);
        OptionalLong selected = selector.toUnsignedLong();
        LogicVector input = LogicOperations.asGateInput(context.readInput(0));
        for (int i = 0; i < outputCount; i++) {
            if (selected.isEmpty()) {
                context.driveOutput(i, LogicVector.repeat(LogicState.UNKNOWN, dataWidth));
            } else if (selected.getAsLong() == i) {
                context.driveOutput(i, input);
            } else {
                context.driveOutput(i, LogicVector.repeat(LogicState.HIGH_IMPEDANCE, dataWidth));
            }
        }
    }
}
