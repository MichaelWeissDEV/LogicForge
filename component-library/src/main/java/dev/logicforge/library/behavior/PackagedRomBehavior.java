package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;
import dev.logicforge.simulation.MemorySnapshot;

/** Generic read-mode EPROM engine with active-low CE/OE and an ignored program pin. */
public record PackagedRomBehavior(BitWidth dataWidth, LogicVector[] contents)
        implements ComponentBehavior {
    public PackagedRomBehavior {
        contents = contents.clone();
    }

    @Override
    public LogicVector[] contents() {
        return contents.clone();
    }

    @Override
    public void evaluate(ComponentContext context) {
        LogicState ceN = LogicOperations.asGateInput(context.readInput(1).singleBit());
        LogicState oeN = LogicOperations.asGateInput(context.readInput(2).singleBit());
        LogicVector z = LogicVector.repeat(LogicState.HIGH_IMPEDANCE, dataWidth);
        if (ceN == LogicState.ONE || oeN == LogicState.ONE) {
            context.driveOutput(0, z);
            return;
        }
        AddressPossibilities addresses = AddressPossibilities.resolve(
                context.readInput(0), contents.length);
        LogicVector[] merged = {null};
        addresses.forEach(address -> merged[0] = merged[0] == null ? contents[address]
                : StatefulControlPolicy.merge(merged[0], contents[address]));
        LogicVector read = merged[0] == null
                ? LogicVector.repeat(LogicState.UNKNOWN, dataWidth) : merged[0];
        context.driveOutput(0, ceN == LogicState.ZERO && oeN == LogicState.ZERO ? read
                : StatefulControlPolicy.merge(z, read));
    }

    @Override
    public MemorySnapshot memorySnapshot(ComponentRuntimeState state) {
        return new MemorySnapshot(contents);
    }

    @Override
    public dev.logicforge.simulation.MemoryInfo memoryInfo(ComponentRuntimeState state) {
        return new dev.logicforge.simulation.MemoryInfo(contents.length, dataWidth.bits(),
                0, 0, -1, -1, null);
    }

    @Override
    public dev.logicforge.simulation.MemoryPageSnapshot memoryPage(ComponentRuntimeState state,
                                                                   int startAddress, int count) {
        int start = Math.max(0, Math.min(startAddress, contents.length));
        int length = Math.max(0, Math.min(count, contents.length - start));
        LogicVector[] page = new LogicVector[length];
        System.arraycopy(contents, start, page, 0, length);
        return new dev.logicforge.simulation.MemoryPageSnapshot(start, page, 0);
    }

    @Override
    public dev.logicforge.simulation.ComponentDebugSnapshot debugSnapshot(
            ComponentRuntimeState state) {
        return new dev.logicforge.simulation.ComponentDebugSnapshot(
                java.util.Map.of(), java.util.List.of(), memoryInfo(state), java.util.Map.of());
    }
}
