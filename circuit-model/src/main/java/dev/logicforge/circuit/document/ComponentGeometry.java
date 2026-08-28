package dev.logicforge.circuit.document;

import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.component.PortSpec;
import dev.logicforge.circuit.geometry.CircuitBounds;
import dev.logicforge.circuit.geometry.CircuitGrid;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.CircuitSize;
import dev.logicforge.circuit.geometry.PortSide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a placed component plus its definition into world-space geometry.
 *
 * <p>This is the single place where rotation is applied. The renderer, the hit testing and
 * the wire router all call it, so a rotated gate can never end up drawn in one place and
 * wired in another.
 */
public final class ComponentGeometry {

    /** Visible bit pins use two grid cells, matching the standard library's pin rhythm. */
    public static final double VISIBLE_PIN_SPACING = CircuitGrid.SPACING * 2;

    private ComponentGeometry() {
    }

    /** The component's body rectangle in world coordinates, rotation included. */
    public static CircuitBounds bodyBounds(ComponentInstance instance, ComponentDefinition definition) {
        CircuitSize size = instance.rotation().apply(effectiveBodySize(instance, definition));
        return CircuitBounds.around(instance.position(), size);
    }

    /**
     * The unrotated body size after accounting for every pin currently visible on each side.
     */
    public static CircuitSize effectiveBodySize(ComponentInstance instance,
                                                ComponentDefinition definition) {
        CircuitSize base = definition.bodySize(instance.parameters());
        if (instance.portDisplayMode() != PortDisplayMode.EXPANDED) {
            return base;
        }
        Map<PortSide, Integer> counts = visibleCounts(definition.ports(instance.parameters()));
        double requiredHeight = Math.max(requiredExtent(counts.get(PortSide.LEFT)),
                requiredExtent(counts.get(PortSide.RIGHT)));
        double requiredWidth = Math.max(requiredExtent(counts.get(PortSide.TOP)),
                requiredExtent(counts.get(PortSide.BOTTOM)));
        return new CircuitSize(Math.max(base.width(), requiredWidth),
                Math.max(base.height(), requiredHeight));
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
        if (instance.portDisplayMode() != PortDisplayMode.EXPANDED) {
            List<PlacedPort> compact = new ArrayList<>(specs.size());
            for (PortSpec spec : specs) {
                PortReference reference = new PortReference(instance.id(), spec.name());
                compact.add(new PlacedPort(PortEndpoint.whole(reference), spec,
                        portPosition(instance, spec), spec.side().rotatedBy(instance.rotation()),
                        !hasPartialConnection(document, reference)));
            }
            return compact;
        }

        CircuitSize base = definition.bodySize(instance.parameters());
        CircuitSize effective = effectiveBodySize(instance, definition);
        List<PlacedPort> placed = new ArrayList<>();
        for (PortSide side : PortSide.values()) {
            List<PortSpec> sideSpecs = specs.stream().filter(spec -> spec.side() == side)
                    .sorted(Comparator.comparingDouble(spec -> tangentCoordinate(spec.anchor(), side)))
                    .toList();
            int count = sideSpecs.stream().mapToInt(ComponentGeometry::visibleCount).sum();
            int row = 0;
            for (PortSpec spec : sideSpecs) {
                PortReference reference = new PortReference(instance.id(), spec.name());
                boolean hasWhole = hasWholeConnection(document, reference);
                int specPins = visibleCount(spec);
                for (int pin = 0; pin < specPins; pin++, row++) {
                    double tangent = (row - (count - 1) / 2.0) * VISIBLE_PIN_SPACING;
                    CircuitPoint local = expandedAnchor(spec, base, effective, tangent);
                    PortEndpoint endpoint = spec.width().isSingleBit()
                            ? PortEndpoint.whole(reference) : PortEndpoint.bit(reference, pin);
                    placed.add(new PlacedPort(endpoint, spec,
                            instance.position().plus(instance.rotation().apply(local)),
                            side.rotatedBy(instance.rotation()),
                            spec.width().isSingleBit() || !hasWhole));
                }
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
        CircuitPoint position = portPosition(instance, port);
        if (instance.portDisplayMode() == PortDisplayMode.EXPANDED) {
            List<PlacedPort> matching = ports(instance, definition, document).stream()
                    .filter(candidate -> candidate.reference().equals(endpoint.port())).toList();
            if (!matching.isEmpty()) {
                double x = matching.stream().mapToDouble(candidate -> candidate.position().x()).average()
                        .orElse(position.x());
                double y = matching.stream().mapToDouble(candidate -> candidate.position().y()).average()
                        .orElse(position.y());
                PortSide worldSide = port.side().rotatedBy(instance.rotation());
                CircuitPoint outward = worldSide.outwards();
                position = new CircuitPoint(x + outward.x() * CircuitGrid.SPACING * 2,
                        y + outward.y() * CircuitGrid.SPACING * 2);
            }
        }
        return Optional.of(new PlacedPort(endpoint, port, position,
                port.side().rotatedBy(instance.rotation()), false));
    }

    private static boolean hasWholeConnection(CircuitDocument document, PortReference reference) {
        return document != null && document.connectionsAt(reference).stream().anyMatch(connection ->
                (connection.from() instanceof ElectricalEndpoint.ComponentEndpoint ce1 && ce1.port().port().equals(reference) && ce1.port().isWhole())
                        || (connection.to() instanceof ElectricalEndpoint.ComponentEndpoint ce2 && ce2.port().port().equals(reference) && ce2.port().isWhole()));
    }

    private static boolean hasPartialConnection(CircuitDocument document, PortReference reference) {
        return document != null && document.connectionsAt(reference).stream().anyMatch(connection ->
                (connection.from() instanceof ElectricalEndpoint.ComponentEndpoint ce1 && ce1.port().port().equals(reference) && !ce1.port().isWhole())
                        || (connection.to() instanceof ElectricalEndpoint.ComponentEndpoint ce2 && ce2.port().port().equals(reference) && !ce2.port().isWhole()));
    }

    private static int visibleCount(PortSpec spec) {
        return spec.width().isSingleBit() ? 1 : spec.width().bits();
    }

    private static Map<PortSide, Integer> visibleCounts(List<PortSpec> specs) {
        Map<PortSide, Integer> counts = new EnumMap<>(PortSide.class);
        for (PortSide side : PortSide.values()) {
            counts.put(side, 0);
        }
        for (PortSpec spec : specs) {
            counts.compute(spec.side(), (side, count) -> count + visibleCount(spec));
        }
        return counts;
    }

    private static double requiredExtent(int visiblePins) {
        return visiblePins == 0 ? 0 : (visiblePins + 1) * VISIBLE_PIN_SPACING;
    }

    private static double tangentCoordinate(CircuitPoint anchor, PortSide side) {
        return side.isHorizontal() ? anchor.y() : anchor.x();
    }

    /** Keeps each pin stub length while moving its body edge out to the effective body. */
    private static CircuitPoint expandedAnchor(PortSpec spec, CircuitSize base,
                                               CircuitSize effective, double tangent) {
        if (spec.side().isHorizontal()) {
            double baseEdge = base.halfWidth();
            double stub = Math.max(0, Math.abs(spec.anchor().x()) - baseEdge);
            double x = (spec.side() == PortSide.LEFT ? -1 : 1) * (effective.halfWidth() + stub);
            return new CircuitPoint(x, tangent);
        }
        double baseEdge = base.halfHeight();
        double stub = Math.max(0, Math.abs(spec.anchor().y()) - baseEdge);
        double y = (spec.side() == PortSide.TOP ? -1 : 1) * (effective.halfHeight() + stub);
        return new CircuitPoint(tangent, y);
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
