package dev.logicforge.simulation;

import dev.logicforge.logic.LogicVector;

/**
 * Cheap, size-independent metadata about a memory component's contents and last access —
 * everything the Inspector's debug view needs, without cloning the (possibly 65536-word)
 * contents array a full {@link MemorySnapshot} carries. Use {@link MemorySnapshot} instead
 * when the actual words are needed (paging through a RAM/ROM viewer, saving an image).
 */
public record MemoryInfo(int size, int wordWidth, long revision,
                         int lastReadAddress, int lastWriteAddress,
                         LogicVector lastWrittenValue) {

    public MemoryInfo {
        if (revision < 0 || wordWidth < 1) {
            throw new IllegalArgumentException("Invalid memory info metadata");
        }
    }

    public static MemoryInfo of(MemorySnapshot snapshot) {
        return new MemoryInfo(snapshot.size(), snapshot.wordWidth(), snapshot.revision(),
                snapshot.lastReadAddress(), snapshot.lastWriteAddress(), snapshot.lastWrittenValue());
    }
}
