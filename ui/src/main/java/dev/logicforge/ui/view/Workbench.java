package dev.logicforge.ui.view;

import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.ElectricalEndpoint;
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
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
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

    private static final double LEFT_PANEL_MIN_WIDTH = 200;

    /** How the platform names the shortcut modifier in tooltips. */
    private static final String SHORTCUT =
            System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac")
                    ? "Cmd" : "Ctrl";

    private final Button undoButton = toolButton("Undo", "Undo the last change (" + SHORTCUT + "+Z)");
    private final Button redoButton = toolButton("Redo", "Redo the last undone change ("
            + SHORTCUT + "+Shift+Z)");
    private final ToggleButton runButton = new ToggleButton("Pause");
    private final Button stepButton = toolButton("Step", "Process the next simulation event");
    private final Button stepTimeButton = toolButton("Step Time",
            "Advance the simulation to the next scheduled time");
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
        projects.setAfterDialog(canvas::requestFocus);
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
        // Wide enough for the circuit buttons even when the window is at its minimum size.
        left.setMinWidth(LEFT_PANEL_MIN_WIDTH);
        SplitPane split = new SplitPane(left, canvas,
                new InspectorView(editor, this::inspectInternals, this::studyImplementation));
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.17, 0.80);
        SplitPane.setResizableWithParent(left, false);
        return split;
    }

    /** Adds the port or chip pin the user right-clicked to the analyzer. */
    private void addToAnalyzer(ElectricalEndpoint endpoint) {
        if (endpoint instanceof ElectricalEndpoint.ComponentEndpoint component) {
            addPortToAnalyzer(component.port());
        } else if (endpoint instanceof ElectricalEndpoint.ChipPinEndpoint chipPin) {
            addChipPinToAnalyzer(chipPin);
        }
    }

    /** Labelled by its component. */
    private void addPortToAnalyzer(PortEndpoint endpoint) {
        PortReference reference = endpoint.port();
        String componentLabel = editor.document().component(reference.componentId())
                .map(ComponentInstance::label)
                .filter(label -> !label.isBlank())
                .orElseGet(() -> reference.componentId().toString().substring(0, 8));
        String pin = endpoint.slice() instanceof dev.logicforge.circuit.document.PortSlice.Bit bit
                ? reference.portName() + "[" + bit.index() + "]" : reference.portName();
        analyzerController.addSignal(endpoint, componentLabel + "." + pin);
        revealAnalyzer();
    }

    /** Labelled by its chip's reference designator (e.g. "U1.pin7"). */
    private void addChipPinToAnalyzer(ElectricalEndpoint.ChipPinEndpoint chipPin) {
        String designator = editor.document().chip(chipPin.chipInstanceId())
                .map(ChipInstance::referenceDesignator)
                .filter(label -> !label.isBlank())
                .orElseGet(() -> chipPin.chipInstanceId().toString().substring(0, 8));
        analyzerController.addSignal(chipPin, designator + ".pin" + chipPin.physicalPinNumber());
        revealAnalyzer();
    }

    private void revealAnalyzer() {
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

    /**
     * A {@link ToolBar} rather than a plain box: when the window is too narrow for every
     * control, the ones that do not fit move into its overflow menu instead of all of them
     * being squeezed until no label is readable.
     */
    private ToolBar buildToolbar() {
        Label title = new Label("LogicForge");
        title.getStyleClass().add("app-title");

        Button newButton = toolButton("New", "Start a new, empty circuit (" + SHORTCUT + "+N)");
        newButton.setOnAction(event -> projects.newProject());
        Button openButton = toolButton("Open", "Open a LogicForge project (" + SHORTCUT + "+O)");
        openButton.setOnAction(event -> projects.open());
        MenuButton examplesButton = examplesMenu();
        Button saveButton = toolButton("Save", "Save the project (" + SHORTCUT + "+S)");
        saveButton.setOnAction(event -> projects.save());
        Button saveAsButton = toolButton("Save As",
                "Save the project under a new name (" + SHORTCUT + "+Shift+S)");
        saveAsButton.setOnAction(event -> projects.saveAs());

        undoButton.setOnAction(event -> editor.undo());
        redoButton.setOnAction(event -> editor.redo());

        runButton.getStyleClass().add("tool-button");
        runButton.setTooltip(new Tooltip("Pause or resume the simulation"));
        runButton.setOnAction(event -> toggleRunning());
        stepButton.setOnAction(event -> editor.step());
        stepTimeButton.setOnAction(event -> editor.stepTime());
        Button resetButton = toolButton("Reset", "Reset the simulation to its initial state");
        resetButton.setOnAction(event -> editor.resetSimulation());
        Button studyButton = toolButton("Study",
                "Open a read-only Study window attached to the running circuit");
        studyButton.setOnAction(event -> new StudyWindow(StudyTarget.live(editor)).show());

        speedBox.setValue(SimulationPlaybackController.Speed.REALTIME);
        speedBox.getStyleClass().add("tool-button");
        speedBox.setTooltip(new Tooltip("Simulation speed while running"));
        speedBox.setOnAction(event -> playback.setSpeed(speedBox.getValue()));

        analyzerToggle.getStyleClass().add("tool-button");
        analyzerToggle.setTooltip(new Tooltip("Show or hide the logic analyzer"));
        analyzerToggle.setOnAction(event -> toggleAnalyzer());

        Button zoomOut = toolButton("−", "Zoom out (" + SHORTCUT + "+-)");
        zoomOut.setOnAction(event -> canvas.zoomOut());
        Button zoomIn = toolButton("+", "Zoom in (" + SHORTCUT + "++)");
        zoomIn.setOnAction(event -> canvas.zoomIn());
        Button zoomFit = toolButton("Fit", "Zoom to fit the whole circuit");
        zoomFit.setOnAction(event -> canvas.zoomToFit());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToolBar toolbar = new ToolBar(title,
                newButton, openButton, examplesButton, saveButton, saveAsButton, separator(),
                undoButton, redoButton, separator(),
                runButton, stepButton, stepTimeButton, resetButton, speedBox, studyButton, separator(),
                analyzerToggle,
                spacer,
                zoomOut, zoomIn, zoomFit, helpMenu());
        toolbar.getStyleClass().add("toolbar");
        return toolbar;
    }

    private MenuButton helpMenu() {
        MenuButton button = new MenuButton("Help");
        button.getStyleClass().add("tool-button");
        button.setTooltip(new Tooltip("Help and information about LogicForge"));
        MenuItem about = new MenuItem("About LogicForge");
        about.setOnAction(event -> {
            AboutDialog.show(getScene() == null ? null : getScene().getWindow());
            canvas.requestFocus();
        });
        button.getItems().add(about);
        return button;
    }

    private MenuButton examplesMenu() {
        MenuButton button = new MenuButton("Examples");
        button.getStyleClass().add("tool-button");
        button.setTooltip(new Tooltip("Open one of the bundled example circuits"));
        button.getItems().addAll(
                exampleCategory("Basic Logic", "logic",
                        "gates", "mux", "decoder"),
                exampleCategory("Sequential", "logic",
                        "sr-latch", "d-latch", "dff"),
                exampleCategory("Arithmetic", "arithmetic",
                        "half-adder", "full-adder", "ripple-adder8", "alu8"),
                exampleCategory("Memory", "memory",
                        "register8", "register-file8x8", "ram", "rom"),
                exampleCategory("Physical ICs", "physical-ic",
                        "74hc-nand", "74hc-half-adder", "74hc283-adder"),
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

    private static Button toolButton(String text, String tooltip) {
        Button button = toolButton(text);
        button.setTooltip(new Tooltip(tooltip));
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

    /** New, Open and Save of this window, including opening a file passed on the command line. */
    public ProjectController projects() {
        return projects;
    }

    public CircuitCanvasView canvas() {
        return canvas;
    }

    public LogicAnalyzerController analyzerController() {
        return analyzerController;
    }
}
