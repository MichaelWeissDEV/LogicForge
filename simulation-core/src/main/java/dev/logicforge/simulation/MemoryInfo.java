package dev.logicforge.simulation;

import dev.logicforge.logic.LogicVector;

/**
 * Cheap, size-independent metadata about a memory component's contents and last access —
 * everything the Inspector's debug view or a MemoryView's "should I refetch" check needs,
 * without cloning the (possibly 65536-word) contents array a full {@link MemorySnapshot} or
 * a {@link MemoryPageSnapshot} carries.
 *
 * <p>Revision is split in two so a viewer can tell "the stored data changed, reload the
 * page" apart from "only the read/write highlight moved, the page's words are still
 * correct": {@code contentRevision} changes only when a stored word's value actually
 * changes; {@code accessRevision} changes whenever {@code lastReadAddress},
 * {@code lastWriteAddress} or {@code lastWrittenValue} changes, which includes every
 * content change but also a read or a rewrite of the same value.
 */
public record MemoryInfo(int size, int wordWidth, long contentRevision, long accessRevision,
                         int lastReadAddress, int lastWriteAddress,
                         LogicVector lastWrittenValue) {

    public MemoryInfo {
        if (contentRevision < 0 || accessRevision < 0 || wordWidth < 1) {
            throw new IllegalArgumentException("Invalid memory info metadata");
        }
    }
}
