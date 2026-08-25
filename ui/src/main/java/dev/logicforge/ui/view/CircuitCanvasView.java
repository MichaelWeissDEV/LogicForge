package dev.logicforge.ui.view;

import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PlacedPort;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.geometry.CircuitBounds;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.Rotation;
import dev.logicforge.logic.LogicState;
import dev.logicforge.ui.command.AddComponentCommand;
import dev.logicforge.ui.command.ConnectCommand;
import dev.logicforge.ui.command.MoveComponentsCommand;
import dev.logicforge.ui.command.PasteCommand;
import dev.logicforge.ui.command.RemoveElementsCommand;
import dev.logicforge.ui.command.RotateComponentsCommand;
import dev.logicforge.ui.edit.CircuitClipboard;
import dev.logicforge.ui.edit.CircuitEditor;
import dev.logicforge.ui.edit.HitTester;
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
 * event translator rather than a second model.
 */
public final class CircuitCanvasView extends Region {

    /** How close to a port the cursor has to be, in pixels, to grab it. */
    private static final double PORT_TOLERANCE_PIXELS = 12;
    private static final double WIRE_TOLERANCE_PIXELS = 6;
    private static final double DRAG_THRESHOLD_PIXELS = 3;
    private static final double PASTE_OFFSET = Grid.SPACING * 2;

    private enum Mode {
        IDLE, PANNING, RUBBER_BAND, MOVING, WIRING
    }

    private final CircuitEditor editor;
    private final Canvas canvas = new Canvas();
    private final ViewportTransform viewport = new ViewportTransform();
    private final WireRouter router = new OrthogonalWireRouter();
    private final CircuitRenderer renderer;
    private final HitTester hitTester;
    private final CanvasContextMenu contextMenu = new CanvasContextMenu(this);
    private final javafx.scene.control.Tooltip portTooltip = new javafx.scene.control.Tooltip();
    private final ComponentDropTarget dropTarget;

    private CanvasOverlay overlay = CanvasOverlay.EMPTY;
    private Mode mode = Mode.IDLE;
    private CircuitPoint dragStartWorld;
    private double lastScreenX;
    private double lastScreenY;
    private boolean dragExceededThreshold;
    private List<ComponentInstance> movedComponentsBefore = List.of();
    private String pendingPlacement;
    private Runnable statusListener = () -> {
    };
    private java.util.function.Consumer<PortReference> analyzerListener = reference -> {
    };
    
    // For momentary button handling: track which component is being pressed
    private UUID pressedComponentId = null;
    
    // For transient move preview: store original positions of components being moved
    private Map<UUID, CircuitPoint> originalPositions = Map.of();

    public CircuitCanvasView(CircuitEditor editor) {
        this.editor = editor;
        this.renderer = new CircuitRenderer(editor, RendererRegistry.standard(), router);
        this.hitTester = new HitTester(editor::document, editor::definition, router);
        this.dropTarget = new ComponentDropTarget(editor, viewport);

        getChildren().add(canvas);
        setFocusTraversable(true);
        editor.addChangeListener(this::redraw);
        editor.selection().addListener(this::redraw);
        portTooltip.setShowDelay(javafx.util.Duration.millis(350));
        javafx.scene.control.Tooltip.install(this, portTooltip);
        installMouseHandlers();
        installKeyHandlers();
        installDragAndDrop();
        viewport.panBy(120, 80);
    }

    public ViewportTransform viewport() {
        return viewport;
    }

    /** Called whenever something the status bar shows may have changed. */
    public void setStatusListener(Runnable listener) {
        this.statusListener = listener;
    }

    /** Called with the port a user picked "Add to Logic Analyzer" for. */
    public void setAnalyzerListener(java.util.function.Consumer<PortReference> listener) {
        this.analyzerListener = listener;
    }

    /** Arms click-to-place: the next click on the canvas drops this component. */
    public void setPendingPlacement(String definitionId) {
        this.pendingPlacement = definitionId;
        setCursor(definitionId == null ? Cursor.DEFAULT : Cursor.CROSSHAIR);
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
            showContextMenu(event);
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
        if (pendingPlacement != null) {
            place(dropTarget.instanceAt(pendingPlacement, Grid.snap(dragStartWorld)));
            setPendingPlacement(null);
            return;
        }

        Optional<PlacedPort> port = hitTester.portAt(dragStartWorld, worldTolerance(PORT_TOLERANCE_PIXELS));
        if (port.isPresent()) {
            mode = Mode.WIRING;
            overlay = overlay.withPreviewWire(port.get(),
                    router.routeToPoint(port.get(), Grid.snap(dragStartWorld)));
            redraw();
            return;
        }

        Optional<ComponentInstance> component = hitTester.componentAt(dragStartWorld);
        if (component.isPresent()) {
            // Check if this is a momentary button - start tracking press
            var interaction = editor.inputInteraction(component.get().id());
            if (interaction == dev.logicforge.circuit.component.InputInteraction.MOMENTARY) {
                pressedComponentId = component.get().id();
                editor.handleInputInteraction(pressedComponentId, true);
            }
            beginComponentInteraction(component.get(), event);
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
            mode = Mode.IDLE;  // Don't enter MOVING mode for momentary buttons
            movedComponentsBefore = List.of();
            return;
        }
        
        mode = Mode.MOVING;
        movedComponentsBefore = selectedComponents();
        
        // Capture original positions for transient preview
        originalPositions = new java.util.LinkedHashMap<>();
        for (ComponentInstance instance : movedComponentsBefore) {
            originalPositions.put(instance.id(), instance.position());
        }
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
                PlacedPort origin = overlay.wireOrigin();
                Optional<PlacedPort> target = hitTester.portAt(world, worldTolerance(PORT_TOLERANCE_PIXELS));
                CircuitPoint end = target.map(PlacedPort::position).orElseGet(() -> Grid.snap(world));
                overlay = overlay.withPreviewWire(origin, router.routeToPoint(origin, end))
                        .withHover(overlay.hoveredComponent(), target.orElse(null));
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
        if (movedComponentsBefore.isEmpty()) {
            return;
        }
        CircuitPoint offset = world.minus(dragStartWorld);
        Map<UUID, CircuitPoint> newPositions = new java.util.LinkedHashMap<>();
        for (ComponentInstance before : movedComponentsBefore) {
            CircuitPoint target = Grid.snap(before.position().plus(offset));
            newPositions.put(before.id(), target);
        }
        overlay = overlay.withMovingComponents(newPositions);
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
        editor.selection().addAll(hitTester.componentsIn(area), hitTester.connectionsIn(area));
    }

    /**
     * Turns the whole drag into a single undoable command — or, if the pointer never
     * really moved, into a click on the component.
     */
    private void finishMove(MouseEvent event) {
        if (!dragExceededThreshold) {
            // No actual movement - this is a click, not a drag
            // Don't restore positions (they were never changed in the document)
            handleClick(event);
            movedComponentsBefore = List.of();
            originalPositions = Map.of();
            overlay = overlay.withMovingComponents(Map.of());
            return;
        }
        
        // Build the final positions from the preview overlay
        Map<UUID, CircuitPoint> finalPositions = overlay.movingComponentPositions();
        if (finalPositions.isEmpty()) {
            // Fallback: use drag position
            CircuitPoint world = viewport.screenToWorld(event.getX(), event.getY());
            CircuitPoint offset = world.minus(dragStartWorld);
            finalPositions = new java.util.LinkedHashMap<>();
            for (ComponentInstance before : movedComponentsBefore) {
                CircuitPoint target = Grid.snap(before.position().plus(offset));
                finalPositions.put(before.id(), target);
            }
        }
        
        // Create the after instances with new positions
        List<ComponentInstance> after = new ArrayList<>();
        for (ComponentInstance before : movedComponentsBefore) {
            CircuitPoint newPosition = finalPositions.getOrDefault(before.id(), before.position());
            after.add(before.withPosition(newPosition));
        }
        
        // Execute ONE command for the entire move
        editor.execute(new MoveComponentsCommand(editor.document(), movedComponentsBefore, after));
        movedComponentsBefore = List.of();
        originalPositions = Map.of();
        overlay = overlay.withMovingComponents(Map.of());
    }

    /** Puts the components back where the drag started, so the command owns the change. */
    private void restorePositions() {
        for (ComponentInstance before : movedComponentsBefore) {
            editor.document().replaceComponent(before);
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
        PlacedPort origin = overlay.wireOrigin();
        if (origin == null) {
            return;
        }
        hitTester.portAt(world, worldTolerance(PORT_TOLERANCE_PIXELS)).ifPresent(target -> {
            if (target.reference().equals(origin.reference())
                    || editor.document().isConnected(origin.reference(), target.reference())) {
                return;
            }
            editor.execute(new ConnectCommand(editor.document(),
                    Connection.create(origin.reference(), target.reference())));
        });
    }

    private void onMouseMoved(MouseEvent event) {
        CircuitPoint world = viewport.screenToWorld(event.getX(), event.getY());
        Optional<PlacedPort> port = hitTester.portAt(world, worldTolerance(PORT_TOLERANCE_PIXELS));
        Optional<ComponentInstance> component = hitTester.componentAt(world);

        overlay = overlay.withHover(component.map(ComponentInstance::id).orElse(null), port.orElse(null));
        setCursor(cursorFor(port.isPresent(), component));
        updateTooltip(port);
        redraw();
    }

    private Cursor cursorFor(boolean overPort, Optional<ComponentInstance> component) {
        if (pendingPlacement != null) {
            return Cursor.CROSSHAIR;
        }
        if (overPort) {
            return Cursor.CROSSHAIR;
        }
        if (component.map(instance -> editor.isUserInput(instance.id())).orElse(false)) {
            return Cursor.HAND;
        }
        return component.isPresent() ? Cursor.OPEN_HAND : Cursor.DEFAULT;
    }

    /**
     * A compact port description: name, direction, width and the value on it. The tooltip
     * is one long-lived object whose text is updated — a new one per mouse move would
     * restart its show delay on every pixel of movement.
     */
    private void updateTooltip(Optional<PlacedPort> port) {
        if (port.isEmpty()) {
            portTooltip.hide();
            portTooltip.setText("");
            return;
        }
        PlacedPort placed = port.get();
        String value = editor.valueAt(placed.reference()).map(Object::toString).orElse("–");
        portTooltip.setText(placed.spec().name() + "\n" + describe(placed) + "\nCurrent: " + value);
    }

    private String describe(PlacedPort placed) {
        String direction = switch (placed.spec().direction()) {
            case INPUT -> "Input";
            case OUTPUT -> "Output";
            case INOUT -> "Bidirectional";
        };
        return direction + " · " + placed.spec().width();
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

    public void rotateSelection() {
        List<ComponentInstance> before = selectedComponents();
        if (before.isEmpty()) {
            return;
        }
        List<ComponentInstance> after = before.stream()
                .map(instance -> instance.withRotation(instance.rotation().rotatedClockwise()))
                .toList();
        editor.execute(new RotateComponentsCommand(editor.document(), before, after));
    }

    public void deleteSelection() {
        if (editor.selection().isEmpty()) {
            return;
        }
        editor.execute(new RemoveElementsCommand(editor.document(),
                Set.copyOf(editor.selection().components()),
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
        overlay = CanvasOverlay.EMPTY;
        setPendingPlacement(null);
        redraw();
    }

    public void copySelection() {
        editor.clipboard().copy(editor.document(), editor.selection().components());
    }

    public void paste() {
        if (editor.clipboard().isEmpty()) {
            return;
        }
        insert(editor.clipboard().prepareForPaste(PASTE_OFFSET, PASTE_OFFSET));
    }

    private void insert(CircuitClipboard.Fragment fragment) {
        editor.execute(new PasteCommand(editor.document(), fragment.components(), fragment.connections()));
        editor.selection().setSelection(
                fragment.components().stream().map(ComponentInstance::id).toList(), List.of());
    }

    /** {@code true} while the canvas has the keyboard focus. */
    public boolean hasKeyboardFocus() {
        return isFocused();
    }

    /** Duplicates the selection in place, leaving whatever is on the clipboard alone. */
    public void duplicateSelection() {
        CircuitClipboard.Fragment copied = new CircuitClipboard()
                .copy(editor.document(), editor.selection().components());
        if (copied.isEmpty()) {
            return;
        }
        insert(CircuitClipboard.prepareForPaste(copied, PASTE_OFFSET, PASTE_OFFSET));
    }

    // ------------------------------------------------------------ drag & drop

    /** The clipboard content a palette drag carries. */
    public static javafx.scene.input.ClipboardContent dragContentFor(String definitionId) {
        return ComponentDropTarget.contentFor(definitionId);
    }

    private void installDragAndDrop() {
        dropTarget.install(this,
                ghost -> {
                    overlay = overlay.withGhost(ghost);
                    redraw();
                },
                this::place);
    }

    private void place(ComponentInstance instance) {
        editor.execute(new AddComponentCommand(editor.document(), instance));
        editor.selection().selectComponent(instance.id());
        requestFocus();
    }

    // ----------------------------------------------------------- context menu

    private void showContextMenu(MouseEvent event) {
        CircuitPoint world = viewport.screenToWorld(event.getX(), event.getY());
        Optional<PlacedPort> port = hitTester.portAt(world, worldTolerance(PORT_TOLERANCE_PIXELS));
        Optional<ComponentInstance> component = hitTester.componentAt(world);
        Optional<Connection> wire = port.isEmpty() && component.isEmpty()
                ? hitTester.connectionAt(world, worldTolerance(WIRE_TOLERANCE_PIXELS))
                : Optional.empty();
        if (port.isPresent()) {
            contextMenu.showForPort(this, event.getScreenX(), event.getScreenY(),
                    () -> analyzerListener.accept(port.get().reference()));
        } else if (component.isPresent()) {
            if (!editor.selection().containsComponent(component.get().id())) {
                editor.selection().selectComponent(component.get().id());
            }
            contextMenu.showForComponent(this, event.getScreenX(), event.getScreenY());
        } else if (wire.isPresent()) {
            PortReference reference = wire.get().from();
            if (!editor.selection().containsConnection(wire.get().id())) {
                editor.selection().selectConnection(wire.get().id());
            }
            contextMenu.showForWire(this, event.getScreenX(), event.getScreenY(),
                    () -> analyzerListener.accept(reference));
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
}
