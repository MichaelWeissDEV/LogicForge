package dev.logicforge.ui.edit;

import dev.logicforge.circuit.chip.ChipDefinition;
import dev.logicforge.circuit.chip.ChipGeometry;
import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.chip.ElectricalPinType;
import dev.logicforge.circuit.chip.PlacedChipPin;
import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.ComponentGeometry;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.ElectricalEndpoint;
import dev.logicforge.circuit.document.ElectricalEndpointGeometry;
import dev.logicforge.circuit.document.PlacedElectricalEndpoint;
import dev.logicforge.circuit.document.PlacedPort;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.geometry.CircuitBounds;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.RoutablePoint;
import dev.logicforge.ui.wiring.WireRouter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Finds what is under the cursor.
 *
 * <p>Everything happens in circuit coordinates against the model's own geometry, so hit
 * testing agrees with what is drawn by construction — component ports through
 * {@code ComponentGeometry}, chip pins through {@code ChipGeometry}, exactly the geometry
 * {@code CircuitRenderer} draws from. The search is linear over the document, which is
 * plenty for the circuits this version is built for; the API takes the visible area so a
 * spatial index can be slid in later without changing callers.
 */
public final class HitTester {

    private final Supplier<CircuitDocument> documents;
    private final Function<String, Optional<ComponentDefinition>> definitions;
    private final Function<String, Optional<ChipDefinition>> chipDefinitions;
    private final WireRouter router;

    /**
     * @param documents the circuit to search; a supplier rather than a fixed reference so
     *                  that opening another project does not leave stale hit testing behind
     */
    public HitTester(Supplier<CircuitDocument> documents,
                     Function<String, Optional<ComponentDefinition>> definitions,
                     Function<String, Optional<ChipDefinition>> chipDefinitions,
                     WireRouter router) {
        this.documents = documents;
        this.definitions = definitions;
        this.chipDefinitions = chipDefinitions;
        this.router = router;
    }

    // ------------------------------------------------------------ components

    /** The port near {@code point}, if any. Ports win over component bodies. */
    public Optional<PlacedPort> portAt(CircuitPoint point, double tolerance) {
        PlacedPort best = null;
        double bestDistance = tolerance;
        for (ComponentInstance instance : reversed(document().components())) {
            Optional<ComponentDefinition> definition = definitions.apply(instance.definitionId());
            if (definition.isEmpty()) {
                continue;
            }
            for (PlacedPort port : ComponentGeometry.ports(instance, definition.get(), document())) {
                double distance = port.position().distanceTo(point);
                if (distance <= bestDistance) {
                    best = port;
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** The topmost component whose body contains {@code point}. */
    public Optional<ComponentInstance> componentAt(CircuitPoint point) {
        for (ComponentInstance instance : reversed(document().components())) {
            Optional<ComponentDefinition> definition = definitions.apply(instance.definitionId());
            if (definition.isPresent()
                    && ComponentGeometry.bodyBounds(instance, definition.get()).contains(point)) {
                return Optional.of(instance);
            }
        }
        return Optional.empty();
    }

    /** Components fully inside a rectangle, as a rubber-band selection collects them. */
    public List<UUID> componentsIn(CircuitBounds area) {
        List<UUID> found = new ArrayList<>();
        for (ComponentInstance instance : document().components()) {
            Optional<ComponentDefinition> definition = definitions.apply(instance.definitionId());
            if (definition.isPresent()
                    && area.contains(ComponentGeometry.bodyBounds(instance, definition.get()))) {
                found.add(instance.id());
            }
        }
        return found;
    }

    public Optional<PlacedPort> port(UUID componentId, String portName) {
        return document().component(componentId).flatMap(instance ->
                definitions.apply(instance.definitionId())
                        .flatMap(definition -> ComponentGeometry.port(instance, definition, portName)));
    }

    public Optional<PlacedPort> endpoint(PortEndpoint endpoint) {
        return document().component(endpoint.componentId()).flatMap(instance ->
                definitions.apply(instance.definitionId()).flatMap(definition ->
                        ComponentGeometry.endpoint(instance, definition, endpoint, document())));
    }

    // ------------------------------------------------------------------ chips

    /** The topmost chip whose package body contains {@code point}. */
    public Optional<ChipInstance> chipAt(CircuitPoint point) {
        for (ChipInstance instance : reversed(document().chips())) {
            Optional<ChipDefinition> definition = chipDefinitions.apply(instance.chipDefinitionId());
            if (definition.isPresent() && ChipGeometry
                    .bodyBounds(instance, definition.get().packageDefinition().type()).contains(point)) {
                return Optional.of(instance);
            }
        }
        return Optional.empty();
    }

    /** The physical chip pin nearest {@code point}, if within tolerance. */
    public Optional<PlacedElectricalEndpoint> chipPinAt(CircuitPoint point, double tolerance) {
        PlacedElectricalEndpoint best = null;
        double bestDistance = tolerance;
        for (ChipInstance instance : reversed(document().chips())) {
            Optional<ChipDefinition> definition = chipDefinitions.apply(instance.chipDefinitionId());
            if (definition.isEmpty()) {
                continue;
            }
            for (PlacedChipPin pin : ChipGeometry.pins(instance, definition.get().packageDefinition())) {
                double distance = pin.position().distanceTo(point);
                if (distance <= bestDistance) {
                    best = toPlaced(instance, pin);
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Chips fully inside a rectangle, as a rubber-band selection collects them. */
    public List<UUID> chipsIn(CircuitBounds area) {
        List<UUID> found = new ArrayList<>();
        for (ChipInstance instance : document().chips()) {
            Optional<ChipDefinition> definition = chipDefinitions.apply(instance.chipDefinitionId());
            if (definition.isPresent() && area.contains(
                    ChipGeometry.bodyBounds(instance, definition.get().packageDefinition().type()))) {
                found.add(instance.id());
            }
        }
        return found;
    }

    private static PlacedElectricalEndpoint toPlaced(ChipInstance instance, PlacedChipPin pin) {
        return new PlacedElectricalEndpoint(
                new ElectricalEndpoint.ChipPinEndpoint(instance.id(), pin.number()),
                pin.position(), pin.side(), pin.electricalType() == ElectricalPinType.SIGNAL,
                instance.referenceDesignator() + "." + pin.name());
    }

    // ------------------------------------------------------- unified endpoints

    /**
     * The connectable electrical endpoint nearest {@code point} — a component port or a
     * chip pin, whichever is closer. Component ports win ties, matching {@link #portAt}'s
     * existing "ports win over bodies" precedence.
     */
    public Optional<PlacedElectricalEndpoint> endpointAt(CircuitPoint point, double tolerance) {
        Optional<PlacedPort> port = portAt(point, tolerance);
        Optional<PlacedElectricalEndpoint> chipPin = chipPinAt(point, tolerance);
        if (port.isEmpty()) {
            return chipPin;
        }
        if (chipPin.isEmpty()) {
            return Optional.of(asPlaced(port.get()));
        }
        double portDistance = port.get().position().distanceTo(point);
        double pinDistance = chipPin.get().position().distanceTo(point);
        return pinDistance < portDistance ? chipPin : Optional.of(asPlaced(port.get()));
    }

    private static PlacedElectricalEndpoint asPlaced(PlacedPort port) {
        return new PlacedElectricalEndpoint(new ElectricalEndpoint.ComponentEndpoint(port.endpoint()),
                port.position(), port.side(), port.connectable(), port.displayName());
    }

    /** Resolves any electrical endpoint — component port or chip pin — into world coordinates. */
    public Optional<PlacedElectricalEndpoint> resolve(ElectricalEndpoint endpoint) {
        return ElectricalEndpointGeometry.resolve(document(), endpoint, definitions, chipDefinitions);
    }

    // ------------------------------------------------------------- connections

    /** The wire running closest to {@code point}, within {@code tolerance}. */
    public Optional<Connection> connectionAt(CircuitPoint point, double tolerance) {
        Connection best = null;
        double bestDistance = tolerance;
        for (Connection connection : document().connections()) {
            Optional<Double> distance = distanceTo(connection, point);
            if (distance.isPresent() && distance.get() <= bestDistance) {
                best = connection;
                bestDistance = distance.get();
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The distance from {@code point} to a connection's route, resolved for any combination
     * of component ports and chip pins: component↔component, component↔chip, chip↔component
     * or chip↔chip.
     */
    private Optional<Double> distanceTo(Connection connection, CircuitPoint point) {
        Optional<PlacedElectricalEndpoint> from = resolve(connection.from());
        Optional<PlacedElectricalEndpoint> to = resolve(connection.to());
        if (from.isEmpty() || to.isEmpty()) {
            return Optional.empty();
        }
        RoutablePoint fromPoint = from.get();
        RoutablePoint toPoint = to.get();
        return Optional.of(router.route(fromPoint, toPoint, connection.waypoints()).distanceTo(point));
    }

    /** Wires whose both ends belong to components or chips inside the rectangle. */
    public List<UUID> connectionsIn(CircuitBounds area) {
        List<UUID> owners = new ArrayList<>(componentsIn(area));
        owners.addAll(chipsIn(area));
        List<UUID> found = new ArrayList<>();
        for (Connection connection : document().connections()) {
            Optional<UUID> from = ownerOf(connection.from());
            Optional<UUID> to = ownerOf(connection.to());
            if (from.isPresent() && to.isPresent() && owners.contains(from.get()) && owners.contains(to.get())) {
                found.add(connection.id());
            }
        }
        return found;
    }

    private static Optional<UUID> ownerOf(ElectricalEndpoint endpoint) {
        if (endpoint instanceof ElectricalEndpoint.ComponentEndpoint component) {
            return Optional.of(component.port().componentId());
        }
        if (endpoint instanceof ElectricalEndpoint.ChipPinEndpoint chipPin) {
            return Optional.of(chipPin.chipInstanceId());
        }
        return Optional.empty();
    }

    // ----------------------------------------------------------------- shared

    private CircuitDocument document() {
        return documents.get();
    }

    private static <T> List<T> reversed(java.util.Collection<T> items) {
        List<T> list = new ArrayList<>(items);
        java.util.Collections.reverse(list);
        return list;
    }
}
