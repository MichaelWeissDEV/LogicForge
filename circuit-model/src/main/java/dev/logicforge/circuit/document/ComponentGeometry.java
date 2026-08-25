package dev.logicforge.circuit.document;

import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.component.PortSpec;
import dev.logicforge.circuit.geometry.CircuitBounds;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.CircuitSize;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns a placed component plus its definition into world-space geometry.
 *
 * <p>This is the single place where rotation is applied. The renderer, the hit testing and
 * the wire router all call it, so a rotated gate can never end up drawn in one place and
 * wired in another.
 */
public final class ComponentGeometry {

    private ComponentGeometry() {
    }

    /** The component's body rectangle in world coordinates, rotation included. */
    public static CircuitBounds bodyBounds(ComponentInstance instance, ComponentDefinition definition) {
        CircuitSize size = instance.rotation().apply(definition.bodySize(instance.parameters()));
        return CircuitBounds.around(instance.position(), size);
    }

    /** The world position of one port. */
    public static CircuitPoint portPosition(ComponentInstance instance, PortSpec port) {
        return instance.position().plus(instance.rotation().apply(port.anchor()));
    }

    /** All ports of the component, resolved into world coordinates. */
    public static List<PlacedPort> ports(ComponentInstance instance, ComponentDefinition definition) {
        return ports(instance, definition, null);
    }

    /** Visible pins, expanded into individual bit endpoints when requested. */
    public static List<PlacedPort> ports(ComponentInstance instance, ComponentDefinition definition,
                                         CircuitDocument document) {
        List<PortSpec> specs = definition.ports(instance.parameters());
        List<PlacedPort> placed = new ArrayList<>();
        for (PortSpec spec : specs) {
            PortReference reference = new PortReference(instance.id(), spec.name());
            boolean hasWhole = hasWholeConnection(document, reference);
            boolean hasBits = hasBitConnection(document, reference);
            boolean expanded = instance.portDisplayMode() == PortDisplayMode.EXPANDED
                    && !spec.width().isSingleBit();
            if (!expanded) {
                placed.add(new PlacedPort(PortEndpoint.whole(reference), spec,
                        portPosition(instance, spec), spec.side().rotatedBy(instance.rotation()),
                        !hasBits));
                continue;
            }
            int width = spec.width().bits();
            for (int bit = 0; bit < width; bit++) {
                double offset = (bit - (width - 1) / 2.0) * 12.0;
                CircuitPoint tangent = spec.side().isHorizontal()
                        ? new CircuitPoint(0, offset) : new CircuitPoint(offset, 0);
                CircuitPoint local = spec.anchor().plus(tangent);
                placed.add(new PlacedPort(PortEndpoint.bit(reference, bit), spec,
                        instance.position().plus(instance.rotation().apply(local)),
                        spec.side().rotatedBy(instance.rotation()), !hasWhole));
            }
        }
        return placed;
    }

    public static Optional<PlacedPort> port(ComponentInstance instance, ComponentDefinition definition,
                                            String portName) {
        return ports(instance, definition).stream()
                .filter(port -> port.spec().name().equals(portName))
                .findFirst();
    }

    /** Resolves a stored electrical endpoint, including hidden compact/expanded anchors. */
    public static Optional<PlacedPort> endpoint(ComponentInstance instance,
                                                ComponentDefinition definition,
                                                PortEndpoint endpoint,
                                                CircuitDocument document) {
        Optional<PortSpec> spec = definition.ports(instance.parameters()).stream()
                .filter(candidate -> candidate.name().equals(endpoint.portName())).findFirst();
        if (spec.isEmpty()) {
            return Optional.empty();
        }
        for (PlacedPort placed : ports(instance, definition, document)) {
            if (placed.endpoint().equals(endpoint)) {
                return Optional.of(placed);
            }
        }
        PortSpec port = spec.get();
        return Optional.of(new PlacedPort(endpoint, port, portPosition(instance, port),
                port.side().rotatedBy(instance.rotation()), false));
    }

    private static boolean hasWholeConnection(CircuitDocument document, PortReference reference) {
        return document != null && document.connectionsAt(reference).stream().anyMatch(connection ->
                (connection.fromPort().equals(reference) && connection.from().isWhole())
                        || (connection.toPort().equals(reference) && connection.to().isWhole()));
    }

    private static boolean hasBitConnection(CircuitDocument document, PortReference reference) {
        return document != null && document.connectionsAt(reference).stream().anyMatch(connection ->
                (connection.fromPort().equals(reference) && connection.from().isBit())
                        || (connection.toPort().equals(reference) && connection.to().isBit()));
    }

    /**
     * The bounds a component occupies including its port stubs — used for selection
     * rectangles and for keeping wires out of component bodies.
     */
    public static CircuitBounds outerBounds(ComponentInstance instance, ComponentDefinition definition) {
        CircuitBounds bounds = bodyBounds(instance, definition);
        for (PlacedPort port : ports(instance, definition)) {
            bounds = bounds.union(new CircuitBounds(port.position().x(), port.position().y(), 0, 0));
        }
        return bounds;
    }
}
