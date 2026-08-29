package dev.logicforge.ui.render;

import dev.logicforge.circuit.chip.ChipDefinition;
import dev.logicforge.circuit.chip.ChipGeometry;
import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.ComponentGeometry;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.ElectricalEndpoint;
import dev.logicforge.circuit.document.ElectricalEndpointGeometry;
import dev.logicforge.circuit.document.PlacedElectricalEndpoint;
import dev.logicforge.circuit.document.PlacedPort;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortDisplayMode;
import dev.logicforge.circuit.geometry.CircuitBounds;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.logic.LogicState;
import dev.logicforge.ui.edit.CircuitEditor;
import dev.logicforge.ui.viewport.Grid;
import dev.logicforge.ui.viewport.ViewportTransform;
import dev.logicforge.ui.wiring.WireRoute;
import dev.logicforge.ui.wiring.WireRouter;
import java.util.List;
import java.util.Optional;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * Draws the circuit onto a single canvas, in layers: grid, wires, components, overlay.
 *
 * <p>Everything is painted in circuit coordinates — the viewport transform is applied to
 * the graphics context once — so the renderer never converts coordinates itself. Only what
 * is inside the visible area is drawn.
 */
public final class CircuitRenderer {

    private static final double LABEL_OFFSET = 14;
    private static final double PORT_LABEL_ZOOM = 1.6;

    private final CircuitEditor editor;
    private final RendererRegistry renderers;
    private final WireRouter router;
    private final ChipPackageRenderer chipPackageRenderer = new ChipPackageRenderer();
    private final ChipSymbolRenderer chipSymbolRenderer = new ChipSymbolRenderer();

    public CircuitRenderer(CircuitEditor editor, RendererRegistry renderers, WireRouter router) {
        this.editor = editor;
        this.renderers = renderers;
        this.router = router;
    }

    public void render(GraphicsContext graphics, double width, double height,
                       ViewportTransform viewport, CanvasOverlay overlay) {
        graphics.setFill(Theme.CANVAS_BACKGROUND);
        graphics.fillRect(0, 0, width, height);

        CircuitBounds visible = viewport.visibleWorldBounds(width, height);
        drawGrid(graphics, width, height, viewport, visible);

        graphics.save();
        graphics.translate(viewport.translationX(), viewport.translationY());
        graphics.scale(viewport.scale(), viewport.scale());

        drawWires(graphics, visible);
        drawComponents(graphics, visible, viewport, overlay);
        drawChips(graphics, visible, viewport, overlay);
        drawOverlay(graphics, overlay, viewport);

        graphics.restore();
    }

    // ------------------------------------------------------------------ grid

    private void drawGrid(GraphicsContext graphics, double width, double height,
                          ViewportTransform viewport, CircuitBounds visible) {
        double scale = viewport.scale();
        boolean drawMinor = scale >= 0.7;
        double step = Grid.SPACING;
        double majorStep = Grid.SPACING * Grid.MAJOR_EVERY;

        graphics.setLineWidth(1);
        if (drawMinor) {
            graphics.setStroke(Theme.GRID_MINOR);
            strokeGridLines(graphics, viewport, visible, width, height, step, majorStep);
        }

        // Zoomed out enough that even the major tier would be sub-4px clutter: fall back to
        // a coarser multiple of it instead of drawing nothing, so the grid stays a usable
        // reference at any zoom level. MIN_SCALE bounds this to a handful of iterations.
        double coarseStep = majorStep;
        while (viewport.worldToScreenLength(coarseStep) < 4) {
            coarseStep *= Grid.MAJOR_EVERY;
        }
        graphics.setStroke(Theme.GRID_MAJOR);
        strokeGridLines(graphics, viewport, visible, width, height, coarseStep, 0);
    }

    /** Draws lines every {@code step} units, skipping those that a stronger line covers. */
    private void strokeGridLines(GraphicsContext graphics, ViewportTransform viewport,
                                 CircuitBounds visible, double width, double height,
                                 double step, double skipMultiplesOf) {
        if (viewport.worldToScreenLength(step) < 4) {
            return;
        }
        for (double x = Grid.firstLineAtOrAfter(visible.x()); x <= visible.maxX(); x += step) {
            if (skipMultiplesOf > 0 && Grid.isOnLine(x, skipMultiplesOf)) {
                continue;
            }
            double screenX = Math.floor(viewport.worldToScreen(new CircuitPoint(x, 0)).x()) + 0.5;
            graphics.strokeLine(screenX, 0, screenX, height);
        }
        for (double y = Grid.firstLineAtOrAfter(visible.y()); y <= visible.maxY(); y += step) {
            if (skipMultiplesOf > 0 && Grid.isOnLine(y, skipMultiplesOf)) {
                continue;
            }
            double screenY = Math.floor(viewport.worldToScreen(new CircuitPoint(0, y)).y()) + 0.5;
            graphics.strokeLine(0, screenY, width, screenY);
        }
    }

    // ----------------------------------------------------------------- wires

    private void drawWires(GraphicsContext graphics, CircuitBounds visible) {
        CircuitDocument document = editor.document();
        graphics.setLineWidth(Theme.WIRE_STROKE);

        for (Connection connection : document.connections()) {
            Optional<PlacedElectricalEndpoint> from = resolveEndpoint(connection.from());
            Optional<PlacedElectricalEndpoint> to = resolveEndpoint(connection.to());
            if (from.isEmpty() || to.isEmpty()) {
                continue;
            }
            WireRoute route = router.route(from.get(), to.get(), connection.waypoints());
            Optional<dev.logicforge.compiler.ResolvedSignal> signal = editor.signalOfConnection(connection.id());
            graphics.setStroke(wireColor(signal));

            int width = signal.map(dev.logicforge.compiler.ResolvedSignal::width).orElse(0);
            if (width > 1) {
                graphics.setLineWidth(Theme.WIRE_STROKE + 1.0);
            }
            
            if (editor.selection().containsConnection(connection.id())) {
                graphics.setLineWidth(Theme.WIRE_STROKE + 1.4);
                graphics.setStroke(Theme.SELECTION);
            }
            strokeRoute(graphics, route);
            
            if (!editor.selection().containsConnection(connection.id())) {
                drawBusWidthMarker(graphics, connection, route, width);
            }
            
            graphics.setLineWidth(Theme.WIRE_STROKE);
        }
        drawJunctions(graphics);
    }
    
    private void drawBusWidthMarker(GraphicsContext graphics, Connection connection, WireRoute route, int width) {
        if (width <= 1) return;
        java.util.List<CircuitPoint> points = route.points();
        if (points.size() < 2) return;
        
        double totalLen = 0;
        for (int i = 1; i < points.size(); i++) {
            double dx = points.get(i).x() - points.get(i-1).x();
            double dy = points.get(i).y() - points.get(i-1).y();
            totalLen += Math.sqrt(dx*dx + dy*dy);
        }
        
        double target = totalLen / 2;
        double walked = 0;
        CircuitPoint mid = points.get(points.size()-1);
        
        for (int i = 1; i < points.size(); i++) {
            double dx = points.get(i).x() - points.get(i-1).x();
            double dy = points.get(i).y() - points.get(i-1).y();
            double segLen = Math.sqrt(dx*dx + dy*dy);
            if (walked + segLen >= target) {
                double t = (target - walked) / segLen;
                mid = new CircuitPoint(points.get(i-1).x() + t*dx, points.get(i-1).y() + t*dy);
                break;
            }
            walked += segLen;
        }
        
        graphics.setFill(Theme.TEXT_MUTED);
        graphics.setFont(Font.font(Theme.PIN_LABEL_SIZE - 1));
        graphics.setTextAlign(TextAlignment.LEFT);
        graphics.setTextBaseline(VPos.CENTER);
        graphics.fillText("/" + width, mid.x() + 2, mid.y() - 4);
    }

    private Color wireColor(Optional<dev.logicforge.compiler.ResolvedSignal> signal) {
        if (signal.isEmpty()) {
            return Theme.WIRE_UNPOWERED;
        }
        if (editor.hasDriverConflict(signal.get())) {
            return Theme.SIGNAL_CONFLICT;
        }
        return editor.simulation().map(signal.get()::read)
                .map(value -> value.width() == 1
                        ? Theme.signalColor(value.getBit(0))
                        : Theme.busColor(value))
                .orElse(Theme.WIRE_UNPOWERED);
    }

    private void strokeRoute(GraphicsContext graphics, WireRoute route) {
        graphics.beginPath();
        CircuitPoint start = route.points().get(0);
        graphics.moveTo(start.x(), start.y());
        for (int i = 1; i < route.points().size(); i++) {
            graphics.lineTo(route.points().get(i).x(), route.points().get(i).y());
        }
        graphics.stroke();
    }

    /**
     * A filled dot marks ports where several wires of the same net meet. Wires that merely
     * cross get nothing, so a junction can never be mistaken for a crossing.
     */
    private void drawJunctions(GraphicsContext graphics) {
        CircuitDocument document = editor.document();
        for (ComponentInstance instance : document.components()) {
            Optional<ComponentDefinition> definition = editor.definitionOf(instance);
            if (definition.isEmpty()) {
                continue;
            }
            for (PlacedPort placed : ComponentGeometry.ports(instance, definition.get(), document)) {
                if (document.connectionsAt(placed.endpoint()).size() < 2) {
                    continue;
                }
                graphics.setFill(signalColorOf(placed.endpoint()));
                graphics.fillOval(placed.position().x() - Theme.JUNCTION_RADIUS,
                        placed.position().y() - Theme.JUNCTION_RADIUS,
                        Theme.JUNCTION_RADIUS * 2, Theme.JUNCTION_RADIUS * 2);
            }
        }
    }

    // ------------------------------------------------------------ components

    private void drawComponents(GraphicsContext graphics, CircuitBounds visible,
                                ViewportTransform viewport, CanvasOverlay overlay) {
        for (ComponentInstance instance : editor.document().components()) {
            Optional<ComponentDefinition> definition = editor.definitionOf(instance);
            if (definition.isEmpty()) {
                continue;
            }
            CircuitBounds bounds = ComponentGeometry.bodyBounds(instance, definition.get());
            if (!visible.grownBy(64).intersects(bounds)) {
                continue; // outside the viewport
            }
            drawComponent(graphics, instance, definition.get(), viewport, overlay, 1.0);
        }
    }

    /** Draws one component: its ports, its symbol and its label. */
    public void drawComponent(GraphicsContext graphics, ComponentInstance instance,
                              ComponentDefinition definition, ViewportTransform viewport,
                              CanvasOverlay overlay, double opacity) {
        boolean selected = editor.selection().containsComponent(instance.id());
        boolean hovered = instance.id().equals(overlay.hoveredComponent());
        
        // Use preview position if this component is being moved
        CircuitPoint effectivePosition = overlay.movingComponentPositions().getOrDefault(
                instance.id(), instance.position());

        graphics.save();
        graphics.setGlobalAlpha(opacity);

        drawPorts(graphics, instance, definition, viewport, overlay, effectivePosition);
        drawExpandedWholeBusFanout(graphics, instance, definition, effectivePosition);

        graphics.translate(effectivePosition.x(), effectivePosition.y());
        graphics.rotate(instance.rotation().degrees());
        renderers.rendererFor(instance.definitionId()).drawSymbol(graphics,
                new SymbolContext(instance, definition, ComponentGeometry.effectiveBodySize(instance, definition),
                        selected, hovered, portName -> valueOf(instance, portName)));
        graphics.restore();

        if (selected) {
            drawSelectionOutline(graphics, ComponentGeometry.bodyBounds(instance, definition));
        }
        drawLabel(graphics, instance, definition, opacity);
    }

    private void drawPorts(GraphicsContext graphics, ComponentInstance instance,
                           ComponentDefinition definition, ViewportTransform viewport,
                           CanvasOverlay overlay, CircuitPoint effectivePosition) {
        graphics.setLineWidth(Theme.WIRE_STROKE);
        for (PlacedPort placed : ComponentGeometry.ports(instance, definition, editor.document())) {
            // Ports are relative to component position, so we need to adjust
            CircuitPoint portPosRelative = placed.position();
            CircuitPoint portPosAbsolute = new CircuitPoint(
                    effectivePosition.x() + portPosRelative.x() - instance.position().x(),
                    effectivePosition.y() + portPosRelative.y() - instance.position().y());
            CircuitPoint outer = portPosAbsolute;
            CircuitPoint inner = new CircuitPoint(
                    outer.x() - dev.logicforge.library.PortLayout.PORT_STUB * directionX(placed.side()),
                    outer.y() - dev.logicforge.library.PortLayout.PORT_STUB * directionY(placed.side()));
            
            // Actually, placed.position() already returns absolute position
            // But we need to offset by the difference between effective and instance position
            double dx = effectivePosition.x() - instance.position().x();
            double dy = effectivePosition.y() - instance.position().y();
            outer = new CircuitPoint(placed.position().x() + dx, placed.position().y() + dy);
            inner = new CircuitPoint(inner.x() + dx, inner.y() + dy);
            
            graphics.setStroke(signalColorOf(placed.endpoint()));
            graphics.strokeLine(inner.x(), inner.y(), outer.x(), outer.y());

            boolean highlighted = overlay.hoveredPortOption()
                    .map(port -> port.endpoint().equals(placed.endpoint()))
                    .orElse(false);
            double radius = highlighted ? Theme.PORT_RADIUS * 1.8 : Theme.PORT_RADIUS;
            graphics.setFill(!placed.connectable() ? Theme.TEXT_MUTED
                    : highlighted ? Theme.PORT_HIGHLIGHT : Theme.PORT);
            graphics.fillOval(outer.x() - radius, outer.y() - radius, radius * 2, radius * 2);

            if (viewport.scale() >= PORT_LABEL_ZOOM) {
                drawPortName(graphics, placed, dx, dy);
            }
        }
    }

    /** Draws the local, presentation-only fan-out for an expanded port wired as one bus. */
    private void drawExpandedWholeBusFanout(GraphicsContext graphics, ComponentInstance instance,
                                            ComponentDefinition definition,
                                            CircuitPoint effectivePosition) {
        if (instance.portDisplayMode() != PortDisplayMode.EXPANDED) {
            return;
        }
        double dx = effectivePosition.x() - instance.position().x();
        double dy = effectivePosition.y() - instance.position().y();
        for (var spec : definition.ports(instance.parameters())) {
            if (spec.width().isSingleBit()) {
                continue;
            }
            PortReference reference = new PortReference(instance.id(), spec.name());
            boolean wholeConnected = editor.document().connectionsAt(reference).stream().anyMatch(connection ->
                    connection.from() instanceof dev.logicforge.circuit.document.ElectricalEndpoint.ComponentEndpoint from
                            && from.port().port().equals(reference) && from.port().isWhole()
                    || connection.to() instanceof dev.logicforge.circuit.document.ElectricalEndpoint.ComponentEndpoint to
                            && to.port().port().equals(reference) && to.port().isWhole());
            if (!wholeConnected) {
                continue;
            }
            PortEndpoint whole = PortEndpoint.whole(reference);
            Optional<PlacedPort> junction = ComponentGeometry.endpoint(
                    instance, definition, whole, editor.document());
            if (junction.isEmpty()) {
                continue;
            }
            CircuitPoint trunk = junction.get().position().plus(dx, dy);
            graphics.setStroke(signalColorOf(whole));
            List<PlacedPort> bits = ComponentGeometry.ports(instance, definition, editor.document()).stream()
                    .filter(bit -> bit.endpoint().port().equals(reference) && bit.endpoint().isBit())
                    .toList();
            if (bits.isEmpty()) {
                continue;
            }
            double min = bits.stream().mapToDouble(bit -> junction.get().side().isHorizontal()
                    ? bit.position().y() + dy : bit.position().x() + dx).min().orElse(0);
            double max = bits.stream().mapToDouble(bit -> junction.get().side().isHorizontal()
                    ? bit.position().y() + dy : bit.position().x() + dx).max().orElse(0);
            graphics.setLineWidth(Theme.WIRE_STROKE + 1.0);
            if (junction.get().side().isHorizontal()) {
                graphics.strokeLine(trunk.x(), min, trunk.x(), max);
            } else {
                graphics.strokeLine(min, trunk.y(), max, trunk.y());
            }
            graphics.setLineWidth(Theme.WIRE_STROKE);
            for (PlacedPort bit : bits) {
                CircuitPoint pin = bit.position().plus(dx, dy);
                CircuitPoint elbow = junction.get().side().isHorizontal()
                        ? new CircuitPoint(trunk.x(), pin.y())
                        : new CircuitPoint(pin.x(), trunk.y());
                graphics.strokeLine(pin.x(), pin.y(), elbow.x(), elbow.y());
            }
            graphics.setFill(Theme.PORT);
            graphics.fillOval(trunk.x() - Theme.JUNCTION_RADIUS, trunk.y() - Theme.JUNCTION_RADIUS,
                    Theme.JUNCTION_RADIUS * 2, Theme.JUNCTION_RADIUS * 2);
        }
    }
    
    private double directionX(dev.logicforge.circuit.geometry.PortSide side) {
        return side == dev.logicforge.circuit.geometry.PortSide.RIGHT ? 1 : 
               side == dev.logicforge.circuit.geometry.PortSide.LEFT ? -1 : 0;
    }
    
    private double directionY(dev.logicforge.circuit.geometry.PortSide side) {
        return side == dev.logicforge.circuit.geometry.PortSide.TOP ? -1 :
               side == dev.logicforge.circuit.geometry.PortSide.BOTTOM ? 1 : 0;
    }

    private void drawPortName(GraphicsContext graphics, PlacedPort placed, double dx, double dy) {
        graphics.setFill(Theme.TEXT_MUTED);
        graphics.setFont(Font.font(Theme.PIN_LABEL_SIZE));
        graphics.setTextBaseline(VPos.CENTER);
        CircuitPoint inside = placed.stubEnd(-dev.logicforge.library.PortLayout.PORT_STUB - 5);
        inside = new CircuitPoint(inside.x() + dx, inside.y() + dy);
        switch (placed.side()) {
            case LEFT -> {
                graphics.setTextAlign(TextAlignment.LEFT);
                graphics.fillText(placed.displayName(), inside.x() + 2, inside.y());
            }
            case RIGHT -> {
                graphics.setTextAlign(TextAlignment.RIGHT);
                graphics.fillText(placed.displayName(), inside.x() - 2, inside.y());
            }
            default -> {
                graphics.setTextAlign(TextAlignment.CENTER);
                graphics.fillText(placed.displayName(), inside.x(), inside.y());
            }
        }
    }

    private void drawSelectionOutline(GraphicsContext graphics, CircuitBounds bounds) {
        CircuitBounds outline = bounds.grownBy(4);
        graphics.setStroke(Theme.SELECTION);
        graphics.setLineWidth(1.2);
        graphics.setLineDashes(4, 3);
        graphics.strokeRoundRect(outline.x(), outline.y(), outline.width(), outline.height(), 4, 4);
        graphics.setLineDashes();
    }

    /** Labels are drawn upright, whatever rotation the component has. */
    private void drawLabel(GraphicsContext graphics, ComponentInstance instance,
                           ComponentDefinition definition, double opacity) {
        if (instance.label().isBlank()) {
            return;
        }
        CircuitBounds bounds = ComponentGeometry.bodyBounds(instance, definition);
        graphics.save();
        graphics.setGlobalAlpha(opacity);
        graphics.setFill(Theme.TEXT_SECONDARY);
        graphics.setFont(Font.font(Theme.LABEL_SIZE));
        graphics.setTextAlign(TextAlignment.CENTER);
        graphics.setTextBaseline(VPos.TOP);
        graphics.fillText(instance.label(), bounds.center().x(), bounds.maxY() + LABEL_OFFSET / 2);
        graphics.restore();
    }

    // --------------------------------------------------------------- overlay

    private void drawOverlay(GraphicsContext graphics, CanvasOverlay overlay,
                             ViewportTransform viewport) {
        if (overlay.previewWire() != null) {
            graphics.setStroke(Theme.PORT_HIGHLIGHT);
            graphics.setLineWidth(Theme.WIRE_STROKE);
            graphics.setLineDashes(5, 4);
            strokeRoute(graphics, overlay.previewWire());
            graphics.setLineDashes();
        }
        if (overlay.ghost() != null) {
            editor.definitionOf(overlay.ghost()).ifPresent(definition ->
                    drawComponent(graphics, overlay.ghost(), definition, viewport, CanvasOverlay.EMPTY, 0.45));
        }
        if (overlay.chipGhost() != null) {
            editor.chipDefinitionOf(overlay.chipGhost()).ifPresent(definition ->
                    drawChip(graphics, overlay.chipGhost(), definition, CanvasOverlay.EMPTY, 0.45));
        }
        if (overlay.selectionRectangle() != null) {
            CircuitBounds rectangle = overlay.selectionRectangle();
            graphics.setFill(Theme.SELECTION_FILL);
            graphics.setStroke(Theme.SELECTION);
            graphics.setLineWidth(1);
            graphics.fillRect(rectangle.x(), rectangle.y(), rectangle.width(), rectangle.height());
            graphics.strokeRect(rectangle.x(), rectangle.y(), rectangle.width(), rectangle.height());
        }
    }

    // ------------------------------------------------------------------ chips

    private void drawChips(GraphicsContext graphics, CircuitBounds visible, ViewportTransform viewport,
                           CanvasOverlay overlay) {
        for (ChipInstance instance : editor.document().chips()) {
            Optional<ChipDefinition> definition = editor.chipDefinitionOf(instance);
            if (definition.isEmpty()) {
                continue;
            }
            CircuitBounds bounds = ChipGeometry.bodyBounds(instance, definition.get().packageDefinition().type());
            if (!visible.grownBy(96).intersects(bounds)) {
                continue; // outside the viewport
            }
            drawChip(graphics, instance, definition.get(), overlay, 1.0);
        }
    }

    /** Draws one physical chip package: its body, its pins with live colors, and its label. */
    public void drawChip(GraphicsContext graphics, ChipInstance instance, ChipDefinition definition,
                         CanvasOverlay overlay, double opacity) {
        CircuitPoint effectivePosition = overlay.movingChipPositions().getOrDefault(instance.id(), instance.position());
        boolean selected = editor.selection().containsChip(instance.id());
        int hoveredPin = instance.id().equals(overlay.hoveredChip())
                ? overlay.hoveredChipPinOption()
                        .filter(pin -> pin.endpoint() instanceof ElectricalEndpoint.ChipPinEndpoint)
                        .map(pin -> ((ElectricalEndpoint.ChipPinEndpoint) pin.endpoint()).physicalPinNumber())
                        .orElse(-1)
                : -1;

        graphics.save();
        graphics.setGlobalAlpha(opacity);
        if (instance.displayMode() == dev.logicforge.circuit.chip.ChipDisplayMode.SYMBOL) {
            chipSymbolRenderer.draw(graphics, definition, instance.referenceDesignator(), effectivePosition,
                    instance.rotation(), pinNumber -> chipPinColor(instance, definition, pinNumber), hoveredPin,
                    this::unitDisplayName);
        } else {
            chipPackageRenderer.draw(graphics, definition, instance.referenceDesignator(), effectivePosition,
                    instance.rotation(), pinNumber -> chipPinColor(instance, definition, pinNumber), hoveredPin);
        }
        graphics.restore();

        if (selected) {
            drawSelectionOutline(graphics, ChipGeometry.bodyBounds(instance.withPosition(effectivePosition),
                    definition.packageDefinition().type()));
        }
    }

    private String unitDisplayName(String componentDefinitionId) {
        return editor.definition(componentDefinitionId)
                .map(ComponentDefinition::displayName)
                .orElseGet(() -> {
                    int dot = componentDefinitionId.lastIndexOf('.');
                    return dot < 0 ? componentDefinitionId : componentDefinitionId.substring(dot + 1);
                });
    }

    private Color chipPinColor(ChipInstance instance, ChipDefinition definition, int pinNumber) {
        Optional<dev.logicforge.circuit.chip.PackagePin> pin = definition.packageDefinition().pin(pinNumber);
        if (pin.isEmpty() || pin.get().electricalType() != dev.logicforge.circuit.chip.ElectricalPinType.SIGNAL) {
            return Theme.TEXT_MUTED;
        }
        ElectricalEndpoint endpoint = new ElectricalEndpoint.ChipPinEndpoint(instance.id(), pinNumber);
        Optional<dev.logicforge.compiler.ResolvedSignal> signal = editor.signalAt(endpoint);
        if (signal.isPresent() && editor.hasDriverConflict(signal.get())) {
            return Theme.SIGNAL_CONFLICT;
        }
        return editor.valueAt(endpoint)
                .map(value -> value.width() == 1 ? Theme.signalColor(value.getBit(0)) : Theme.busColor(value))
                .orElse(Theme.WIRE_UNPOWERED);
    }

    // --------------------------------------------------------------- helpers

    /** Resolves any electrical endpoint — a component port or a physical chip pin. */
    private Optional<PlacedElectricalEndpoint> resolveEndpoint(ElectricalEndpoint endpoint) {
        return ElectricalEndpointGeometry.resolve(editor.document(), endpoint, editor::definition,
                id -> editor.chipRegistry().find(id));
    }

    private Color signalColorOf(PortEndpoint endpoint) {
        Optional<dev.logicforge.compiler.ResolvedSignal> signal = editor.signalAt(endpoint);
        if (signal.isPresent() && editor.hasDriverConflict(signal.get())) {
            return Theme.SIGNAL_CONFLICT;
        }
        return editor.valueAt(endpoint)
                .map(value -> value.width() == 1
                        ? Theme.signalColor(value.getBit(0))
                        : Theme.busColor(value))
                .orElse(Theme.WIRE_UNPOWERED);
    }

    private LogicState valueOf(ComponentInstance instance, String portName) {
        return editor.valueAt(new PortReference(instance.id(), portName))
                .map(value -> value.getBit(0))
                .orElse(LogicState.HIGH_IMPEDANCE);
    }
}
