package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentRuntimeState;
import java.util.Arrays;

/**
 * The stored words of a register file, plus the clock level last observed for rising-edge
 * detection. Power-on value is all-zero for every register.
 */
final class RegisterFileState implements ComponentRuntimeState {

    private final BitWidth width;
    private final int count;
    LogicVector[] registers;
    LogicState lastClock = LogicState.UNKNOWN;

    RegisterFileState(BitWidth width, int count) {
        this.width = width;
        this.count = count;
        reset();
    }

    @Override
    public void reset() {
        registers = new LogicVector[count];
        Arrays.fill(registers, LogicVector.repeat(LogicState.ZERO, width));
        lastClock = LogicState.UNKNOWN;
    }

    @Override
    public Object snapshot() {
        return new Snapshot(registers.clone(), width, count, lastClock);
    }

    @Override
    public void restore(Object snap) {
        if (snap instanceof Snapshot s && s.width().equals(width) && s.count() == count) {
            this.registers = s.registers().clone();
            this.lastClock = s.lastClock();
        }
        // else: incompatible — keep reset state
    }

    record Snapshot(LogicVector[] registers, BitWidth width, int count, LogicState lastClock) {}
}
