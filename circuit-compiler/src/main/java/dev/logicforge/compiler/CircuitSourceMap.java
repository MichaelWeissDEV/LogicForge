package dev.logicforge.compiler;

import dev.logicforge.circuit.document.PortEndpoint;
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
        Map<UUID, Integer> netByConnection,
        Map<PortEndpoint, Integer> netByEndpoint,
        List<List<PortEndpoint>> endpointsByNet,
        Map<PortReference, Integer> portWidth,
        Map<PortReference, Boolean> portBitMode) {

    public CircuitSourceMap {
        componentIdByUuid = Map.copyOf(componentIdByUuid);
        uuidByComponentId = List.copyOf(uuidByComponentId);
        netByPort = Map.copyOf(netByPort);
        portsByNet = portsByNet.stream().map(List::copyOf).toList();
        netByConnection = Map.copyOf(netByConnection);
        netByEndpoint = Map.copyOf(netByEndpoint);
        endpointsByNet = endpointsByNet.stream().map(List::copyOf).toList();
        portWidth = Map.copyOf(portWidth);
        portBitMode = Map.copyOf(portBitMode);
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

    /**
     * The net carrying an endpoint. A bit of a whole-bound bus maps to its vector net;
     * a bit-bound endpoint maps to its actual scalar net.
     */
    public OptionalInt netOf(PortEndpoint endpoint) {
        Integer net = netByEndpoint.get(endpoint);
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

    /** Every effective electrical endpoint that shares the given net. */
    public List<PortEndpoint> endpointsOf(int netId) {
        return endpointsByNet.get(netId);
    }

    public int netCount() {
        return portsByNet.size();
    }

    /** The declared bit width of a port, straight from its {@code PortSpec} — no probing. */
    public OptionalInt widthOf(PortReference port) {
        Integer width = portWidth.get(port);
        return width == null ? OptionalInt.empty() : OptionalInt.of(width);
    }

    /**
     * {@code true} once any connection has forced this port into bit mode (every bit its
     * own independent net) rather than one net for the whole port. {@code false} (including
     * for an unknown port) means whole-port mode.
     */
    public boolean isBitMode(PortReference port) {
        return portBitMode.getOrDefault(port, false);
    }
}
