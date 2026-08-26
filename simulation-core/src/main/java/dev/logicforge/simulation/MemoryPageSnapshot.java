package dev.logicforge.simulation;

import dev.logicforge.logic.LogicVector;

/**
 * A window of a memory's contents — {@code words.length} words starting at
 * {@code startAddress} — for paging through a large RAM/ROM without cloning the whole
 * thing. {@code contentRevision} is {@link MemoryInfo#contentRevision()} at the moment this
 * page was read, so a viewer can tell whether a page it already has is still current
 * without re-fetching it.
 */
public record MemoryPageSnapshot(int startAddress, LogicVector[] words, long contentRevision) {

    public MemoryPageSnapshot {
        words = words.clone();
        if (startAddress < 0 || contentRevision < 0) {
            throw new IllegalArgumentException("Invalid memory page metadata");
        }
    }

    @Override
    public LogicVector[] words() {
        return words.clone();
    }

    public int size() {
        return words.length;
    }

    /** The word at absolute address {@code address}, or all-X if outside this page. */
    public LogicVector wordAt(int address) {
        int index = address - startAddress;
        if (index >= 0 && index < words.length) {
            return words[index];
        }
        int width = words.length > 0 ? words[0].width() : 1;
        return LogicVector.repeat(dev.logicforge.logic.LogicState.UNKNOWN, width);
    }
}
