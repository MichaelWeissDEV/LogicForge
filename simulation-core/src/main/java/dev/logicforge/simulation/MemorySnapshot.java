package dev.logicforge.simulation;

import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;

/**
 * Read-only view of a memory array at a point in time.
 *
 * <p>Used by the UI to display RAM/ROM contents without direct access to behavior
 * internals. The constructor defensively copies the array so the caller's copy of the
 * state cannot be mutated through this snapshot.
 */
public record MemorySnapshot(LogicVector[] words) {

    public MemorySnapshot {
        words = words.clone();
    }

    /** Number of words in this memory. */
    public int size() {
        return words.length;
    }

    /**
     * Returns the word at the given address, or an all-{@code X} vector if the address is
     * out of range. The width of the vector matches the other words in this snapshot.
     *
     * @param address the zero-based word address
     * @return the stored word, never {@code null}
     */
    public LogicVector wordAt(int address) {
        if (address >= 0 && address < words.length) {
            return words[address];
        }
        // Out-of-range: return X of the same width as the rest (width from first word, or 1)
        int width = words.length > 0 ? words[0].width() : 1;
        return LogicVector.repeat(LogicState.UNKNOWN, width);
    }
}
