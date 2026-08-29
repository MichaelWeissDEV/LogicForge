package dev.logicforge.ui.edit;

import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.ElectricalEndpoint;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Copy and paste for circuit fragments.
 *
 * <p>A copy keeps the wires that run <em>between</em> the copied components/chips and drops
 * the ones that led elsewhere. Pasting hands out fresh ids to everything — components, chips
 * and the connections between them — so the copy is an independent circuit fragment rather
 * than a second reference to the original. A chip's physical pin numbers are part of its
 * definition and never change; only its instance id is remapped. Pasted chips get fresh
 * {@code U*} reference designators so they never collide with the originals.
 */
public final class CircuitClipboard {

    /** What was copied. Immutable, so it can be pasted any number of times. */
    public record Fragment(List<ComponentInstance> components, List<ChipInstance> chips,
                           List<Connection> connections) {

        public Fragment {
            components = List.copyOf(components);
            chips = List.copyOf(chips);
            connections = List.copyOf(connections);
        }

        public boolean isEmpty() {
            return components.isEmpty() && chips.isEmpty();
        }
    }

    private Fragment content = new Fragment(List.of(), List.of(), List.of());

    /** Copies the given components and chips, and every wire that runs between them. */
    public Fragment copy(CircuitDocument document, Collection<UUID> componentIds, Collection<UUID> chipIds) {
        List<ComponentInstance> components = new ArrayList<>();
        for (UUID id : componentIds) {
            document.component(id).ifPresent(components::add);
        }
        List<ChipInstance> chips = new ArrayList<>();
        for (UUID id : chipIds) {
            document.chip(id).ifPresent(chips::add);
        }
        List<Connection> internal = new ArrayList<>();
        for (Connection connection : document.connections()) {
            if (ownedBy(connection.from(), componentIds, chipIds) && ownedBy(connection.to(), componentIds, chipIds)) {
                internal.add(connection);
            }
        }
        content = new Fragment(components, chips, internal);
        return content;
    }

    private static boolean ownedBy(ElectricalEndpoint endpoint, Collection<UUID> componentIds,
                                   Collection<UUID> chipIds) {
        if (endpoint instanceof ElectricalEndpoint.ComponentEndpoint ce) {
            return componentIds.contains(ce.port().componentId());
        }
        if (endpoint instanceof ElectricalEndpoint.ChipPinEndpoint cp) {
            return chipIds.contains(cp.chipInstanceId());
        }
        return false;
    }

    public Fragment content() {
        return content;
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }

    /**
     * Prepares the clipboard content for insertion at an offset, with new ids throughout.
     * {@code targetDocument} supplies the existing chips so pasted designators never collide.
     */
    public Fragment prepareForPaste(CircuitDocument targetDocument, double offsetX, double offsetY) {
        return prepareForPaste(targetDocument, content, offsetX, offsetY);
    }

    public static Fragment prepareForPaste(CircuitDocument targetDocument, Fragment fragment,
                                           double offsetX, double offsetY) {
        Map<UUID, UUID> newComponentIds = new HashMap<>();
        List<ComponentInstance> components = new ArrayList<>(fragment.components().size());
        for (ComponentInstance original : fragment.components()) {
            UUID newId = UUID.randomUUID();
            newComponentIds.put(original.id(), newId);
            components.add(original.withId(newId).movedBy(offsetX, offsetY));
        }

        Map<UUID, UUID> newChipIds = new HashMap<>();
        List<ChipInstance> chips = new ArrayList<>(fragment.chips().size());
        String nextDesignator = ChipDesignators.next(targetDocument);
        int designatorSuffix = Integer.parseInt(nextDesignator.substring(1));
        for (ChipInstance original : fragment.chips()) {
            UUID newId = UUID.randomUUID();
            newChipIds.put(original.id(), newId);
            chips.add(new ChipInstance(newId, original.chipDefinitionId(),
                    original.position().plus(offsetX, offsetY), original.rotation(),
                    "U" + designatorSuffix++, original.displayMode()));
        }

        List<Connection> connections = new ArrayList<>(fragment.connections().size());
        for (Connection original : fragment.connections()) {
            Optional<ElectricalEndpoint> from = remap(original.from(), newComponentIds, newChipIds);
            Optional<ElectricalEndpoint> to = remap(original.to(), newComponentIds, newChipIds);
            if (from.isEmpty() || to.isEmpty()) {
                continue;
            }
            connections.add(new Connection(UUID.randomUUID(), from.get(), to.get(),
                    original.waypoints().stream().map(point -> point.plus(offsetX, offsetY)).toList()));
        }
        return new Fragment(components, chips, connections);
    }

    private static Optional<ElectricalEndpoint> remap(ElectricalEndpoint endpoint,
                                                       Map<UUID, UUID> newComponentIds, Map<UUID, UUID> newChipIds) {
        if (endpoint instanceof ElectricalEndpoint.ComponentEndpoint ce) {
            UUID newId = newComponentIds.get(ce.port().componentId());
            if (newId == null) {
                return Optional.empty();
            }
            PortEndpoint remapped = new PortEndpoint(
                    new PortReference(newId, ce.port().portName()), ce.port().slice());
            return Optional.of(new ElectricalEndpoint.ComponentEndpoint(remapped));
        }
        if (endpoint instanceof ElectricalEndpoint.ChipPinEndpoint cp) {
            UUID newId = newChipIds.get(cp.chipInstanceId());
            if (newId == null) {
                return Optional.empty();
            }
            return Optional.of(new ElectricalEndpoint.ChipPinEndpoint(newId, cp.physicalPinNumber()));
        }
        return Optional.empty();
    }
}
