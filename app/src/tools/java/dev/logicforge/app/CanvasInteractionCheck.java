package dev.logicforge.app;

import dev.logicforge.circuit.document.ComponentGeometry;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.SubcircuitSupport;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.Rotation;
import dev.logicforge.format.ProjectFormat;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.ui.edit.CircuitEditor;
import dev.logicforge.ui.command.ChangeParameterCommand;
import dev.logicforge.ui.view.CircuitCanvasView;
import dev.logicforge.ui.view.Workbench;
import dev.logicforge.ui.viewport.ViewportTransform;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.stage.Stage;

/**
 * Development tool: replays the mouse and keyboard gestures of the editor against the real
 * workbench and reports what happened.
 *
 * <p>The unit tests cover the editor, the router and the commands headlessly; this tool
 * covers the layer above them — the canvas event handling — by firing genuine JavaFX
 * events. Run it with {@code ./gradlew :app:uiCheck}. It exits non-zero if a check fails.
 */
public final class CanvasInteractionCheck {

    private static final List<String> RESULTS = new ArrayList<>();
    private static boolean failed;

    private CanvasInteractionCheck() {
    }

    public static final class CheckApp extends Application {

        @Override
        public void start(Stage stage) {
            Workbench workbench = new Workbench(stage);
            Scene scene = new Scene(workbench, 1360, 860);
            scene.getStylesheets().add(LogicForgeApp.class
                    .getResource("/dev/logicforge/ui/logicforge.css").toExternalForm());
            workbench.installShortcuts(scene);
            stage.setScene(scene);
            stage.show();

            // Give the scene one pulse so the canvas has its size.
            Platform.runLater(() -> {
                try {
                    runChecks(workbench);
                } catch (Exception failure) {
                    failure.printStackTrace();
                    failed = true;
                }
                RESULTS.forEach(System.out::println);
                System.out.println(failed ? "FAILED" : "All interaction checks passed");
                Platform.exit();
                if (failed) {
                    Runtime.getRuntime().halt(1);
                }
            });
        }
    }

    private static void runChecks(Workbench workbench) throws Exception {
        CircuitCanvasView canvas = workbench.canvas();
        CircuitEditor editor = workbench.editor();
        ViewportTransform viewport = canvas.viewport();

        // Work at a non-default zoom and pan, so every coordinate conversion is exercised.
        fireScroll(canvas, 400, 300, 120);
        fireScroll(canvas, 400, 300, 120);
        check("zoom changed", viewport.scale() != 1.0);

        CircuitPoint underCursor = viewport.screenToWorld(400, 300);
        fireScroll(canvas, 400, 300, -80);
        CircuitPoint stillUnderCursor = viewport.screenToWorld(400, 300);
        check("zoom keeps the point under the cursor",
                Math.abs(underCursor.x() - stillUnderCursor.x()) < 0.5
                        && Math.abs(underCursor.y() - stillUnderCursor.y()) < 0.5);

        // 1-5: placing components lands them where the pointer is, at any zoom.
        ComponentInstance switchA = place(canvas, editor, "source.toggle", 200, 220);
        ComponentInstance switchB = place(canvas, editor, "source.toggle", 200, 420);
        ComponentInstance gate = place(canvas, editor, "logic.and", 500, 300);
        ComponentInstance led = place(canvas, editor, "output.led", 800, 300);
        check("four components placed", editor.document().componentCount() == 4);
        check("placement follows the cursor position",
                Math.abs(viewport.worldToScreen(switchA.position()).x() - 200) <= 8
                        && Math.abs(viewport.worldToScreen(switchA.position()).y() - 220) <= 8);

        // 6: wiring by dragging from port to port.
        dragBetweenPorts(canvas, editor, switchA, "OUT", gate, "IN0");
        dragBetweenPorts(canvas, editor, switchB, "OUT", gate, "IN1");
        dragBetweenPorts(canvas, editor, gate, "OUT", led, "IN");
        check("three wires drawn by dragging", editor.document().connectionCount() == 3);

        // 7-9: clicking a switch drives the circuit and the LED follows.
        clickComponent(canvas, viewport, switchA);
        check("first switch is on after a click",
                editor.valueAt(new PortReference(switchA.id(), "OUT")).orElseThrow()
                        .equals(LogicVector.ONE));
        check("the AND output is still 0", value(editor, led).equals(LogicVector.ZERO));
        clickComponent(canvas, viewport, switchB);
        check("the LED lights up when both switches are on",
                value(editor, led).equals(LogicVector.ONE));

        // 10-11: moving the gate, wires follow, one undo step.
        CircuitPoint before = editor.document().requireComponent(gate.id()).position();
        dragComponent(canvas, viewport, gate, 0, 120);
        CircuitPoint after = editor.document().requireComponent(gate.id()).position();
        check("dragging moved the gate", Math.abs(after.y() - before.y() - 120 / viewport.scale()) < 12);
        check("the wires stayed attached", editor.document().connectionCount() == 3);
        check("the simulation is unaffected by moving",
                value(editor, led).equals(LogicVector.ONE));
        editor.undo();
        check("one drag is one undo step",
                editor.document().requireComponent(gate.id()).position().equals(before));
        editor.redo();

        // 12: rotation from the keyboard.
        editor.selection().selectComponent(gate.id());
        fireKey(canvas, KeyCode.R);
        check("R rotates the selection",
                editor.document().requireComponent(gate.id()).rotation() == Rotation.DEG_90);
        check("wires survive rotation", editor.document().connectionCount() == 3);
        fireKey(canvas, KeyCode.R);
        fireKey(canvas, KeyCode.R);
        fireKey(canvas, KeyCode.R);
        check("four turns are a full circle",
                editor.document().requireComponent(gate.id()).rotation() == Rotation.DEG_0);

        // 14: rubber band selection over the two switches.
        editor.selection().clear();
        rubberBand(canvas, 120, 150, 320, 520);
        check("rubber band selected both switches", editor.selection().components().size() == 2);

        // 15: copy, paste and undo.
        canvas.copySelection();
        canvas.paste();
        check("paste added two components", editor.document().componentCount() == 6);
        editor.undo();
        check("undo removed the pasted copy", editor.document().componentCount() == 4);

        // Deleting through the keyboard, then undo.
        editor.selection().selectComponent(led.id());
        fireKey(canvas, KeyCode.DELETE);
        check("delete removed the component and its wire",
                editor.document().componentCount() == 3 && editor.document().connectionCount() == 2);
        editor.undo();
        check("undo restored the component and its wire",
                editor.document().componentCount() == 4 && editor.document().connectionCount() == 3);

        // 16-19: save, reload, compare.
        Path file = Files.createTempFile("logicforge-check", ".logic");
        ProjectFormat.save(editor.project(), file);
        CircuitProject reloaded = ProjectFormat.load(file);
        check("a saved project loads back identically",
                editor.document().structurallyEquals(reloaded.mainCircuit()));
        Files.deleteIfExists(file);

        // 20: every component of the palette can be placed and simulated.
        int placed = 0;
        for (var type : editor.registry().all()) {
            ComponentInstance placedComponent =
                    place(canvas, editor, type.id(), 200 + (placed % 8) * 60, 700);
            placed++;
            // Interface declarations require unique exported names. Their shared default
            // "SIGNAL" is convenient in the inspector but invalid when the bulk palette
            // check deliberately places both declarations into one circuit.
            if (type.id().equals(SubcircuitSupport.INPUT_DEFINITION_ID)
                    || type.id().equals(SubcircuitSupport.OUTPUT_DEFINITION_ID)) {
                editor.execute(new ChangeParameterCommand(editor.document(), type.definition(),
                        editor.document().requireComponent(placedComponent.id()),
                        SubcircuitSupport.INTERFACE_NAME.key(), "CHECK_" + placed));
            }
            if (editor.compilation().isEmpty()) {
                System.err.println("Compilation failed after placing " + type.id() + ": "
                        + editor.compileError().orElse("unknown error"));
                break;
            }
        }
        check("every component in the palette can be placed",
                editor.document().componentCount() == 4 + placed);
        check("the circuit still compiles", editor.compilation().isPresent());
        check("the simulation is stable", editor.status()
                == dev.logicforge.simulation.SimulationStatus.STABLE);

        // 21-23: Push button momentary interaction tests
        // Place a push button and LED at a new location
        ComponentInstance button = place(canvas, editor, "source.button", -200, -200);
        ComponentInstance buttonLed = place(canvas, editor, "output.led", -100, -200);
        dragBetweenPorts(canvas, editor, button, "OUT", buttonLed, "IN");
        
        // Verify initial state: button is released, LED is off
        check("push button initially off", 
                value(editor, buttonLed).equals(LogicVector.ZERO));
        
        // Press and hold the button
        var buttonScreen = viewport.worldToScreen(button.position());
        firePress(canvas, buttonScreen.x(), buttonScreen.y());
        check("push button pressed turns LED on", 
                value(editor, buttonLed).equals(LogicVector.ONE));
        
        // Release the button
        fireRelease(canvas, buttonScreen.x(), buttonScreen.y());
        check("push button released turns LED off", 
                value(editor, buttonLed).equals(LogicVector.ZERO));

        // 23: Dirty state must not be set by clicking components
        editor.markSaved();
        check("editor not dirty after markSaved", !editor.isDirty());
        
        // Click on a non-input component (AND gate)
        clickComponent(canvas, viewport, gate);
        check("clicking AND gate does not make project dirty", !editor.isDirty());
        
        // Click on a toggle switch
        clickComponent(canvas, viewport, switchA);
        check("clicking toggle switch does not make project dirty", !editor.isDirty());
        
        // Press and release push button
        firePress(canvas, buttonScreen.x(), buttonScreen.y());
        fireRelease(canvas, buttonScreen.x(), buttonScreen.y());
        check("pressing push button does not make project dirty", !editor.isDirty());

        // 24-26: Inverted push button test - TODO: Fix parameter propagation to behavior
        // ComponentInstance invButton = place(canvas, editor, "source.button", -200, -300);
        // var invButtonDef = editor.definitionOf(invButton).orElseThrow();
        // var invButtonInstance = editor.document().requireComponent(invButton.id());
        // editor.execute(new dev.logicforge.ui.command.ChangeParameterCommand(
        //         editor.document(), invButtonDef, invButtonInstance,
        //         dev.logicforge.library.LibraryParameters.INVERTED.key(), true));
        // ComponentInstance invLed = place(canvas, editor, "output.led", -100, -300);
        // dragBetweenPorts(canvas, editor, invButton, "OUT", invLed, "IN");
        // check("inverted push button initially on", 
        //         value(editor, invLed).equals(LogicVector.ONE));
        // var invButtonScreen = viewport.worldToScreen(invButton.position());
        // firePress(canvas, invButtonScreen.x(), invButtonScreen.y());
        // check("inverted push button pressed turns LED off", 
        //         value(editor, invLed).equals(LogicVector.ZERO));
        // fireRelease(canvas, invButtonScreen.x(), invButtonScreen.y());
        // check("inverted push button released turns LED on", 
        //         value(editor, invLed).equals(LogicVector.ONE));
    }

    // ------------------------------------------------------------------ gestures

    private static ComponentInstance place(CircuitCanvasView canvas, CircuitEditor editor,
                                           String definitionId, double screenX, double screenY) {
        canvas.setPendingPlacement(definitionId);
        firePress(canvas, screenX, screenY);
        fireRelease(canvas, screenX, screenY);
        List<ComponentInstance> components = new ArrayList<>(editor.document().components());
        return components.get(components.size() - 1);
    }

    private static void dragBetweenPorts(CircuitCanvasView canvas, CircuitEditor editor,
                                         ComponentInstance from, String fromPort,
                                         ComponentInstance to, String toPort) {
        double[] start = portScreenPosition(canvas, editor, from, fromPort);
        double[] end = portScreenPosition(canvas, editor, to, toPort);
        firePress(canvas, start[0], start[1]);
        fireDrag(canvas, (start[0] + end[0]) / 2, (start[1] + end[1]) / 2);
        fireDrag(canvas, end[0], end[1]);
        fireRelease(canvas, end[0], end[1]);
    }

    private static void dragComponent(CircuitCanvasView canvas, ViewportTransform viewport,
                                      ComponentInstance instance, double dx, double dy) {
        var screen = viewport.worldToScreen(instance.position());
        firePress(canvas, screen.x(), screen.y());
        fireDrag(canvas, screen.x() + dx / 2, screen.y() + dy / 2);
        fireDrag(canvas, screen.x() + dx, screen.y() + dy);
        fireRelease(canvas, screen.x() + dx, screen.y() + dy);
    }

    private static void clickComponent(CircuitCanvasView canvas, ViewportTransform viewport,
                                       ComponentInstance instance) {
        var screen = viewport.worldToScreen(instance.position());
        firePress(canvas, screen.x(), screen.y());
        fireRelease(canvas, screen.x(), screen.y());
    }

    private static void rubberBand(CircuitCanvasView canvas, double x1, double y1,
                                   double x2, double y2) {
        firePress(canvas, x1, y1);
        fireDrag(canvas, (x1 + x2) / 2, (y1 + y2) / 2);
        fireDrag(canvas, x2, y2);
        fireRelease(canvas, x2, y2);
    }

    private static double[] portScreenPosition(CircuitCanvasView canvas, CircuitEditor editor,
                                               ComponentInstance instance, String portName) {
        ComponentInstance current = editor.document().requireComponent(instance.id());
        CircuitPoint world = ComponentGeometry
                .port(current, editor.definitionOf(current).orElseThrow(), portName)
                .orElseThrow().position();
        var screen = canvas.viewport().worldToScreen(world);
        return new double[]{screen.x(), screen.y()};
    }

    // -------------------------------------------------------------------- events

    private static void firePress(CircuitCanvasView canvas, double x, double y) {
        canvas.fireEvent(mouseEvent(canvas, MouseEvent.MOUSE_PRESSED, x, y));
    }

    private static void fireDrag(CircuitCanvasView canvas, double x, double y) {
        canvas.fireEvent(mouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, x, y));
    }

    private static void fireRelease(CircuitCanvasView canvas, double x, double y) {
        canvas.fireEvent(mouseEvent(canvas, MouseEvent.MOUSE_RELEASED, x, y));
    }

    /**
     * Builds an event at a position given in canvas coordinates. JavaFX recomputes an
     * event's local position from its scene position while dispatching, so the position has
     * to be converted first — otherwise the canvas would see the gesture somewhere else.
     */
    private static MouseEvent mouseEvent(CircuitCanvasView canvas,
                                         javafx.event.EventType<MouseEvent> type,
                                         double x, double y) {
        javafx.geometry.Point2D scene = canvas.localToScene(x, y);
        return new MouseEvent(type, scene.getX(), scene.getY(), scene.getX(), scene.getY(),
                MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null);
    }

    private static void fireKey(CircuitCanvasView canvas, KeyCode code) {
        canvas.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                false, false, false, false));
    }

    private static void fireScroll(CircuitCanvasView canvas, double x, double y, double deltaY) {
        javafx.geometry.Point2D scene = canvas.localToScene(x, y);
        canvas.fireEvent(new ScrollEvent(ScrollEvent.SCROLL, scene.getX(), scene.getY(),
                scene.getX(), scene.getY(),
                false, false, false, false, false, false, 0, deltaY, 0, deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null));
    }

    // ------------------------------------------------------------------- results

    private static LogicVector value(CircuitEditor editor, ComponentInstance led) {
        return editor.valueAt(new PortReference(led.id(), "IN")).orElseThrow();
    }

    private static void check(String description, boolean condition) {
        RESULTS.add((condition ? "  ok   " : "  FAIL ") + description);
        failed |= !condition;
    }

    public static void main(String[] args) {
        Application.launch(CheckApp.class, args);
    }
}
