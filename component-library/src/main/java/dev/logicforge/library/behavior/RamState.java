package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentRuntimeState;
import dev.logicforge.simulation.MemorySnapshot;
import java.util.Arrays;

/**
 * The contents of a RAM: one word per address, zero-initialized on reset for deterministic
 * simulation (a documented, revisitable choice — real hardware powers up undefined).
 */
final class RamState implements ComponentRuntimeState {

    private final int wordCount;
    private final BitWidth dataWidth;
    private LogicVector[] memory;

    RamState(int addressBits, BitWidth dataWidth) {
        this.wordCount = 1 << addressBits;
        this.dataWidth = dataWidth;
        reset();
    }

    LogicVector read(int address) {
        return address >= 0 && address < wordCount ? memory[address] : LogicVector.repeat(LogicState.UNKNOWN, dataWidth);
    }

    void write(int address, LogicVector value) {
        if (address >= 0 && address < wordCount) {
            memory[address] = value;
        }
    }

    @Override
    public void writeMemoryWord(int address, LogicVector value) {
        write(address, value);
    }

    int wordCount() {
        return wordCount;
    }

    @Override
    public void reset() {
        memory = new LogicVector[wordCount];
        Arrays.fill(memory, LogicVector.repeat(LogicState.ZERO, dataWidth));
    }

    @Override
    public Object snapshot() {
        // Deep-copy the memory array so the snapshot is independent of live state
        return new Snapshot(memory.clone(), wordCount, dataWidth);
    }

    @Override
    public void restore(Object snap) {
        if (snap instanceof Snapshot s
                && s.wordCount() == wordCount
                && s.dataWidth().equals(dataWidth)) {
            System.arraycopy(s.memory(), 0, memory, 0, wordCount);
        }
        // else: incompatible configuration — keep zero-initialised reset state
    }

    @Override
    public MemorySnapshot memorySnapshot() {
        return new MemorySnapshot(memory.clone());
    }

    record Snapshot(LogicVector[] memory, int wordCount, BitWidth dataWidth) {}
}
