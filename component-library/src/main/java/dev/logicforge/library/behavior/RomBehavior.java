package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;
import dev.logicforge.simulation.MemorySnapshot;
import java.util.OptionalLong;

/**
 * A read-only memory: while ENABLE is 1, drives {@code contents[ADDRESS]} onto DATA;
 * otherwise DATA floats (Z). Contents are fixed at compile time — a ROM has no state and
 * needs none, unlike RAM.
 *
 * <p>Ports are {@code ADDRESS, ENABLE} in, {@code DATA} out.
 *
 * @param dataWidth the bus width of DATA
 * @param contents one entry per address, {@code contents.length} words
 */
public record RomBehavior(BitWidth dataWidth, LogicVector[] contents) implements ComponentBehavior {

    private static final int ADDRESS = 0;
    private static final int ENABLE = 1;

    public RomBehavior {
        contents = contents.clone();
    }

    @Override
    public LogicVector[] contents() {
        return contents.clone();
    }

    @Override
    public void evaluate(ComponentContext context) {
        LogicState enable = LogicOperations.asGateInput(context.readInput(ENABLE).singleBit());
        if (enable == LogicState.ZERO) {
            context.driveOutput(0, LogicVector.repeat(LogicState.HIGH_IMPEDANCE, dataWidth));
            return;
        }
        OptionalLong address = context.readInput(ADDRESS).toUnsignedLong();
        if (enable != LogicState.ONE || address.isEmpty() || address.getAsLong() >= contents.length) {
            context.driveOutput(0, LogicVector.repeat(LogicState.UNKNOWN, dataWidth));
            return;
        }
        context.driveOutput(0, contents[(int) address.getAsLong()]);
    }

    @Override
    public MemorySnapshot memorySnapshot(ComponentRuntimeState state) {
        return new MemorySnapshot(contents);
    }

    @Override public long memoryRevision(ComponentRuntimeState state) { return 0; }

    @Override
    public dev.logicforge.simulation.ComponentDebugSnapshot debugSnapshot(ComponentRuntimeState state) {
        return new dev.logicforge.simulation.ComponentDebugSnapshot(
                java.util.Map.of(), java.util.List.of(), memorySnapshot(state), java.util.Map.of());
    }
}
