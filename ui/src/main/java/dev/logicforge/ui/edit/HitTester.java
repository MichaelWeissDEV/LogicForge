package dev.logicforge.ui.edit;

import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.ComponentGeometry;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PlacedPort;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.geometry.CircuitBounds;
import dev.logicforge.circuit.geometry.CircuitPoint;
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
 * testing agrees with what is drawn by construction. The search is linear over the
 * document, which is plenty for the circuits this version is built for; the API takes the
 * visible area so a spatial index can be slid in later without changing callers.
 */
public final class HitTester {

    private final Supplier<CircuitDocument> documents;
    private final Function<String, Optional<ComponentDefinition>> definitions;
    private final WireRouter router;

    /**
     * @param documents the circuit to search; a supplier rather than a fixed reference so
     *                  that opening another project does not leave stale hit testing behind
     */
    public HitTester(Supplier<CircuitDocument> documents,
                     Function<String, Optional<ComponentDefinition>> definitions,
                     WireRouter router) {
        this.documents = documents;
        this.definitions = definitions;
        this.router = router;
    }

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

    /** The wire running closest to {@code point}, within {@code tolerance}. */
    public Optional<Connection> connectionAt(CircuitPoint point, double tolerance) {
        Connection best = null;
        double bestDistance = tolerance;
        for (Connection connection : document().connections()) {
            Optional<double[]> distance = distanceTo(connection, point);
            if (distance.isPresent() && distance.get()[0] <= bestDistance) {
                best = connection;
                bestDistance = distance.get()[0];
            }
        }
        return Optional.ofNullable(best);
    }

    private Optional<double[]> distanceTo(Connection connection, CircuitPoint point) {
        Optional<PlacedPort> from = dev.logicforge.circuit.document.ElectricalEndpoints
                .componentPort(connection.from()).flatMap(this::endpoint);
        Optional<PlacedPort> to = dev.logicforge.circuit.document.ElectricalEndpoints
                .componentPort(connection.to()).flatMap(this::endpoint);
        if (from.isEmpty() || to.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new double[]{
                router.route(from.get(), to.get(), connection.waypoints()).distanceTo(point)});
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

    /** Wires whose both ends belong to components inside the rectangle. */
    public List<UUID> connectionsIn(CircuitBounds area) {
        List<UUID> components = componentsIn(area);
        List<UUID> found = new ArrayList<>();
        for (Connection connection : document().connections()) {
            if (connection.from() instanceof dev.logicforge.circuit.document.ElectricalEndpoint.ComponentEndpoint from
                    && connection.to() instanceof dev.logicforge.circuit.document.ElectricalEndpoint.ComponentEndpoint to
                    && components.contains(from.port().componentId())
                    && components.contains(to.port().componentId())) {
                found.add(connection.id());
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

    private CircuitDocument document() {
        return documents.get();
    }

    private static List<ComponentInstance> reversed(java.util.Collection<ComponentInstance> components) {
        List<ComponentInstance> list = new ArrayList<>(components);
        java.util.Collections.reverse(list);
        return list;
    }
}
