package dev.logicforge.simulation;

import dev.logicforge.logic.LogicVector;

/**
 * The simulation behaviour of one kind of component — kept separate from its
 * {@code ComponentDefinition} (what it is) and its renderer (how it looks).
 *
 * <p>A behaviour is shared by all instances of a component, so it must not keep instance
 * data in fields; anything that must survive between evaluations belongs in the
 * {@link ComponentRuntimeState} it creates.
 */
public interface ComponentBehavior {

    /**
     * Recomputes the outputs. Called whenever an input net changed, when the component's
     * own state was changed from the outside, and once for every component on reset.
     */
    void evaluate(ComponentContext context);

    /** Creates the state object for one instance. Stateless components keep the default. */
    default ComponentRuntimeState createState() {
        return ComponentRuntimeState.STATELESS;
    }

    /** Read-only memory contents exposed uniformly by stateful RAM and stateless ROM. */
    default MemorySnapshot memorySnapshot(ComponentRuntimeState state) {
        return state.memorySnapshot();
    }

    default long memoryRevision(ComponentRuntimeState state) {
        return state.memoryRevision();
    }

    /** @see ComponentRuntimeState#memoryInfo() */
    default MemoryInfo memoryInfo(ComponentRuntimeState state) {
        return state.memoryInfo();
    }

    /** @see ComponentRuntimeState#memoryPage(int, int) */
    default MemoryPageSnapshot memoryPage(ComponentRuntimeState state, int startAddress, int count) {
        return state.memoryPage(startAddress, count);
    }

    default ComponentDebugSnapshot debugSnapshot(ComponentRuntimeState state) {
        return state.debugSnapshot();
    }

    /** Updates runtime memory when supported. Project-backed ROM is edited by the editor. */
    default void writeMemoryWord(ComponentRuntimeState state, int address, LogicVector value) {
        state.writeMemoryWord(address, value);
    }
}
