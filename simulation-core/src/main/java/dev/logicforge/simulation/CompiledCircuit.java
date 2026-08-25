package dev.logicforge.simulation;

import dev.logicforge.logic.BitWidth;
import java.util.ArrayList;
import java.util.List;

/**
 * The runtime form of a circuit: flat arrays indexed by compact integer ids.
 *
 * <p>Built by the {@code circuit-compiler} module, never edited. Signal propagation only
 * ever indexes into arrays; no UUID map is consulted while the simulation runs.
 */
public final class CompiledCircuit {

    private final CompiledComponent[] components;
    private final CompiledNet[] nets;
    private final CompiledDriver[] drivers;

    private CompiledCircuit(CompiledComponent[] components, CompiledNet[] nets, CompiledDriver[] drivers) {
        this.components = components;
        this.nets = nets;
        this.drivers = drivers;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int componentCount() {
        return components.length;
    }

    public int netCount() {
        return nets.length;
    }

    public int driverCount() {
        return drivers.length;
    }

    public CompiledComponent component(int id) {
        return components[id];
    }

    public CompiledNet net(int id) {
        return nets[id];
    }

    public CompiledDriver driver(int id) {
        return drivers[id];
    }

    /** Read-only view for iteration; the simulator itself uses indices. */
    public List<CompiledComponent> components() {
        return List.of(components);
    }

    public List<CompiledNet> nets() {
        return List.of(nets);
    }

    /** Assembles a compiled circuit; used by the circuit compiler. */
    public static final class Builder {

        private final List<CompiledNet> nets = new ArrayList<>();
        private final List<BitWidth> netWidths = new ArrayList<>();
        private final List<CompiledComponent> components = new ArrayList<>();
        private final List<CompiledDriver> drivers = new ArrayList<>();
        private final List<List<Integer>> driversByNet = new ArrayList<>();
        private final List<List<Integer>> consumersByNet = new ArrayList<>();

        private Builder() {
        }

        /** Adds a net and returns its runtime id. */
        public int addNet(BitWidth width) {
            int id = netWidths.size();
            netWidths.add(width);
            driversByNet.add(new ArrayList<>());
            consumersByNet.add(new ArrayList<>());
            return id;
        }

        /**
         * Adds a component and returns its runtime id. {@code inputNets} and
         * {@code outputNets} are indexed by the component's input and output port order.
         */
        public int addComponent(String definitionId, String label, ComponentBehavior behavior,
                                int[] inputNets, int[] outputNets) {
            CompiledInputBinding[] inputs = new CompiledInputBinding[inputNets.length];
            for (int i = 0; i < inputNets.length; i++) {
                inputs[i] = new CompiledInputBinding.Whole(inputNets[i], netWidths.get(inputNets[i]));
            }
            CompiledOutputBinding[] outputs = new CompiledOutputBinding[outputNets.length];
            for (int i = 0; i < outputNets.length; i++) {
                outputs[i] = new CompiledOutputBinding.Whole(
                        outputNets[i], -1, netWidths.get(outputNets[i]).bits());
            }
            return addComponent(definitionId, label, behavior, inputs, outputs);
        }

        /** Adds a component whose logical ports may bind to whole nets or per-bit nets. */
        public int addComponent(String definitionId, String label, ComponentBehavior behavior,
                                CompiledInputBinding[] inputBindings,
                                CompiledOutputBinding[] outputBindings) {
            int componentId = components.size();
            CompiledOutputBinding[] boundOutputs = new CompiledOutputBinding[outputBindings.length];
            for (int outputIndex = 0; outputIndex < outputBindings.length; outputIndex++) {
                CompiledOutputBinding binding = outputBindings[outputIndex];
                if (binding instanceof CompiledOutputBinding.Whole whole) {
                    int driverId = addDriver(componentId, outputIndex, whole.netId());
                    boundOutputs[outputIndex] = new CompiledOutputBinding.Whole(
                            whole.netId(), driverId, whole.width());
                } else {
                    CompiledOutputBinding.Bits bits = (CompiledOutputBinding.Bits) binding;
                    int[] netIds = bits.netIds();
                    int[] driverIds = new int[netIds.length];
                    for (int bit = 0; bit < netIds.length; bit++) {
                        driverIds[bit] = addDriver(componentId, outputIndex, netIds[bit]);
                    }
                    boundOutputs[outputIndex] = new CompiledOutputBinding.Bits(netIds, driverIds);
                }
            }

            for (CompiledInputBinding binding : inputBindings) {
                if (binding instanceof CompiledInputBinding.Whole whole) {
                    addConsumer(whole.netId(), componentId);
                } else {
                    for (int netId : ((CompiledInputBinding.Bits) binding).netIds()) {
                        addConsumer(netId, componentId);
                    }
                }
            }
            components.add(new CompiledComponent(componentId, definitionId, label, behavior,
                    inputBindings, boundOutputs));
            return componentId;
        }

        private int addDriver(int componentId, int outputIndex, int netId) {
            int driverId = drivers.size();
            drivers.add(new CompiledDriver(driverId, componentId, outputIndex, netId));
            driversByNet.get(netId).add(driverId);
            return driverId;
        }

        private void addConsumer(int netId, int componentId) {
            List<Integer> consumers = consumersByNet.get(netId);
            if (!consumers.contains(componentId)) {
                consumers.add(componentId);
            }
        }

        public CompiledCircuit build() {
            for (int netId = 0; netId < netWidths.size(); netId++) {
                nets.add(new CompiledNet(netId, netWidths.get(netId),
                        toArray(driversByNet.get(netId)), toArray(consumersByNet.get(netId))));
            }
            return new CompiledCircuit(components.toArray(CompiledComponent[]::new),
                    nets.toArray(CompiledNet[]::new), drivers.toArray(CompiledDriver[]::new));
        }

        private static int[] toArray(List<Integer> values) {
            int[] array = new int[values.size()];
            for (int i = 0; i < array.length; i++) {
                array[i] = values.get(i);
            }
            return array;
        }
    }
}
