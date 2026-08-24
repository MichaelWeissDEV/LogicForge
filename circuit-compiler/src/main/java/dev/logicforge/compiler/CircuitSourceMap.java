package dev.logicforge.compiler;

import dev.logicforge.circuit.document.PortReference;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Translates between what the user edits and what the simulator runs.
 *
 * <p>The compiled circuit only knows small integers. The editor needs to go the other way
 * — "which net does this wire belong to", "which runtime component is this gate" — and
 * this map is where that knowledge lives, kept out of both the document and the simulator.
 */
public record CircuitSourceMap(
        Map<UUID, Integer> componentIdByUuid,
        List<UUID> uuidByComponentId,
        Map<PortReference, Integer> netByPort,
        List<List<PortReference>> portsByNet,
        Map<UUID, Integer> netByConnection) {

    public CircuitSourceMap {
        componentIdByUuid = Map.copyOf(componentIdByUuid);
        uuidByComponentId = List.copyOf(uuidByComponentId);
        netByPort = Map.copyOf(netByPort);
        portsByNet = List.copyOf(portsByNet);
        netByConnection = Map.copyOf(netByConnection);
    }

    /** The runtime id of a placed component, if it made it into the compiled circuit. */
    public OptionalInt componentId(UUID componentUuid) {
        Integer id = componentIdByUuid.get(componentUuid);
        return id == null ? OptionalInt.empty() : OptionalInt.of(id);
    }

    public Optional<UUID> componentUuid(int componentId) {
        return componentId >= 0 && componentId < uuidByComponentId.size()
                ? Optional.of(uuidByComponentId.get(componentId))
                : Optional.empty();
    }

    /** The net a port is attached to. */
    public OptionalInt netOf(PortReference port) {
        Integer net = netByPort.get(port);
        return net == null ? OptionalInt.empty() : OptionalInt.of(net);
    }

    /** The net a wire belongs to — used to show live signal values on wires. */
    public OptionalInt netOfConnection(UUID connectionId) {
        Integer net = netByConnection.get(connectionId);
        return net == null ? OptionalInt.empty() : OptionalInt.of(net);
    }

    /** Every port that shares the given net. */
    public List<PortReference> portsOf(int netId) {
        return portsByNet.get(netId);
    }

    public int netCount() {
        return portsByNet.size();
    }
}
