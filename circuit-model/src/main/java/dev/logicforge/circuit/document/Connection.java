package dev.logicforge.circuit.document;

import dev.logicforge.circuit.geometry.CircuitPoint;
import java.util.List;
import java.util.UUID;

/**
 * A wire between two ports.
 *
 * <p>Electrically a connection is only a statement that two ports belong to the same net;
 * the compiler merges connections that share a port into one net. The optional waypoints
 * are pure presentation: they let the user route a wire by hand without changing what the
 * circuit does.
 */
public record Connection(UUID id, PortEndpoint from, PortEndpoint to, List<CircuitPoint> waypoints) {

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
        return new Connection(UUID.randomUUID(), PortEndpoint.whole(from), PortEndpoint.whole(to), List.of());
    }

    public static Connection create(PortEndpoint from, PortEndpoint to) {
        return new Connection(UUID.randomUUID(), from, to, List.of());
    }

    public boolean touches(UUID componentId) {
        return from.componentId().equals(componentId) || to.componentId().equals(componentId);
    }

    public boolean touches(PortReference port) {
        return from.port().equals(port) || to.port().equals(port);
    }

    public boolean touchesEndpoint(PortEndpoint endpoint) {
        return from.equals(endpoint) || to.equals(endpoint);
    }

    /** True if this connection touches any endpoint belonging to a physical chip package. */
    public boolean touchesChip(UUID chipId) {
        // Will be implemented when ChipPinEndpoint is introduced
        return false;
    }

    public Connection withWaypoints(List<CircuitPoint> newWaypoints) {
        return new Connection(id, from, to, newWaypoints);
    }

    public Connection withId(UUID newId) {
        return new Connection(newId, from, to, waypoints);
    }

    public PortReference fromPort() {
        return from.port();
    }

    public PortReference toPort() {
        return to.port();
    }
}
