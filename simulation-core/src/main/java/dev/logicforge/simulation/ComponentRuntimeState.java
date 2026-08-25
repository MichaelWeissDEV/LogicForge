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

    /** Generic live state exposed without UI casts to component-specific state classes. */
    default ComponentDebugSnapshot debugSnapshot() {
        MemorySnapshot memory = memorySnapshot();
        return memory == null ? ComponentDebugSnapshot.EMPTY
                : new ComponentDebugSnapshot(java.util.Map.of(), java.util.List.of(), memory,
                java.util.Map.of());
    }

    /**
     * Writes a word to position {@code address} in this component's memory, if it has one.
     */
    default void writeMemoryWord(int address, dev.logicforge.logic.LogicVector value) {}
}
