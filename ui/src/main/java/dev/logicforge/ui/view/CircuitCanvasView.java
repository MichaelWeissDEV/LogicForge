package dev.logicforge.ui.view;

import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.ElectricalEndpoint;
import dev.logicforge.circuit.document.PlacedElectricalEndpoint;
import dev.logicforge.circuit.document.PlacedPort;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.geometry.CircuitBounds;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.Rotation;
import dev.logicforge.logic.LogicState;
import dev.logicforge.ui.command.AddChipCommand;
import dev.logicforge.ui.command.AddComponentCommand;
import dev.logicforge.ui.command.CircuitCommand;
import dev.logicforge.ui.command.CompositeCommand;
import dev.logicforge.ui.command.ConnectCommand;
import dev.logicforge.ui.command.MoveChipsCommand;
import dev.logicforge.ui.command.MoveComponentsCommand;
import dev.logicforge.ui.command.PasteCommand;
import dev.logicforge.ui.command.RemoveElementsCommand;
import dev.logicforge.ui.command.RotateChipsCommand;
import dev.logicforge.ui.command.RotateComponentsCommand;
import dev.logicforge.ui.edit.CircuitClipboard;
import dev.logicforge.ui.edit.CircuitEditor;
import dev.logicforge.ui.edit.HitTester;
import dev.logicforge.ui.edit.PlacementRequest;
import dev.logicforge.ui.render.CanvasOverlay;
import dev.logicforge.ui.render.CircuitRenderer;
import dev.logicforge.ui.render.RendererRegistry;
import dev.logicforge.ui.viewport.Grid;
import dev.logicforge.ui.viewport.ViewportTransform;
import dev.logicforge.ui.wiring.OrthogonalWireRouter;
import dev.logicforge.ui.wiring.WireRouter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;

/**
 * The circuit canvas: drawing, hit testing and every direct manipulation gesture.
 *
 * <p>The view owns only view state — the viewport, what the mouse is over, what is being
 * dragged. Every change to the circuit itself goes through a command on the
 * {@link CircuitEditor}, which is what makes undo work uniformly and keeps this class an
 * event translator rather than a second model. Components and physical chips go through the
 * same gestures throughout: click/shift-click/rubber-band selection, drag-move, rotate,
 * delete, copy/paste/duplicate and wiring all operate on whichever combination of the two is
 * selected.
 */
public final class CircuitCanvasView extends Region {

    /** How close to a port or chip pin the cursor has to be, in pixels, to grab it. */
    private static final double PORT_TOLERANCE_PIXELS = 12;
    private static final double WIRE_TOLERANCE_PIXELS = 6;
    private static final double DRAG_THRESHOLD_PIXELS = 3;
    private static final double PASTE_OFFSET = Grid.SPACING * 2;

    private enum Mode {
        IDLE, PANNING, RUBBER_BAND, MOVING, WIRING
    }

    private final CircuitEditor editor;
    private final boolean readOnly;
    private final Canvas canvas = new Canvas();
    private final ViewportTransform viewport = new ViewportTransform();
    private final WireRouter router = new OrthogonalWireRouter();
    private final CircuitRenderer renderer;
    private final HitTester hitTester;
    private final CanvasContextMenu contextMenu = new CanvasContextMenu(this);
    private final javafx.scene.control.Tooltip portTooltip = new javafx.scene.control.Tooltip();
    private final DropTarget dropTarget;

    private CanvasOverlay overlay = CanvasOverlay.EMPTY;
    private Mode mode = Mode.IDLE;
    private CircuitPoint dragStartWorld;
    private double lastScreenX;
    private double lastScreenY;
    private boolean dragExceededThreshold;
    private List<ComponentInstance> movedComponentsBefore = List.of();
    private List<ChipInstance> movedChipsBefore = List.of();
    private PlacementRequest pendingPlacement;
    private Runnable statusListener = () -> {
    };
    private java.util.function.Consumer<ElectricalEndpoint> analyzerListener = endpoint -> {
    };
    private java.util.function.Consumer<ComponentInstance> hierarchyOpenListener = instance -> {
    };

    // For momentary button handling: track which component is being pressed
    private UUID pressedComponentId = null;

    public CircuitCanvasView(CircuitEditor editor) {
        this(editor, false, true);
    }

    /** Creates a canvas that can retain navigation/selection while suppressing edits. */
    public CircuitCanvasView(CircuitEditor editor, boolean readOnly) {
        this(editor, readOnly, true);
    }

    /** Allows a throttled owner (Study) to drive dynamic redraws explicitly. */
    public CircuitCanvasView(CircuitEditor editor, boolean readOnly, boolean automaticRedraw) {
        this.editor = editor;
        this.readOnly = readOnly;
        this.renderer = new CircuitRenderer(editor, RendererRegistry.standard(), router);
        this.hitTester = new HitTester(editor::document, editor::definition,
                id -> editor.chipRegistry().find(id), router);
        this.dropTarget = new DropTarget(editor, viewport);

        getChildren().add(canvas);
        setFocusTraversable(true);
        if (automaticRedraw) {
            editor.addChangeListener(this::redraw);
        }
        editor.selection().addListener(this::redraw);
        portTooltip.setShowDelay(javafx.util.Duration.millis(350));
        javafx.scene.control.Tooltip.install(this, portTooltip);
        installMouseHandlers();
        if (!readOnly) {
            installKeyHandlers();
            installDragAndDrop();
        }
        viewport.panBy(120, 80);
    }

    public ViewportTransform viewport() {
        return viewport;
    }

    /** Called whenever something the status bar shows may have changed. */
    public void setStatusListener(Runnable listener) {
        this.statusListener = listener;
    }

    /** Called with the port or chip pin a user picked "Add to Logic Analyzer" for. */
    public void setAnalyzerListener(java.util.function.Consumer<ElectricalEndpoint> listener) {
        this.analyzerListener = listener;
    }

    public void setHierarchyOpenListener(java.util.function.Consumer<ComponentInstance> listener) {
        this.hierarchyOpenListener = listener;
    }

    /** Arms click-to-place: the next click on the canvas drops this component or chip. */
    public void setPendingPlacement(PlacementRequest request) {
        this.pendingPlacement = request;
        setCursor(request == null ? Cursor.DEFAULT : Cursor.CROSSHAIR);
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        redraw();
    }

    public void redraw() {
        renderer.render(canvas.getGraphicsContext2D(), getWidth(), getHeight(), viewport, overlay);
        statusListener.run();
    }

    // ------------------------------------------------------------------ zoom

    public void zoomIn() {
        viewport.zoomBy(1.2, getWidth() / 2, getHeight() / 2);
        redraw();
    }

    public void zoomOut() {
        viewport.zoomBy(1 / 1.2, getWidth() / 2, getHeight() / 2);
        redraw();
    }

    public void zoomToFit() {
        CircuitBounds content = contentBounds();
        if (content == null) {
            viewport.reset();
            viewport.panBy(getWidth() / 2, getHeight() / 2);
        } else {
            viewport.fit(content.grownBy(48), getWidth(), getHeight());
        }
        redraw();
    }

    public void resetZoom() {
        double centreX = getWidth() / 2;
        double centreY = getHeight() / 2;
        viewport.setScaleAround(1.0, centreX, centreY);
        redraw();
    }

    private CircuitBounds contentBounds() {
        CircuitBounds bounds = null;
        for (ComponentInstance instance : editor.document().components()) {
            Optional<ComponentDefinition> definition = editor.definitionOf(instance);
            if (definition.isEmpty()) {
                continue;
            }
            CircuitBounds body = dev.logicforge.circuit.document.ComponentGeometry
                    .bodyBounds(instance, definition.get());
            bounds = bounds == null ? body : bounds.union(body);
        }
        for (ChipInstance instance : editor.document().chips()) {
            Optional<dev.logicforge.circuit.chip.ChipDefinition> definition = editor.chipDefinitionOf(instance);
            if (definition.isEmpty()) {
                continue;
            }
            CircuitBounds body = dev.logicforge.circuit.chip.ChipGeometry
                    .bodyBounds(instance, definition.get().packageDefinition().type());
            bounds = bounds == null ? body : bounds.union(body);
        }
        return bounds;
    }

    // ----------------------------------------------------------- mouse input

    private void installMouseHandlers() {
        setOnMousePressed(this::onMousePressed);
        setOnMouseDragged(this::onMouseDragged);
        setOnMouseReleased(this::onMouseReleased);
        setOnMouseMoved(this::onMouseMoved);
        setOnScroll(this::onScroll);
        setOnZoom(event -> {
            viewport.zoomBy(event.getZoomFactor(), event.getX(), event.getY());
            redraw();
        });
        setOnMouseExited(this::onMouseExited);
    }

    private void onMouseExited(MouseEvent event) {
        // If a momentary button is pressed and mouse exits, release it
        if (pressedComponentId != null) {
            editor.handleInputInteraction(pressedComponentId, false);
            pressedComponentId = null;
            redraw();
        }
    }

    private void onMousePressed(MouseEvent event) {
        requestFocus();
        contextMenu.hide();
        lastScreenX = event.getX();
        lastScreenY = event.getY();
        dragStartWorld = viewport.screenToWorld(event.getX(), event.getY());
        dragExceededThreshold = false;

        if (event.getButton() == MouseButton.SECONDARY) {
            if (!readOnly) {
                showContextMenu(event);
            }
            return;
        }
        if (event.getButton() == MouseButton.MIDDLE || event.isAltDown()) {
            mode = Mode.PANNING;
            setCursor(Cursor.MOVE);
            return;
        }
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        if (!readOnly && pendingPlacement != null) {
            CircuitPoint snapped = Grid.snap(dragStartWorld);
            switch (pendingPlacement) {
                case PlacementRequest.Component request -> place(dropTarget.componentAt(request.definitionId(), snapped));
                case PlacementRequest.Chip request -> placeChip(dropTarget.chipAt(request.chipDefinitionId(), snapped));
            }
            setPendingPlacement(null);
            return;
        }

        Optional<PlacedElectricalEndpoint> endpoint =
                hitTester.endpointAt(dragStartWorld, worldTolerance(PORT_TOLERANCE_PIXELS));
        if (endpoint.isPresent() && endpoint.get().connectable()) {
            if (readOnly) {
                return;
            }
            mode = Mode.WIRING;
            overlay = overlay.withPreviewWire(endpoint.get(),
                    router.routeToPoint(endpoint.get(), Grid.snap(dragStartWorld)));
            redraw();
            return;
        }

        Optional<ComponentInstance> component = hitTester.componentAt(dragStartWorld);
        if (component.isPresent()) {
            if (event.getClickCount() == 2
                    && dev.logicforge.circuit.document.SubcircuitSupport
                    .isInstanceDefinition(component.get().definitionId())) {
                hierarchyOpenListener.accept(component.get());
                mode = Mode.IDLE;
                return;
            }
            if (readOnly) {
                if (isMultiSelect(event)) {
                    editor.selection().toggleComponent(component.get().id());
                } else {
                    editor.selection().selectComponent(component.get().id());
                }
                mode = Mode.IDLE;
                redraw();
                return;
            }
            // Check if this is a momentary button - start tracking press
            var interaction = editor.inputInteraction(component.get().id());
            if (interaction == dev.logicforge.circuit.component.InputInteraction.MOMENTARY) {
                pressedComponentId = component.get().id();
                editor.handleInputInteraction(pressedComponentId, true);
            }
            beginComponentInteraction(component.get(), event);
            return;
        }

        Optional<ChipInstance> chip = hitTester.chipAt(dragStartWorld);
        if (chip.isPresent()) {
            if (readOnly) {
                if (isMultiSelect(event)) {
                    editor.selection().toggleChip(chip.get().id());
                } else {
                    editor.selection().selectChip(chip.get().id());
                }
                mode = Mode.IDLE;
                redraw();
                return;
            }
            beginChipInteraction(chip.get(), event);
            return;
        }

        Optional<Connection> wire = hitTester.connectionAt(dragStartWorld,
                worldTolerance(WIRE_TOLERANCE_PIXELS));
        if (wire.isPresent()) {
            if (isMultiSelect(event)) {
                editor.selection().toggleConnection(wire.get().id());
            } else {
                editor.selection().selectConnection(wire.get().id());
            }
            redraw();
            return;
        }

        if (!isMultiSelect(event)) {
            editor.selection().clear();
        }
        mode = Mode.RUBBER_BAND;
        redraw();
    }

    private void beginComponentInteraction(ComponentInstance component, MouseEvent event) {
        if (isMultiSelect(event)) {
            editor.selection().toggleComponent(component.id());
        } else if (!editor.selection().containsComponent(component.id())) {
            editor.selection().selectComponent(component.id());
        }

        // For momentary buttons, don't start a drag - they are handled by press/release
        var interaction = editor.inputInteraction(component.id());
        if (interaction == dev.logicforge.circuit.component.InputInteraction.MOMENTARY) {
            mode = Mode.IDLE; // Don't enter MOVING mode for momentary buttons
            movedComponentsBefore = List.of();
            movedChipsBefore = List.of();
            return;
        }
        beginMove();
    }

    private void beginChipInteraction(ChipInstance chip, MouseEvent event) {
        if (isMultiSelect(event)) {
            editor.selection().toggleChip(chip.id());
        } else if (!editor.selection().containsChip(chip.id())) {
            editor.selection().selectChip(chip.id());
        }
        beginMove();
    }

    /** Captures the whole current selection — components and chips — as the drag's before-state. */
    private void beginMove() {
        mode = Mode.MOVING;
        movedComponentsBefore = selectedComponents();
        movedChipsBefore = selectedChips();
    }

    private void onMouseDragged(MouseEvent event) {
        double dx = event.getX() - lastScreenX;
        double dy = event.getY() - lastScreenY;
        if (Math.hypot(event.getX() - viewport.worldToScreen(dragStartWorld).x(),
                event.getY() - viewport.worldToScreen(dragStartWorld).y()) > DRAG_THRESHOLD_PIXELS) {
            dragExceededThreshold = true;
        }
        CircuitPoint world = viewport.screenToWorld(event.getX(), event.getY());

        // If a momentary button is pressed, check if mouse is still over it
        if (pressedComponentId != null) {
            Optional<ComponentInstance> component = hitTester.componentAt(world);
            if (component.isEmpty() || !component.get().id().equals(pressedComponentId)) {
                // Mouse moved off the button - release it
                editor.handleInputInteraction(pressedComponentId, false);
                pressedComponentId = null;
            }
        }

        switch (mode) {
            case PANNING -> {
                viewport.panBy(dx, dy);
                redraw();
            }
            case RUBBER_BAND -> {
                overlay = overlay.withSelectionRectangle(CircuitBounds.between(dragStartWorld, world));
                redraw();
            }
            case MOVING -> dragSelection(world);
            case WIRING -> {
                PlacedElectricalEndpoint origin = overlay.wireOrigin();
                Optional<PlacedElectricalEndpoint> target =
                        hitTester.endpointAt(world, worldTolerance(PORT_TOLERANCE_PIXELS));
                CircuitPoint end = target.map(PlacedElectricalEndpoint::position).orElseGet(() -> Grid.snap(world));
                overlay = overlay.withPreviewWire(origin, router.routeToPoint(origin, end));
                overlay = withHoverFor(target);
                redraw();
            }
            default -> {
            }
        }
        lastScreenX = event.getX();
        lastScreenY = event.getY();
    }

    /** Updates the transient preview positions during drag. Does NOT modify the document. */
    private void dragSelection(CircuitPoint world) {
        if (movedComponentsBefore.isEmpty() && movedChipsBefore.isEmpty()) {
            return;
        }
        CircuitPoint offset = world.minus(dragStartWorld);
        Map<UUID, CircuitPoint> newComponentPositions = new LinkedHashMap<>();
        for (ComponentInstance before : movedComponentsBefore) {
            newComponentPositions.put(before.id(), Grid.snap(before.position().plus(offset)));
        }
        Map<UUID, CircuitPoint> newChipPositions = new LinkedHashMap<>();
        for (ChipInstance before : movedChipsBefore) {
            newChipPositions.put(before.id(), Grid.snap(before.position().plus(offset)));
        }
        overlay = overlay.withMovingComponents(newComponentPositions).withMovingChips(newChipPositions);
        redraw();
    }

    private void onMouseReleased(MouseEvent event) {
        CircuitPoint world = viewport.screenToWorld(event.getX(), event.getY());
        switch (mode) {
            case RUBBER_BAND -> finishRubberBand(world);
            case MOVING -> finishMove(event);
            case WIRING -> finishWiring(world);
            default -> {
            }
        }

        // Release momentary button if it was pressed
        if (pressedComponentId != null) {
            editor.handleInputInteraction(pressedComponentId, false);
            pressedComponentId = null;
        }

        mode = Mode.IDLE;
        overlay = overlay.withSelectionRectangle(null).withPreviewWire(null, null);
        setCursor(pendingPlacement == null ? Cursor.DEFAULT : Cursor.CROSSHAIR);
        redraw();
    }

    private void finishRubberBand(CircuitPoint world) {
        CircuitBounds area = CircuitBounds.between(dragStartWorld, world);
        editor.selection().addAll(hitTester.componentsIn(area), hitTester.chipsIn(area),
                hitTester.connectionsIn(area));
    }

    /**
     * Turns the whole drag into a single undoable command — or, if the pointer never
     * really moved, into a click on the component. Components and chips move together as
     * one undo step.
     */
    private void finishMove(MouseEvent event) {
        if (!dragExceededThreshold) {
            // No actual movement - this is a click, not a drag
            handleClick(event);
            movedComponentsBefore = List.of();
            movedChipsBefore = List.of();
            overlay = overlay.withMovingComponents(Map.of()).withMovingChips(Map.of());
            return;
        }

        Map<UUID, CircuitPoint> finalComponentPositions = overlay.movingComponentPositions();
        Map<UUID, CircuitPoint> finalChipPositions = overlay.movingChipPositions();
        if (finalComponentPositions.isEmpty() && finalChipPositions.isEmpty()) {
            // Fallback: use drag position
            CircuitPoint world = viewport.screenToWorld(event.getX(), event.getY());
            CircuitPoint offset = world.minus(dragStartWorld);
            finalComponentPositions = new LinkedHashMap<>();
            for (ComponentInstance before : movedComponentsBefore) {
                finalComponentPositions.put(before.id(), Grid.snap(before.position().plus(offset)));
            }
            finalChipPositions = new LinkedHashMap<>();
            for (ChipInstance before : movedChipsBefore) {
                finalChipPositions.put(before.id(), Grid.snap(before.position().plus(offset)));
            }
        }

        List<CircuitCommand> commands = new ArrayList<>();
        if (!movedComponentsBefore.isEmpty()) {
            Map<UUID, CircuitPoint> positions = finalComponentPositions;
            List<ComponentInstance> after = movedComponentsBefore.stream()
                    .map(before -> before.withPosition(positions.getOrDefault(before.id(), before.position())))
                    .toList();
            commands.add(new MoveComponentsCommand(editor.document(), movedComponentsBefore, after));
        }
        if (!movedChipsBefore.isEmpty()) {
            Map<UUID, CircuitPoint> positions = finalChipPositions;
            List<ChipInstance> after = movedChipsBefore.stream()
                    .map(before -> before.withPosition(positions.getOrDefault(before.id(), before.position())))
                    .toList();
            commands.add(new MoveChipsCommand(editor.document(), movedChipsBefore, after));
        }
        executeCombined(commands.size() == 1 ? commands.get(0).name() : "Move selection", commands);

        movedComponentsBefore = List.of();
        movedChipsBefore = List.of();
        overlay = overlay.withMovingComponents(Map.of()).withMovingChips(Map.of());
    }

    /** Runs one or more commands as a single undo step. */
    private void executeCombined(String name, List<CircuitCommand> commands) {
        if (commands.isEmpty()) {
            return;
        }
        editor.execute(commands.size() == 1 ? commands.get(0) : new CompositeCommand(name, commands));
    }

    /** Puts the components/chips back where the drag started, so the command owns the change. */
    private void restorePositions() {
        for (ComponentInstance before : movedComponentsBefore) {
            editor.document().replaceComponent(before);
        }
        for (ChipInstance before : movedChipsBefore) {
            editor.document().replaceChip(before);
        }
    }

    private void handleClick(MouseEvent event) {
        hitTester.componentAt(viewport.screenToWorld(event.getX(), event.getY()))
                .ifPresent(component -> {
                    if (editor.isUserInput(component.id())) {
                        var interaction = editor.inputInteraction(component.id());
                        if (interaction == dev.logicforge.circuit.component.InputInteraction.TOGGLE) {
                            editor.toggleInput(component.id());
                        }
                        // MOMENTARY buttons are handled by press/release in onMousePressed/onMouseReleased
                        // so we don't need to do anything here for them
                    }
                });
    }

    private void finishWiring(CircuitPoint world) {
        PlacedElectricalEndpoint origin = overlay.wireOrigin();
        if (origin == null) {
            return;
        }
        hitTester.endpointAt(world, worldTolerance(PORT_TOLERANCE_PIXELS)).ifPresent(target -> {
            if (!target.connectable() || target.endpoint().equals(origin.endpoint())
                    || editor.document().isConnected(origin.endpoint(), target.endpoint())) {
                return;
            }
            editor.execute(new ConnectCommand(editor.document(),
                    Connection.create(origin.endpoint(), target.endpoint())));
        });
    }

    private void onMouseMoved(MouseEvent event) {
        CircuitPoint world = viewport.screenToWorld(event.getX(), event.getY());
        Optional<PlacedElectricalEndpoint> endpoint = hitTester.endpointAt(world, worldTolerance(PORT_TOLERANCE_PIXELS));
        Optional<ComponentInstance> component = hitTester.componentAt(world);
        Optional<ChipInstance> chip = component.isEmpty() ? hitTester.chipAt(world) : Optional.empty();

        overlay = withHoverFor(endpoint)
                .withHover(component.map(ComponentInstance::id).orElse(null), overlay.hoveredPort())
                .withChipHover(chip.map(ChipInstance::id).orElse(null), overlay.hoveredChipPin());
        setCursor(cursorFor(endpoint.map(PlacedElectricalEndpoint::connectable).orElse(false), component, chip));
        updateTooltip(endpoint);
        redraw();
    }

    /** Splits a unified endpoint hit into the component-port and chip-pin overlay fields. */
    private CanvasOverlay withHoverFor(Optional<PlacedElectricalEndpoint> endpoint) {
        if (endpoint.isEmpty()) {
            return overlay.withHover(overlay.hoveredComponent(), null).withChipHover(overlay.hoveredChip(), null);
        }
        PlacedElectricalEndpoint placed = endpoint.get();
        if (placed.endpoint() instanceof ElectricalEndpoint.ComponentEndpoint component) {
            PlacedPort port = hitTester.endpoint(component.port()).orElse(null);
            return overlay.withHover(overlay.hoveredComponent(), port).withChipHover(overlay.hoveredChip(), null);
        }
        return overlay.withHover(overlay.hoveredComponent(), null).withChipHover(overlay.hoveredChip(), placed);
    }

    private Cursor cursorFor(boolean overEndpoint, Optional<ComponentInstance> component,
                             Optional<ChipInstance> chip) {
        if (readOnly) {
            return component.isPresent() || chip.isPresent() ? Cursor.HAND : Cursor.DEFAULT;
        }
        if (pendingPlacement != null) {
            return Cursor.CROSSHAIR;
        }
        if (overEndpoint) {
            return Cursor.CROSSHAIR;
        }
        if (component.map(instance -> editor.isUserInput(instance.id())).orElse(false)) {
            return Cursor.HAND;
        }
        return component.isPresent() || chip.isPresent() ? Cursor.OPEN_HAND : Cursor.DEFAULT;
    }

    /**
     * A compact description of the endpoint under the cursor: name, direction, width and
     * the value on it — for a component port; part designator, pin name and electrical role
     * — for a chip pin. The tooltip is one long-lived object whose text is updated — a new
     * one per mouse move would restart its show delay on every pixel of movement.
     */
    private void updateTooltip(Optional<PlacedElectricalEndpoint> endpoint) {
        if (endpoint.isEmpty()) {
            portTooltip.hide();
            portTooltip.setText("");
            return;
        }
        ElectricalEndpoint raw = endpoint.get().endpoint();
        if (raw instanceof ElectricalEndpoint.ComponentEndpoint component) {
            updatePortTooltip(component.port());
        } else if (raw instanceof ElectricalEndpoint.ChipPinEndpoint chipPin) {
            updateChipPinTooltip(chipPin);
        }
    }

    private void updatePortTooltip(PortEndpoint portEndpoint) {
        Optional<PlacedPort> port = hitTester.endpoint(portEndpoint);
        if (port.isEmpty()) {
            portTooltip.hide();
            return;
        }
        PlacedPort placed = port.get();
        String value = editor.valueAt(placed.endpoint()).map(Object::toString).orElse("–");
        String availability = placed.connectable() ? "" : "\nLocked by existing bus wiring";
        portTooltip.setText(placed.displayName() + "\n" + describe(placed)
                + "\nCurrent: " + value + availability);
    }

    private void updateChipPinTooltip(ElectricalEndpoint.ChipPinEndpoint chipPin) {
        Optional<ChipInstance> chip = editor.document().chip(chipPin.chipInstanceId());
        Optional<dev.logicforge.circuit.chip.ChipDefinition> definition = chip.flatMap(editor::chipDefinitionOf);
        if (chip.isEmpty() || definition.isEmpty()) {
            portTooltip.hide();
            return;
        }
        Optional<dev.logicforge.circuit.chip.PackagePin> pin =
                definition.get().packageDefinition().pin(chipPin.physicalPinNumber());
        if (pin.isEmpty()) {
            portTooltip.hide();
            return;
        }
        String value = editor.valueAt((ElectricalEndpoint) chipPin).map(Object::toString).orElse("–");
        portTooltip.setText(chip.get().referenceDesignator() + "." + pin.get().name()
                + "  (pin " + pin.get().number() + ")"
                + "\n" + pin.get().electricalType()
                + "\nCurrent: " + value);
    }

    private String describe(PlacedPort placed) {
        String direction = switch (placed.spec().direction()) {
            case INPUT -> "Input";
            case OUTPUT -> "Output";
            case INOUT -> "Bidirectional";
        };
        int width = placed.endpoint().isBit() ? 1 : placed.spec().width().bits();
        return direction + " · " + width + (width == 1 ? " bit" : " bits");
    }

    private void onScroll(ScrollEvent event) {
        if (event.isShortcutDown() || event.isControlDown()) {
            viewport.panBy(event.getDeltaX(), event.getDeltaY());
        } else {
            double factor = Math.pow(1.0015, event.getDeltaY());
            viewport.zoomBy(factor, event.getX(), event.getY());
        }
        redraw();
    }

    // -------------------------------------------------------- keyboard input

    private void installKeyHandlers() {
        addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case R -> rotateSelection();
                case DELETE, BACK_SPACE -> deleteSelection();
                case ESCAPE -> cancelInteraction();
                default -> {
                    return;
                }
            }
            event.consume();
        });
    }

    /** Rotates every selected component and chip by a quarter turn, as one undo step. */
    public void rotateSelection() {
        List<ComponentInstance> componentsBefore = selectedComponents();
        List<ChipInstance> chipsBefore = selectedChips();
        if (componentsBefore.isEmpty() && chipsBefore.isEmpty()) {
            return;
        }
        List<CircuitCommand> commands = new ArrayList<>();
        if (!componentsBefore.isEmpty()) {
            List<ComponentInstance> after = componentsBefore.stream()
                    .map(instance -> instance.withRotation(instance.rotation().rotatedClockwise()))
                    .toList();
            commands.add(new RotateComponentsCommand(editor.document(), componentsBefore, after));
        }
        if (!chipsBefore.isEmpty()) {
            List<ChipInstance> after = chipsBefore.stream()
                    .map(instance -> instance.withRotation(instance.rotation().rotatedClockwise()))
                    .toList();
            commands.add(new RotateChipsCommand(editor.document(), chipsBefore, after));
        }
        executeCombined("Rotate", commands);
    }

    public void deleteSelection() {
        if (editor.selection().isEmpty()) {
            return;
        }
        editor.execute(new RemoveElementsCommand(editor.document(),
                Set.copyOf(editor.selection().components()),
                Set.copyOf(editor.selection().chips()),
                Set.copyOf(editor.selection().connections())));
        editor.selection().clear();
    }

    /** Escape: abandon whatever gesture is in progress without changing the circuit. */
    public void cancelInteraction() {
        if (mode == Mode.MOVING) {
            restorePositions();
        }
        mode = Mode.IDLE;
        movedComponentsBefore = List.of();
        movedChipsBefore = List.of();
        overlay = CanvasOverlay.EMPTY;
        setPendingPlacement(null);
        redraw();
    }

    public void copySelection() {
        editor.clipboard().copy(editor.document(), editor.selection().components(), editor.selection().chips());
    }

    public void paste() {
        if (editor.clipboard().isEmpty()) {
            return;
        }
        insert(editor.clipboard().prepareForPaste(editor.document(), PASTE_OFFSET, PASTE_OFFSET));
    }

    private void insert(CircuitClipboard.Fragment fragment) {
        editor.execute(new PasteCommand(editor.document(), fragment.components(), fragment.chips(),
                fragment.connections()));
        editor.selection().setSelection(
                fragment.components().stream().map(ComponentInstance::id).toList(),
                fragment.chips().stream().map(ChipInstance::id).toList(),
                List.of());
    }

    /** {@code true} while the canvas has the keyboard focus. */
    public boolean hasKeyboardFocus() {
        return isFocused();
    }

    /** Duplicates the selection in place, leaving whatever is on the clipboard alone. */
    public void duplicateSelection() {
        CircuitClipboard.Fragment copied = new CircuitClipboard()
                .copy(editor.document(), editor.selection().components(), editor.selection().chips());
        if (copied.isEmpty()) {
            return;
        }
        insert(CircuitClipboard.prepareForPaste(editor.document(), copied, PASTE_OFFSET, PASTE_OFFSET));
    }

    // ------------------------------------------------------------ drag & drop

    /** The clipboard content a palette drag carries. */
    public static javafx.scene.input.ClipboardContent dragContentFor(PlacementRequest request) {
        return DropTarget.contentFor(request);
    }

    private void installDragAndDrop() {
        dropTarget.install(this,
                ghost -> {
                    overlay = overlay.withGhost(ghost);
                    redraw();
                },
                ghost -> {
                    overlay = overlay.withChipGhost(ghost);
                    redraw();
                },
                this::place,
                this::placeChip);
    }

    private void place(ComponentInstance instance) {
        editor.execute(new AddComponentCommand(editor.document(), instance));
        editor.selection().selectComponent(instance.id());
        requestFocus();
    }

    private void placeChip(ChipInstance instance) {
        editor.execute(new AddChipCommand(editor.document(), instance));
        editor.selection().selectChip(instance.id());
        requestFocus();
    }

    // ----------------------------------------------------------- context menu

    private void showContextMenu(MouseEvent event) {
        CircuitPoint world = viewport.screenToWorld(event.getX(), event.getY());
        Optional<PlacedElectricalEndpoint> hit = hitTester.endpointAt(world, worldTolerance(PORT_TOLERANCE_PIXELS));
        // A ground/power chip pin (or any other non-connectable endpoint) has no net of its
        // own to watch — it never expands onto a logical port — so it must not offer "Add to
        // Logic Analyzer"; treat it like the chip/component body was clicked instead.
        Optional<PlacedElectricalEndpoint> endpoint = hit.filter(PlacedElectricalEndpoint::connectable);
        Optional<ComponentInstance> component = endpoint.isEmpty() ? hitTester.componentAt(world) : Optional.empty();
        Optional<ChipInstance> chip = endpoint.isEmpty() && component.isEmpty()
                ? hitTester.chipAt(world) : Optional.empty();
        Optional<Connection> wire = endpoint.isEmpty() && component.isEmpty() && chip.isEmpty()
                ? hitTester.connectionAt(world, worldTolerance(WIRE_TOLERANCE_PIXELS))
                : Optional.empty();

        if (endpoint.isPresent()) {
            // A port or chip pin: select its chip (if any) and offer the analyzer action —
            // the same treatment a component port already gets.
            ElectricalEndpoint electrical = endpoint.get().endpoint();
            if (electrical instanceof ElectricalEndpoint.ChipPinEndpoint chipPin) {
                editor.document().chip(chipPin.chipInstanceId()).ifPresent(instance -> {
                    if (!editor.selection().containsChip(instance.id())) {
                        editor.selection().selectChip(instance.id());
                    }
                });
            }
            contextMenu.showForPort(this, event.getScreenX(), event.getScreenY(),
                    () -> analyzerListener.accept(electrical));
        } else if (hit.isPresent()
                && hit.get().endpoint() instanceof ElectricalEndpoint.ChipPinEndpoint nonConnectablePin) {
            // Landed exactly on a non-connectable pin (e.g. GND/VCC): fall back to the chip
            // body's own menu, the same as clicking anywhere else on the package.
            editor.document().chip(nonConnectablePin.chipInstanceId()).ifPresent(instance -> {
                if (!editor.selection().containsChip(instance.id())) {
                    editor.selection().selectChip(instance.id());
                }
            });
            contextMenu.showForComponent(this, event.getScreenX(), event.getScreenY());
        } else if (component.isPresent()) {
            if (!editor.selection().containsComponent(component.get().id())) {
                editor.selection().selectComponent(component.get().id());
            }
            contextMenu.showForComponent(this, event.getScreenX(), event.getScreenY());
        } else if (chip.isPresent()) {
            if (!editor.selection().containsChip(chip.get().id())) {
                editor.selection().selectChip(chip.get().id());
            }
            contextMenu.showForComponent(this, event.getScreenX(), event.getScreenY());
        } else if (wire.isPresent()) {
            ElectricalEndpoint wireEndpoint = wire.get().from();
            if (!editor.selection().containsConnection(wire.get().id())) {
                editor.selection().selectConnection(wire.get().id());
            }
            contextMenu.showForWire(this, event.getScreenX(), event.getScreenY(),
                    () -> analyzerListener.accept(wireEndpoint));
        } else {
            contextMenu.showForCanvas(this, event.getScreenX(), event.getScreenY());
        }
        redraw();
    }

    // --------------------------------------------------------------- helpers

    private List<ComponentInstance> selectedComponents() {
        List<ComponentInstance> components = new ArrayList<>();
        for (UUID id : editor.selection().components()) {
            editor.document().component(id).ifPresent(components::add);
        }
        return components;
    }

    private List<ChipInstance> selectedChips() {
        List<ChipInstance> chips = new ArrayList<>();
        for (UUID id : editor.selection().chips()) {
            editor.document().chip(id).ifPresent(chips::add);
        }
        return chips;
    }

    private boolean isMultiSelect(MouseEvent event) {
        return event.isShiftDown() || event.isShortcutDown();
    }

    private double worldTolerance(double pixels) {
        return viewport.screenToWorldLength(pixels);
    }

    /** Presses a push button while the mouse is held down on it. */
    public void setMomentaryInput(UUID componentId, boolean pressed) {
        editor.setInput(componentId, pressed ? LogicState.ONE : LogicState.ZERO);
    }

    /** Rotates a single component, used by the inspector. */
    public void rotate(ComponentInstance instance, Rotation rotation) {
        editor.execute(new RotateComponentsCommand(editor.document(), List.of(instance),
                List.of(instance.withRotation(rotation))));
    }

    /** Rotates a single chip, used by the inspector. */
    public void rotate(ChipInstance instance, Rotation rotation) {
        editor.execute(new RotateChipsCommand(editor.document(), List.of(instance),
                List.of(instance.withRotation(rotation))));
    }
}
