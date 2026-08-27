package dev.logicforge.ui.view;

import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.ui.edit.CircuitEditor;
import dev.logicforge.ui.edit.LogicAnalyzerController;
import dev.logicforge.ui.study.StudyTarget;
import dev.logicforge.ui.study.StudyWindow;
import dev.logicforge.structures.ImplementationLevel;
import dev.logicforge.structures.StructuralImplementationRegistry;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
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
    private final LogicAnalyzerController analyzerController;
    private final LogicAnalyzerView analyzerView;
    private final SimulationPlaybackController playback;
    private final SplitPane verticalSplit = new SplitPane();

    private final Button undoButton = toolButton("Undo");
    private final Button redoButton = toolButton("Redo");
    private final ToggleButton runButton = new ToggleButton("Pause");
    private final Button stepButton = toolButton("Step");
    private final Button stepTimeButton = toolButton("Step Time");
    private final ComboBox<SimulationPlaybackController.Speed> speedBox =
            new ComboBox<>(javafx.collections.FXCollections.observableArrayList(
                    SimulationPlaybackController.Speed.values()));
    private final ToggleButton analyzerToggle = new ToggleButton("Analyzer");

    public Workbench(Stage stage) {
        this.editor = new CircuitEditor(ComponentRegistry.standard());
        this.canvas = new CircuitCanvasView(editor);
        this.palette = new PaletteView(editor, canvas);
        this.statusBar = new StatusBarView(editor, canvas.viewport());
        this.projects = new ProjectController(editor, stage, statusBar::showMessage);
        this.analyzerController = new LogicAnalyzerController(editor);
        this.analyzerView = new LogicAnalyzerView(analyzerController, this::showAnalyzerSignal);
        this.playback = new SimulationPlaybackController(editor);

        canvas.setStatusListener(statusBar::update);
        canvas.setAnalyzerListener(this::addToAnalyzer);
        canvas.setHierarchyOpenListener(editor::openSubcircuit);
        editor.addChangeListener(this::updateToolbarState);

        setTop(buildToolbar());
        verticalSplit.setOrientation(Orientation.VERTICAL);
        verticalSplit.getItems().setAll(buildContent(), analyzerView);
        verticalSplit.setDividerPositions(0.72);
        setCenter(verticalSplit);
        setBottom(statusBar);
        analyzerToggle.setSelected(true);
        updateToolbarState();
        playback.start();
    }

    private SplitPane buildContent() {
        VBox left = new VBox(new ProjectCircuitsView(editor), palette);
        VBox.setVgrow(palette, Priority.ALWAYS);
        SplitPane split = new SplitPane(left, canvas,
                new InspectorView(editor, this::inspectInternals, this::studyImplementation));
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.17, 0.80);
        SplitPane.setResizableWithParent(left, false);
        return split;
    }

    /** Adds the port the user right-clicked to the analyzer, labelled by its component. */
    private void addToAnalyzer(PortEndpoint endpoint) {
        PortReference reference = endpoint.port();
        String componentLabel = editor.document().component(reference.componentId())
                .map(ComponentInstance::label)
                .filter(label -> !label.isBlank())
                .orElseGet(() -> reference.componentId().toString().substring(0, 8));
        String pin = endpoint.slice() instanceof dev.logicforge.circuit.document.PortSlice.Bit bit
                ? reference.portName() + "[" + bit.index() + "]" : reference.portName();
        analyzerController.addSignal(endpoint, componentLabel + "." + pin);
        if (!analyzerToggle.isSelected()) {
            analyzerToggle.setSelected(true);
            toggleAnalyzer();
        }
    }

    private void toggleAnalyzer() {
        boolean visible = analyzerToggle.isSelected();
        if (visible && !verticalSplit.getItems().contains(analyzerView)) {
            verticalSplit.getItems().add(analyzerView);
            verticalSplit.setDividerPositions(0.72);
        } else if (!visible) {
            verticalSplit.getItems().remove(analyzerView);
        }
    }

    private void showAnalyzerSignal(LogicAnalyzerController.WatchedSignal signal) {
        analyzerController.location(signal).ifPresent(location ->
                editor.navigateToRuntimeEndpoint(location.parentPath(), location.endpoint()));
    }

    private HBox buildToolbar() {
        Label title = new Label("LogicForge");
        title.getStyleClass().add("app-title");

        Button newButton = toolButton("New");
        newButton.setOnAction(event -> projects.newProject());
        Button openButton = toolButton("Open");
        openButton.setOnAction(event -> projects.open());
        MenuButton examplesButton = examplesMenu();
        Button saveButton = toolButton("Save");
        saveButton.setOnAction(event -> projects.save());

        undoButton.setOnAction(event -> editor.undo());
        redoButton.setOnAction(event -> editor.redo());

        runButton.getStyleClass().add("tool-button");
        runButton.setOnAction(event -> toggleRunning());
        stepButton.setOnAction(event -> editor.step());
        stepTimeButton.setOnAction(event -> editor.stepTime());
        Button resetButton = toolButton("Reset");
        resetButton.setOnAction(event -> editor.resetSimulation());
        Button studyButton = toolButton("Study This Circuit");
        studyButton.setOnAction(event -> new StudyWindow(StudyTarget.live(editor)).show());

        speedBox.setValue(SimulationPlaybackController.Speed.REALTIME);
        speedBox.getStyleClass().add("tool-button");
        speedBox.setOnAction(event -> playback.setSpeed(speedBox.getValue()));

        analyzerToggle.getStyleClass().add("tool-button");
        analyzerToggle.setOnAction(event -> toggleAnalyzer());

        Button zoomOut = toolButton("−");
        zoomOut.setOnAction(event -> canvas.zoomOut());
        Button zoomIn = toolButton("+");
        zoomIn.setOnAction(event -> canvas.zoomIn());
        Button zoomFit = toolButton("Fit");
        zoomFit.setOnAction(event -> canvas.zoomToFit());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(title,
                newButton, openButton, examplesButton, saveButton, separator(),
                undoButton, redoButton, separator(),
                runButton, stepButton, stepTimeButton, resetButton, speedBox, studyButton, separator(),
                analyzerToggle,
                spacer,
                zoomOut, zoomIn, zoomFit);
        toolbar.getStyleClass().add("toolbar");
        return toolbar;
    }

    private MenuButton examplesMenu() {
        MenuButton button = new MenuButton("Examples");
        button.getStyleClass().add("tool-button");
        button.getItems().addAll(
                exampleCategory("Basic Logic", "logic",
                        "gates", "mux", "decoder"),
                exampleCategory("Sequential", "logic",
                        "sr-latch", "d-latch", "dff"),
                exampleCategory("Arithmetic", "arithmetic",
                        "half-adder", "full-adder", "ripple-adder8", "alu8"),
                exampleCategory("Memory", "memory",
                        "register8", "register-file8x8", "ram", "rom"),
                exampleCategory("Processors", "lf8",
                        "lf8-fast", "lf8-structural", "lf8-gate-level"));
        return button;
    }

    private Menu exampleCategory(String category, String directory, String... examples) {
        Menu menu = new Menu(category);
        for (String example : examples) {
            MenuItem item = new MenuItem(example);
            item.setOnAction(event -> projects.openExample(example,
                    "/examples/" + directory + "/" + example + ".logic"));
            menu.getItems().add(item);
        }
        return menu;
    }

    private void inspectInternals(ComponentInstance instance) {
        new StudyWindow(StudyTarget.inspectInternals(editor, instance)).show();
    }

    private void studyImplementation(ComponentInstance instance) {
        StructuralImplementationRegistry.standard()
                .find(instance.definitionId(), instance.parameters(), ImplementationLevel.GATE)
                .ifPresent(implementation ->
                        new StudyWindow(StudyTarget.reference(implementation)).show());
    }

    private void toggleRunning() {
        boolean running = !editor.isRunning();
        editor.setRunning(running);
        if (running) {
            playback.start();
        } else {
            playback.stop();
        }
        updateToolbarState();
    }

    private void updateToolbarState() {
        undoButton.setDisable(!editor.undoStack().canUndo());
        redoButton.setDisable(!editor.undoStack().canRedo());
        boolean running = editor.isRunning();
        runButton.setSelected(!running);
        runButton.setText(running ? "Pause" : "Run");
        // Stepping and manual speed only matter while playback isn't driving time itself.
        stepButton.setDisable(running);
        stepTimeButton.setDisable(running || editor.nextScheduledTime().isEmpty());
        speedBox.setDisable(!running);
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

    public LogicAnalyzerController analyzerController() {
        return analyzerController;
    }
}
