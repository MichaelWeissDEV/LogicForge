package dev.logicforge.ui.edit;

import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PortReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Copy and paste for circuit fragments.
 *
 * <p>A copy keeps the wires that run <em>between</em> the copied components and drops the
 * ones that led elsewhere. Pasting hands out fresh ids to everything, so the copy is an
 * independent circuit fragment rather than a second reference to the original.
 */
public final class CircuitClipboard {

    /** What was copied. Immutable, so it can be pasted any number of times. */
    public record Fragment(List<ComponentInstance> components, List<Connection> connections) {

        public Fragment {
            components = List.copyOf(components);
            connections = List.copyOf(connections);
        }

        public boolean isEmpty() {
            return components.isEmpty();
        }
    }

    private Fragment content = new Fragment(List.of(), List.of());

    /** Copies the given components and every wire that runs between them. */
    public Fragment copy(CircuitDocument document, Collection<UUID> componentIds) {
        List<ComponentInstance> components = new ArrayList<>();
        for (UUID id : componentIds) {
            document.component(id).ifPresent(components::add);
        }
        List<Connection> internal = new ArrayList<>();
        for (Connection connection : document.connections()) {
            if (componentIds.contains(connection.from().componentId())
                    && componentIds.contains(connection.to().componentId())) {
                internal.add(connection);
            }
        }
        content = new Fragment(components, internal);
        return content;
    }

    public Fragment content() {
        return content;
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }

    /**
     * Prepares the clipboard content for insertion at an offset, with new ids throughout.
     */
    public Fragment prepareForPaste(double offsetX, double offsetY) {
        return prepareForPaste(content, offsetX, offsetY);
    }

    public static Fragment prepareForPaste(Fragment fragment, double offsetX, double offsetY) {
        Map<UUID, UUID> newIds = new HashMap<>();
        List<ComponentInstance> components = new ArrayList<>(fragment.components().size());
        for (ComponentInstance original : fragment.components()) {
            UUID newId = UUID.randomUUID();
            newIds.put(original.id(), newId);
            components.add(original.withId(newId).movedBy(offsetX, offsetY));
        }
        List<Connection> connections = new ArrayList<>(fragment.connections().size());
        for (Connection original : fragment.connections()) {
            UUID from = newIds.get(original.from().componentId());
            UUID to = newIds.get(original.to().componentId());
            if (from == null || to == null) {
                continue;
            }
            connections.add(new Connection(UUID.randomUUID(),
                    new PortReference(from, original.from().portName()),
                    new PortReference(to, original.to().portName()),
                    original.waypoints().stream()
                            .map(point -> point.plus(offsetX, offsetY))
                            .toList()));
        }
        return new Fragment(components, connections);
    }
}
