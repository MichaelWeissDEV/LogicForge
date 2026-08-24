package dev.logicforge.simulation;

/**
 * A component in the compiled circuit, reduced to what the simulator needs: which nets it
 * reads, which drivers it owns, and how it behaves.
 *
 * <p>Everything the editor cares about — UUIDs, coordinates, labels of wires — stayed
 * behind in the circuit document. The mapping between the two lives in the compiler's
 * source map.
 */
public record CompiledComponent(
        int id,
        String definitionId,
        String label,
        ComponentBehavior behavior,
        int[] inputNets,
        int[] outputNets,
        int[] outputDrivers) {

    public int inputCount() {
        return inputNets.length;
    }

    public int outputCount() {
        return outputNets.length;
    }
}
