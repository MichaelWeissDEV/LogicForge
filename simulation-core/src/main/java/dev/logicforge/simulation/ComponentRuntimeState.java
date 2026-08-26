package dev.logicforge.simulation;

/**
 * Mutable state a component keeps between evaluations.
 *
 * <p>Purely combinational gates have none; a toggle switch remembers what the user set,
 * and later registers, counters and memories will keep their contents here. The simulator
 * owns these objects so that a reset can restore a well defined initial state.
 */
@FunctionalInterface
public interface ComponentRuntimeState {

    /** Shared instance for components whose output only depends on their inputs. */
    ComponentRuntimeState STATELESS = () -> {
    };

    /** Restores the power-on state. */
    void reset();

    /**
     * Returns an opaque snapshot of this component's runtime state, or {@code null} if
     * this component has no persistent state worth preserving.
     * The returned object must be serialization-safe (no live references to simulation internals).
     */
    default Object snapshot() {
        return null;
    }

    /**
     * Attempts to restore from a snapshot previously captured by {@link #snapshot()}.
     * Implementations should validate that the snapshot is compatible (e.g., same width)
     * before applying it. If incompatible, silently ignore and keep current (reset) state.
     *
     * @param snap the snapshot object (may be {@code null}, in which case do nothing)
     */
    default void restore(Object snap) {}

    /**
     * Returns a read-only memory snapshot if this component has addressable memory,
     * otherwise {@code null}. Used by the UI to display RAM/ROM contents without
     * direct access to behavior internals.
     */
    default MemorySnapshot memorySnapshot() {
        return null;
    }

    /** Cheap monotonic memory/access revision, or {@code -1} for non-memory state. */
    default long memoryRevision() {
        return -1;
    }

    /**
     * Cheap memory metadata (size, last access) without the contents array, or {@code null}
     * for non-memory state. Memory-backed states should override this directly rather than
     * relying on the default derivation from {@link #memorySnapshot()}, which still clones
     * every word — see {@code RamState} for the cheap override.
     *
     * <p>The default cannot distinguish content changes from access-only ones (a single
     * {@link MemorySnapshot#revision()} counts as both), which is always safe — a viewer
     * following {@link MemoryInfo#contentRevision()} just reloads a page it didn't strictly
     * need to. A state that tracks the two separately should override this directly instead.
     */
    default MemoryInfo memoryInfo() {
        MemorySnapshot memory = memorySnapshot();
        return memory == null ? null : new MemoryInfo(memory.size(), memory.wordWidth(),
                memory.revision(), memory.revision(),
                memory.lastReadAddress(), memory.lastWriteAddress(), memory.lastWrittenValue());
    }

    /**
     * A window of {@code count} words starting at {@code startAddress}, or {@code null} for
     * non-memory state. The default derives it from {@link #memorySnapshot()} (still a full
     * clone under the hood); a memory-backed state should override this directly so a large
     * RAM/ROM can be paged through without ever cloning more than one page's worth of words
     * — see {@code RamState} for the cheap override.
     */
    default MemoryPageSnapshot memoryPage(int startAddress, int count) {
        MemorySnapshot memory = memorySnapshot();
        if (memory == null) {
            return null;
        }
        int clampedStart = Math.max(0, Math.min(startAddress, memory.size()));
        int clampedCount = Math.max(0, Math.min(count, memory.size() - clampedStart));
        dev.logicforge.logic.LogicVector[] words = new dev.logicforge.logic.LogicVector[clampedCount];
        for (int i = 0; i < clampedCount; i++) {
            words[i] = memory.wordAt(clampedStart + i);
        }
        return new MemoryPageSnapshot(clampedStart, words, memory.revision());
    }

    /** Generic live state exposed without UI casts to component-specific state classes. */
    default ComponentDebugSnapshot debugSnapshot() {
        MemoryInfo memory = memoryInfo();
        return memory == null ? ComponentDebugSnapshot.EMPTY
                : new ComponentDebugSnapshot(java.util.Map.of(), java.util.List.of(), memory,
                java.util.Map.of());
    }

    /**
     * Writes a word to position {@code address} in this component's memory, if it has one.
     */
    default void writeMemoryWord(int address, dev.logicforge.logic.LogicVector value) {}
}
