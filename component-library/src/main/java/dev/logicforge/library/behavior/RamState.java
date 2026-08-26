package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentRuntimeState;
import dev.logicforge.simulation.MemoryInfo;
import dev.logicforge.simulation.MemoryPageSnapshot;
import dev.logicforge.simulation.MemorySnapshot;
import java.util.Arrays;

/**
 * The contents of a RAM: one word per address, zero-initialized on reset for deterministic
 * simulation (a documented, revisitable choice — real hardware powers up undefined).
 *
 * <p>Tracks two revisions: {@code contentRevision} changes only when a stored word's value
 * actually changes; {@code accessRevision} changes whenever the last-read or last-write
 * metadata changes, whether or not the underlying value did (a write of the same value to a
 * new address still moves the "last write" highlight, so it counts). Content changes always
 * bump both; {@link #memoryRevision()} — the older, single-counter API — reports
 * {@code accessRevision}, matching what it always meant here.
 */
final class RamState implements ComponentRuntimeState {

    private final int wordCount;
    private final BitWidth dataWidth;
    private LogicVector[] memory;
    private long contentRevision;
    private long accessRevision;
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
                accessRevision++;
            }
            return memory[address];
        }
        return LogicVector.repeat(LogicState.UNKNOWN, dataWidth);
    }

    void write(int address, LogicVector value) {
        if (address >= 0 && address < wordCount) {
            value.requireWidth(dataWidth);
            boolean contentChanged = !memory[address].equals(value);
            boolean metadataChanged = lastWriteAddress != address || !value.equals(lastWrittenValue);
            memory[address] = value;
            lastWriteAddress = address;
            lastWrittenValue = value;
            if (contentChanged) {
                contentRevision++;
            }
            if (contentChanged || metadataChanged) {
                accessRevision++;
            }
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
        contentRevision++;
        accessRevision++;
    }

    @Override
    public Object snapshot() {
        // Deep-copy the memory array so the snapshot is independent of live state
        return new Snapshot(memory.clone(), wordCount, dataWidth, contentRevision, accessRevision,
                lastReadAddress, lastWriteAddress, lastWrittenValue);
    }

    @Override
    public void restore(Object snap) {
        if (snap instanceof Snapshot s
                && s.wordCount() == wordCount
                && s.dataWidth().equals(dataWidth)) {
            System.arraycopy(s.memory(), 0, memory, 0, wordCount);
            contentRevision = s.contentRevision();
            accessRevision = s.accessRevision();
            lastReadAddress = s.lastReadAddress();
            lastWriteAddress = s.lastWriteAddress();
            lastWrittenValue = s.lastWrittenValue();
        }
        // else: incompatible configuration — keep zero-initialised reset state
    }

    @Override
    public MemorySnapshot memorySnapshot() {
        return new MemorySnapshot(memory, contentRevision, dataWidth.bits(), lastReadAddress,
                lastWriteAddress, lastWrittenValue);
    }

    /** Avoids {@link #memorySnapshot()}'s array clone — the Inspector only needs metadata. */
    @Override
    public MemoryInfo memoryInfo() {
        return new MemoryInfo(wordCount, dataWidth.bits(), contentRevision, accessRevision,
                lastReadAddress, lastWriteAddress, lastWrittenValue);
    }

    /** Avoids cloning the whole array — only the requested window. */
    @Override
    public MemoryPageSnapshot memoryPage(int startAddress, int count) {
        int start = Math.max(0, Math.min(startAddress, wordCount));
        int clampedCount = Math.max(0, Math.min(count, wordCount - start));
        LogicVector[] page = new LogicVector[clampedCount];
        System.arraycopy(memory, start, page, 0, clampedCount);
        return new MemoryPageSnapshot(start, page, contentRevision);
    }

    @Override public long memoryRevision() { return accessRevision; }

    record Snapshot(LogicVector[] memory, int wordCount, BitWidth dataWidth,
                    long contentRevision, long accessRevision,
                    int lastReadAddress, int lastWriteAddress, LogicVector lastWrittenValue) {}
}
