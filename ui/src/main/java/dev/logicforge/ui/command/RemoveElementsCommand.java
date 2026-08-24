package dev.logicforge.ui.command;

import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Deletes components and wires together.
 *
 * <p>Removing a component takes its wires with it — the document guarantees that — so the
 * command records them and puts them back on undo. Nothing is ever left dangling.
 */
public final class RemoveElementsCommand implements CircuitCommand {

    private final CircuitDocument document;
    private final Set<UUID> componentIds;
    private final Set<UUID> connectionIds;
    private final List<ComponentInstance> removedComponents = new ArrayList<>();
    private final List<Connection> removedConnections = new ArrayList<>();

    public RemoveElementsCommand(CircuitDocument document, Collection<UUID> componentIds,
                                 Collection<UUID> connectionIds) {
        this.document = document;
        this.componentIds = new LinkedHashSet<>(componentIds);
        this.connectionIds = new LinkedHashSet<>(connectionIds);
    }

    @Override
    public String name() {
        return componentIds.size() + connectionIds.size() == 1 ? "Delete" : "Delete selection";
    }

    @Override
    public void execute() {
        removedComponents.clear();
        removedConnections.clear();
        for (UUID connectionId : connectionIds) {
            document.connection(connectionId).ifPresent(removedConnections::add);
        }
        for (UUID componentId : componentIds) {
            document.component(componentId).ifPresent(component -> {
                removedComponents.add(component);
                for (Connection attached : document.connectionsOf(componentId)) {
                    if (removedConnections.stream().noneMatch(known -> known.id().equals(attached.id()))) {
                        removedConnections.add(attached);
                    }
                }
            });
        }
        for (Connection connection : removedConnections) {
            document.connection(connection.id()).ifPresent(
                    existing -> document.removeConnection(existing.id()));
        }
        for (ComponentInstance component : removedComponents) {
            document.removeComponent(component.id());
        }
    }

    @Override
    public void undo() {
        for (ComponentInstance component : removedComponents) {
            document.addComponent(component);
        }
        for (Connection connection : removedConnections) {
            document.addConnection(connection);
        }
    }
}
