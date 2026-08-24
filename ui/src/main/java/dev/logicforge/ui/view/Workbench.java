package dev.logicforge.ui.view;

import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.ui.edit.CircuitEditor;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/**
 * The application window: toolbar on top, palette, canvas and inspector side by side, and
 * the status bar underneath.
 *
 * <p>The workbench only assembles the pieces and forwards user actions; the editing logic
 * lives in {@link CircuitEditor} and in the commands.
 */
public final class Workbench extends BorderPane {

    private final CircuitEditor editor;
    private final CircuitCanvasView canvas;
    private final PaletteView palette;
    private final StatusBarView statusBar;
    private final ProjectController projects;

    private final Button undoButton = toolButton("Undo");
    private final Button redoButton = toolButton("Redo");
    private final ToggleButton runButton = new ToggleButton("Pause");
    private final Button stepButton = toolButton("Step");

    public Workbench(Stage stage) {
        this.editor = new CircuitEditor(ComponentRegistry.standard());
        this.canvas = new CircuitCanvasView(editor);
        this.palette = new PaletteView(editor.registry(), canvas);
        this.statusBar = new StatusBarView(editor, canvas.viewport());
        this.projects = new ProjectController(editor, stage, statusBar::showMessage);

        canvas.setStatusListener(statusBar::update);
        editor.addChangeListener(this::updateToolbarState);

        setTop(buildToolbar());
        setCenter(buildContent());
        setBottom(statusBar);
        updateToolbarState();
    }

    private SplitPane buildContent() {
        SplitPane split = new SplitPane(palette, canvas, new InspectorView(editor));
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.17, 0.80);
        SplitPane.setResizableWithParent(palette, false);
        return split;
    }

    private HBox buildToolbar() {
        Label title = new Label("LogicForge");
        title.getStyleClass().add("app-title");

        Button newButton = toolButton("New");
        newButton.setOnAction(event -> projects.newProject());
        Button openButton = toolButton("Open");
        openButton.setOnAction(event -> projects.open());
        Button saveButton = toolButton("Save");
        saveButton.setOnAction(event -> projects.save());

        undoButton.setOnAction(event -> editor.undo());
        redoButton.setOnAction(event -> editor.redo());

        runButton.getStyleClass().add("tool-button");
        runButton.setOnAction(event -> toggleRunning());
        stepButton.setOnAction(event -> editor.step());
        Button resetButton = toolButton("Reset");
        resetButton.setOnAction(event -> editor.resetSimulation());

        Button zoomOut = toolButton("−");
        zoomOut.setOnAction(event -> canvas.zoomOut());
        Button zoomIn = toolButton("+");
        zoomIn.setOnAction(event -> canvas.zoomIn());
        Button zoomFit = toolButton("Fit");
        zoomFit.setOnAction(event -> canvas.zoomToFit());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(title,
                newButton, openButton, saveButton, separator(),
                undoButton, redoButton, separator(),
                runButton, stepButton, resetButton,
                spacer,
                zoomOut, zoomIn, zoomFit);
        toolbar.getStyleClass().add("toolbar");
        return toolbar;
    }

    private void toggleRunning() {
        editor.setRunning(!editor.isRunning());
        updateToolbarState();
    }

    private void updateToolbarState() {
        undoButton.setDisable(!editor.undoStack().canUndo());
        redoButton.setDisable(!editor.undoStack().canRedo());
        boolean running = editor.isRunning();
        runButton.setSelected(!running);
        runButton.setText(running ? "Pause" : "Run");
        // Stepping is only meaningful while the simulation is paused.
        stepButton.setDisable(running);
    }

    private static Button toolButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("tool-button");
        return button;
    }

    private static Region separator() {
        Region separator = new Region();
        separator.getStyleClass().add("toolbar-separator");
        HBox.setMargin(separator, new javafx.geometry.Insets(0, 6, 0, 6));
        return separator;
    }

    /** Registers the keyboard shortcuts, using Cmd on macOS and Ctrl elsewhere. */
    public void installShortcuts(Scene scene) {
        accelerator(scene, KeyCode.N, projects::newProject);
        accelerator(scene, KeyCode.O, projects::open);
        accelerator(scene, KeyCode.S, projects::save);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                projects::saveAs);
        accelerator(scene, KeyCode.Z, editor::undo);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                editor::redo);
        accelerator(scene, KeyCode.Y, editor::redo);
        // Editing shortcuts must not fire while the user is typing in a text field.
        canvasAccelerator(scene, KeyCode.C, canvas::copySelection);
        canvasAccelerator(scene, KeyCode.V, canvas::paste);
        canvasAccelerator(scene, KeyCode.D, canvas::duplicateSelection);
        accelerator(scene, KeyCode.F, palette::focusSearch);
        accelerator(scene, KeyCode.PLUS, canvas::zoomIn);
        accelerator(scene, KeyCode.EQUALS, canvas::zoomIn);
        accelerator(scene, KeyCode.MINUS, canvas::zoomOut);
        accelerator(scene, KeyCode.DIGIT0, canvas::resetZoom);
    }

    /** An accelerator that only acts when the canvas, not a text field, has the focus. */
    private void canvasAccelerator(Scene scene, KeyCode code, Runnable action) {
        scene.getAccelerators().put(new KeyCodeCombination(code, KeyCombination.SHORTCUT_DOWN), () -> {
            if (canvas.hasKeyboardFocus()) {
                action.run();
            }
        });
    }

    private void accelerator(Scene scene, KeyCode code, Runnable action) {
        scene.getAccelerators().put(new KeyCodeCombination(code, KeyCombination.SHORTCUT_DOWN),
                action::run);
    }

    /** Asked before the window closes. */
    public boolean confirmClose() {
        return projects.confirmDiscardingChanges();
    }

    public CircuitEditor editor() {
        return editor;
    }

    public CircuitCanvasView canvas() {
        return canvas;
    }
}
