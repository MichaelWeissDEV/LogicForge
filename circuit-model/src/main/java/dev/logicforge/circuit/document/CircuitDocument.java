package dev.logicforge.circuit.document;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
        components.put(instance.id(), instance);
        boolean reconfigured = !previous.parameters().equals(instance.parameters());
        notifyListeners(new CircuitChange(reconfigured
                ? CircuitChange.Kind.COMPONENT_RECONFIGURED
                : CircuitChange.Kind.COMPONENT_MOVED, instance.id()));
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
        requireComponent(connection.from().componentId());
        requireComponent(connection.to().componentId());
        if (connections.putIfAbsent(connection.id(), connection) != null) {
            throw new IllegalStateException("Connection " + connection.id() + " already exists");
        }
        notifyListeners(new CircuitChange(CircuitChange.Kind.CONNECTION_ADDED, connection.id()));
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

    /** Every wire attached to any port of the given component. */
    public List<Connection> connectionsOf(UUID componentId) {
        return connections.values().stream().filter(connection -> connection.touches(componentId)).toList();
    }

    public boolean isConnected(PortReference a, PortReference b) {
        return connections.values().stream()
                .anyMatch(connection -> (connection.from().equals(a) && connection.to().equals(b))
                        || (connection.from().equals(b) && connection.to().equals(a)));
    }

    // ------------------------------------------------------------------
    // Bulk operations, listeners
    // ------------------------------------------------------------------

    /** An independent copy holding the same components and wires. Listeners are not copied. */
    public CircuitDocument copy() {
        CircuitDocument copy = new CircuitDocument(metadata);
        copy.components.putAll(components);
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
