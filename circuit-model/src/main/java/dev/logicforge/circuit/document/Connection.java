package dev.logicforge.circuit.document;

import dev.logicforge.circuit.geometry.CircuitPoint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A wire between two ports.
 *
 * <p>Electrically a connection is only a statement that two ports belong to the same net;
 * the compiler merges connections that share a port into one net. The optional waypoints
 * are pure presentation: they let the user route a wire by hand without changing what the
 * circuit does.
 */
public record Connection(UUID id, ElectricalEndpoint from, ElectricalEndpoint to, List<CircuitPoint> waypoints) {

    public Connection {
        if (id == null || from == null || to == null) {
            throw new IllegalArgumentException("Incomplete connection");
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("A connection cannot start and end at the same port");
        }
        waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
    }

    public static Connection create(PortReference from, PortReference to) {
        return new Connection(UUID.randomUUID(),
            new ElectricalEndpoint.ComponentEndpoint(PortEndpoint.whole(from)),
            new ElectricalEndpoint.ComponentEndpoint(PortEndpoint.whole(to)),
            List.of());
    }

    public static Connection create(PortEndpoint from, PortEndpoint to) {
        return new Connection(UUID.randomUUID(),
            new ElectricalEndpoint.ComponentEndpoint(from),
            new ElectricalEndpoint.ComponentEndpoint(to),
            List.of());
    }

    public static Connection create(ElectricalEndpoint from, ElectricalEndpoint to) {
        return new Connection(UUID.randomUUID(), from, to, List.of());
    }

    public boolean touches(UUID componentId) {
        return (from instanceof ElectricalEndpoint.ComponentEndpoint ce1 && ce1.port().componentId().equals(componentId))
            || (to instanceof ElectricalEndpoint.ComponentEndpoint ce2 && ce2.port().componentId().equals(componentId));
    }

    public boolean touches(PortReference port) {
        return (from instanceof ElectricalEndpoint.ComponentEndpoint ce1 && ce1.port().port().equals(port))
            || (to instanceof ElectricalEndpoint.ComponentEndpoint ce2 && ce2.port().port().equals(port));
    }

    public boolean touchesEndpoint(PortEndpoint endpoint) {
        return (from instanceof ElectricalEndpoint.ComponentEndpoint ce1 && ce1.port().equals(endpoint))
            || (to instanceof ElectricalEndpoint.ComponentEndpoint ce2 && ce2.port().equals(endpoint));
    }

    /** True if this connection touches any endpoint belonging to a physical chip package. */
    public boolean touchesChip(UUID chipId) {
        return (from instanceof ElectricalEndpoint.ChipPinEndpoint cp1 && cp1.chipInstanceId().equals(chipId))
            || (to instanceof ElectricalEndpoint.ChipPinEndpoint cp2 && cp2.chipInstanceId().equals(chipId));
    }

    public Connection withWaypoints(List<CircuitPoint> newWaypoints) {
        return new Connection(id, from, to, newWaypoints);
    }

    public Connection withId(UUID newId) {
        return new Connection(newId, from, to, waypoints);
    }

    public Optional<PortReference> fromPort() {
        return from instanceof ElectricalEndpoint.ComponentEndpoint ce ? Optional.of(ce.port().port()) : Optional.empty();
    }

    public Optional<PortReference> toPort() {
        return to instanceof ElectricalEndpoint.ComponentEndpoint ce ? Optional.of(ce.port().port()) : Optional.empty();
    }
}
