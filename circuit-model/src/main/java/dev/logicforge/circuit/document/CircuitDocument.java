package dev.logicforge.circuit.document;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import dev.logicforge.circuit.chip.ChipInstance;

/**
 * The circuit the user edits: placed components, the wires between them, and a bit of
 * metadata.
 *
 * <p>This is the editing model, not the simulation model. It is organised for stable
 * identity and cheap editing, keeps its elements in insertion order so that saving is
 * reproducible, and knows nothing about nets, evaluation or drawing. The
 * {@code circuit-compiler} module turns it into the compact runtime structure the
 * simulator uses.
 */
public final class CircuitDocument {

    private final Map<UUID, ComponentInstance> components = new LinkedHashMap<>();
    private final Map<UUID, Connection> connections = new LinkedHashMap<>();
    private final Map<UUID, ChipInstance> chips = new LinkedHashMap<>();
    private final List<CircuitDocumentListener> listeners = new ArrayList<>();

    private CircuitMetadata metadata;

    public CircuitDocument() {
        this(CircuitMetadata.DEFAULT);
    }

    public CircuitDocument(CircuitMetadata metadata) {
        this.metadata = metadata;
    }

    public CircuitMetadata metadata() {
        return metadata;
    }

    public void setMetadata(CircuitMetadata newMetadata) {
        this.metadata = newMetadata;
        notifyListeners(new CircuitChange(CircuitChange.Kind.METADATA, null));
    }

    // ------------------------------------------------------------------
    // Components
    // ------------------------------------------------------------------

    public Collection<ComponentInstance> components() {
        return Collections.unmodifiableCollection(components.values());
    }

    public int componentCount() {
        return components.size();
    }

    public Optional<ComponentInstance> component(UUID id) {
        return Optional.ofNullable(components.get(id));
    }

    /** The component with this id, or a failure if it is gone — for command code. */
    public ComponentInstance requireComponent(UUID id) {
        ComponentInstance instance = components.get(id);
        if (instance == null) {
            throw new IllegalStateException("No component " + id + " in this circuit");
        }
        return instance;
    }

    public void addComponent(ComponentInstance instance) {
        if (components.putIfAbsent(instance.id(), instance) != null) {
            throw new IllegalStateException("Component " + instance.id() + " already exists");
        }
        notifyListeners(new CircuitChange(CircuitChange.Kind.COMPONENT_ADDED, instance.id()));
    }

    /**
     * Removes a component together with every wire attached to it, so no connection can
     * ever refer to a component that is no longer there.
     *
     * @return the connections that were removed, in their original order
     */
    public List<Connection> removeComponent(UUID componentId) {
        ComponentInstance removed = components.remove(componentId);
        if (removed == null) {
            throw new IllegalStateException("No component " + componentId + " in this circuit");
        }
        List<Connection> detached = new ArrayList<>();
        for (Connection connection : List.copyOf(connections.values())) {
            if (connection.touches(componentId)) {
                connections.remove(connection.id());
                detached.add(connection);
            }
        }
        notifyListeners(new CircuitChange(CircuitChange.Kind.COMPONENT_REMOVED, componentId));
        return detached;
    }

    /** Replaces a component with an edited copy that has the same id. */
    public void replaceComponent(ComponentInstance instance) {
        ComponentInstance previous = requireComponent(instance.id());
        if (previous.equals(instance)) {
            // No-op replacement: instance is identical to previous, skip notification
            return;
        }
        components.put(instance.id(), instance);
        
        // Determine the most specific change kind
        boolean definitionChanged = !previous.definitionId().equals(instance.definitionId());
        boolean parametersChanged = !previous.parameters().equals(instance.parameters());
        boolean positionChanged = !previous.position().equals(instance.position());
        boolean rotationChanged = previous.rotation() != instance.rotation();
        boolean labelChanged = !previous.label().equals(instance.label());
        boolean presentationChanged = previous.portDisplayMode() != instance.portDisplayMode();
        
        CircuitChange.Kind kind;
        if (definitionChanged || parametersChanged) {
            kind = CircuitChange.Kind.COMPONENT_RECONFIGURED;
        } else if (presentationChanged && !positionChanged && !rotationChanged && !labelChanged) {
            kind = CircuitChange.Kind.COMPONENT_PRESENTATION;
        } else if (positionChanged && !rotationChanged && !labelChanged) {
            kind = CircuitChange.Kind.COMPONENT_MOVED;
        } else if (!positionChanged && rotationChanged && !labelChanged) {
            kind = CircuitChange.Kind.COMPONENT_ROTATED;
        } else if (!positionChanged && !rotationChanged && labelChanged) {
            kind = CircuitChange.Kind.COMPONENT_RENAMED;
        } else if (positionChanged || rotationChanged) {
            // Combined position and/or rotation change
            kind = CircuitChange.Kind.COMPONENT_MOVED;
        } else if (labelChanged) {
            kind = CircuitChange.Kind.COMPONENT_RENAMED;
        } else {
            // Fallback for any other change
            kind = CircuitChange.Kind.COMPONENT_MOVED;
        }
        
        notifyListeners(new CircuitChange(kind, instance.id()));
    }

    // ------------------------------------------------------------------
    // Connections
    // ------------------------------------------------------------------

    public Collection<Connection> connections() {
        return Collections.unmodifiableCollection(connections.values());
    }

    public int connectionCount() {
        return connections.size();
    }

    public Optional<Connection> connection(UUID id) {
        return Optional.ofNullable(connections.get(id));
    }

    public void addConnection(Connection connection) {
        requireEndpointHost(connection.from());
        requireEndpointHost(connection.to());
        if (connections.putIfAbsent(connection.id(), connection) != null) {
            throw new IllegalStateException("Connection " + connection.id() + " already exists");
        }
        notifyListeners(new CircuitChange(CircuitChange.Kind.CONNECTION_ADDED, connection.id()));
    }

    private void requireEndpointHost(ElectricalEndpoint endpoint) {
        if (endpoint instanceof ElectricalEndpoint.ComponentEndpoint ce) {
            requireComponent(ce.port().componentId());
        } else if (endpoint instanceof ElectricalEndpoint.ChipPinEndpoint cp) {
            requireChip(cp.chipInstanceId());
        }
    }

    public void removeConnection(UUID connectionId) {
        Connection removed = connections.remove(connectionId);
        if (removed == null) {
            throw new IllegalStateException("No connection " + connectionId + " in this circuit");
        }
        notifyListeners(new CircuitChange(CircuitChange.Kind.CONNECTION_REMOVED, connectionId));
    }

    /** Replaces a wire's manual routing without changing what it connects. */
    public void replaceConnection(Connection connection) {
        Connection previous = connections.get(connection.id());
        if (previous == null) {
            throw new IllegalStateException("No connection " + connection.id() + " in this circuit");
        }
        if (!previous.from().equals(connection.from()) || !previous.to().equals(connection.to())) {
            throw new IllegalArgumentException("Rewiring must remove and add a connection");
        }
        connections.put(connection.id(), connection);
        notifyListeners(new CircuitChange(CircuitChange.Kind.CONNECTION_ROUTED, connection.id()));
    }

    /** Every wire attached to the given port. */
    public List<Connection> connectionsAt(PortReference port) {
        return connections.values().stream().filter(connection -> connection.touches(port)).toList();
    }

    /** Every wire attached to this exact whole or bit endpoint. */
    public List<Connection> connectionsAt(PortEndpoint endpoint) {
        return connections.values().stream()
                .filter(connection -> connection.touchesEndpoint(endpoint)).toList();
    }

    /** Every wire attached to any port of the given component. */
    public List<Connection> connectionsOf(UUID componentId) {
        return connections.values().stream().filter(connection -> connection.touches(componentId)).toList();
    }

    public boolean isConnected(PortReference a, PortReference b) {
        return connections.values().stream()
                .anyMatch(connection -> {
                    var from = connection.fromPort().orElse(null);
                    var to = connection.toPort().orElse(null);
                    if (from == null || to == null) return false;
                    return (from.equals(a) && to.equals(b)) || (from.equals(b) && to.equals(a));
                });
    }

    public boolean isConnected(PortEndpoint a, PortEndpoint b) {
        ElectricalEndpoint ea = new ElectricalEndpoint.ComponentEndpoint(a);
        ElectricalEndpoint eb = new ElectricalEndpoint.ComponentEndpoint(b);
        return connections.values().stream()
                .anyMatch(connection -> (connection.from().equals(ea) && connection.to().equals(eb))
                        || (connection.from().equals(eb) && connection.to().equals(ea)));
    }

    // ------------------------------------------------------------------
    // Chips
    // ------------------------------------------------------------------

    public Collection<ChipInstance> chips() {
        return Collections.unmodifiableCollection(chips.values());
    }

    public int chipCount() {
        return chips.size();
    }

    public Optional<ChipInstance> chip(UUID id) {
        return Optional.ofNullable(chips.get(id));
    }

    /** The chip with this id, or a failure if it is gone — for command code. */
    public ChipInstance requireChip(UUID id) {
        ChipInstance instance = chips.get(id);
        if (instance == null) {
            throw new IllegalStateException("No chip " + id + " in this circuit");
        }
        return instance;
    }

    public void addChip(ChipInstance instance) {
        if (chips.putIfAbsent(instance.id(), instance) != null) {
            throw new IllegalStateException("Chip " + instance.id() + " already exists");
        }
        notifyListeners(new CircuitChange(CircuitChange.Kind.CHIP_ADDED, instance.id()));
    }

    /**
     * Removes a chip together with every wire attached to its physical pins, so no
     * connection can ever refer to a chip that is no longer there.
     *
     * @return the connections that were removed, in their original order
     */
    public List<Connection> removeChip(UUID chipId) {
        ChipInstance removed = chips.remove(chipId);
        if (removed == null) {
            throw new IllegalStateException("No chip " + chipId + " in this circuit");
        }
        List<Connection> detached = new ArrayList<>();
        for (Connection connection : List.copyOf(connections.values())) {
            if (connection.touchesChip(chipId)) {
                connections.remove(connection.id());
                detached.add(connection);
            }
        }
        notifyListeners(new CircuitChange(CircuitChange.Kind.CHIP_REMOVED, chipId));
        return detached;
    }

    /** Replaces a chip with an edited copy that has the same id. */
    public void replaceChip(ChipInstance instance) {
        ChipInstance previous = requireChip(instance.id());
        if (previous.equals(instance)) {
            return;
        }
        chips.put(instance.id(), instance);

        boolean positionChanged = !previous.position().equals(instance.position());
        boolean rotationChanged = previous.rotation() != instance.rotation();
        boolean displayModeChanged = previous.displayMode() != instance.displayMode();
        boolean designatorChanged = !previous.referenceDesignator().equals(instance.referenceDesignator());

        CircuitChange.Kind kind;
        if (displayModeChanged && !positionChanged && !rotationChanged && !designatorChanged) {
            kind = CircuitChange.Kind.CHIP_PRESENTATION;
        } else if (positionChanged && !rotationChanged) {
            kind = CircuitChange.Kind.CHIP_MOVED;
        } else if (!positionChanged && rotationChanged) {
            kind = CircuitChange.Kind.CHIP_ROTATED;
        } else if (designatorChanged && !positionChanged && !rotationChanged) {
            kind = CircuitChange.Kind.CHIP_RENAMED;
        } else {
            kind = CircuitChange.Kind.CHIP_MOVED;
        }
        notifyListeners(new CircuitChange(kind, instance.id()));
    }

    // ------------------------------------------------------------------
    // Bulk operations, listeners
    // ------------------------------------------------------------------

    /** An independent copy holding the same components and wires. Listeners are not copied. */
    public CircuitDocument copy() {
        CircuitDocument copy = new CircuitDocument(metadata);
        copy.components.putAll(components);
        copy.chips.putAll(chips);
        copy.connections.putAll(connections);
        return copy;
    }

    /**
     * Structural equality: same components (id, definition, position, rotation, parameters,
     * label) and same wires. Used by the save/load round-trip tests.
     */
    public boolean structurallyEquals(CircuitDocument other) {
        return metadata.equals(other.metadata)
                && components.equals(other.components)
                && chips.equals(other.chips)
                && connections.equals(other.connections);
    }

    public void addListener(CircuitDocumentListener listener) {
        listeners.add(listener);
    }

    public void removeListener(CircuitDocumentListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(CircuitChange change) {
        for (CircuitDocumentListener listener : List.copyOf(listeners)) {
            listener.onCircuitChanged(this, change);
        }
    }
}
