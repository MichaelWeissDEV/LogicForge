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
        CompiledInputBinding[] inputBindings,
        CompiledOutputBinding[] outputBindings) {

    public CompiledComponent {
        inputBindings = inputBindings.clone();
        outputBindings = outputBindings.clone();
    }

    @Override
    public CompiledInputBinding[] inputBindings() {
        return inputBindings.clone();
    }

    @Override
    public CompiledOutputBinding[] outputBindings() {
        return outputBindings.clone();
    }

    public int inputCount() {
        return inputBindings.length;
    }

    public CompiledInputBinding inputBinding(int index) {
        return inputBindings[index];
    }

    public int outputCount() {
        return outputBindings.length;
    }

    public CompiledOutputBinding outputBinding(int index) {
        return outputBindings[index];
    }
}
