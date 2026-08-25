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
    private long revision;
    private int lastReadAddress = -1;
    private int lastWriteAddress = -1;
    private LogicVector lastWrittenValue;

    RamState(int addressBits, BitWidth dataWidth) {
        this.wordCount = 1 << addressBits;
        this.dataWidth = dataWidth;
        reset();
    }

    LogicVector read(int address) {
        if (address >= 0 && address < wordCount) {
            if (lastReadAddress != address) {
                lastReadAddress = address;
                revision++;
            }
            return memory[address];
        }
        return LogicVector.repeat(LogicState.UNKNOWN, dataWidth);
    }

    void write(int address, LogicVector value) {
        if (address >= 0 && address < wordCount) {
            value.requireWidth(dataWidth);
            boolean changed = !memory[address].equals(value) || lastWriteAddress != address
                    || !value.equals(lastWrittenValue);
            memory[address] = value;
            lastWriteAddress = address;
            lastWrittenValue = value;
            if (changed) revision++;
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
        lastReadAddress = -1;
        lastWriteAddress = -1;
        lastWrittenValue = null;
        revision++;
    }

    @Override
    public Object snapshot() {
        // Deep-copy the memory array so the snapshot is independent of live state
        return new Snapshot(memory.clone(), wordCount, dataWidth, revision,
                lastReadAddress, lastWriteAddress, lastWrittenValue);
    }

    @Override
    public void restore(Object snap) {
        if (snap instanceof Snapshot s
                && s.wordCount() == wordCount
                && s.dataWidth().equals(dataWidth)) {
            System.arraycopy(s.memory(), 0, memory, 0, wordCount);
            revision = s.revision();
            lastReadAddress = s.lastReadAddress();
            lastWriteAddress = s.lastWriteAddress();
            lastWrittenValue = s.lastWrittenValue();
        }
        // else: incompatible configuration — keep zero-initialised reset state
    }

    @Override
    public MemorySnapshot memorySnapshot() {
        return new MemorySnapshot(memory, revision, dataWidth.bits(), lastReadAddress,
                lastWriteAddress, lastWrittenValue);
    }

    @Override public long memoryRevision() { return revision; }

    record Snapshot(LogicVector[] memory, int wordCount, BitWidth dataWidth, long revision,
                    int lastReadAddress, int lastWriteAddress, LogicVector lastWrittenValue) {}
}
