package dev.logicforge.ui.edit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What the user currently has selected. Editor state, not circuit state — it is never
 * saved.
 */
public final class SelectionModel {

    private final Set<UUID> components = new LinkedHashSet<>();
    private final Set<UUID> connections = new LinkedHashSet<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public Set<UUID> components() {
        return Collections.unmodifiableSet(components);
    }

    public Set<UUID> connections() {
        return Collections.unmodifiableSet(connections);
    }

    public boolean isEmpty() {
        return components.isEmpty() && connections.isEmpty();
    }

    public int size() {
        return components.size() + connections.size();
    }

    public boolean containsComponent(UUID id) {
        return components.contains(id);
    }

    public boolean containsConnection(UUID id) {
        return connections.contains(id);
    }

    /** Replaces the selection with a single component. */
    public void selectComponent(UUID id) {
        components.clear();
        connections.clear();
        components.add(id);
        notifyListeners();
    }

    public void selectConnection(UUID id) {
        components.clear();
        connections.clear();
        connections.add(id);
        notifyListeners();
    }

    /** Adds or removes one component, as shift-clicking does. */
    public void toggleComponent(UUID id) {
        if (!components.remove(id)) {
            components.add(id);
        }
        notifyListeners();
    }

    public void toggleConnection(UUID id) {
        if (!connections.remove(id)) {
            connections.add(id);
        }
        notifyListeners();
    }

    public void setSelection(Collection<UUID> componentIds, Collection<UUID> connectionIds) {
        components.clear();
        connections.clear();
        components.addAll(componentIds);
        connections.addAll(connectionIds);
        notifyListeners();
    }

    public void addAll(Collection<UUID> componentIds, Collection<UUID> connectionIds) {
        components.addAll(componentIds);
        connections.addAll(connectionIds);
        notifyListeners();
    }

    public void clear() {
        if (isEmpty()) {
            return;
        }
        components.clear();
        connections.clear();
        notifyListeners();
    }

    /** Drops ids that are no longer in the document, e.g. after an undo. */
    public void retainExisting(Collection<UUID> existingComponents, Collection<UUID> existingConnections) {
        boolean changed = components.retainAll(existingComponents);
        changed |= connections.retainAll(existingConnections);
        if (changed) {
            notifyListeners();
        }
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }
}
