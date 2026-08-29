package dev.logicforge.ui.study;

import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.ElectricalEndpoint;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.ui.view.CircuitCanvasView;
import dev.logicforge.ui.view.LogicAnalyzerView;
import dev.logicforge.ui.edit.LogicAnalyzerController;
import javafx.animation.AnimationTimer;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/** Dedicated read-only live/reference hardware study stage. */
public final class StudyWindow extends Stage {

    private static final long MIN_REFRESH_NANOS = 1_000_000_000L / 30;

    private final StudyController controller;
    private final CircuitCanvasView canvas;
    private final StudyInspectorView inspector;
    private final LogicAnalyzerController analyzerController;
    private final LogicAnalyzerView analyzer;
    private final HBox breadcrumbs = new HBox(4);
    private final Label status = new Label();
    private final Button back = new Button("Back");
    private final Button forward = new Button("Forward");
    private final ToggleButton run = new ToggleButton("Run");
    private final Button stepEvent = new Button("Step Event");
    private final Button stepClock = new Button("Clock");
    private final Button stepInstruction = new Button("Instruction");
    private final AnimationTimer refresher;
    private boolean dirty = true;
    private long lastRefresh;

    public StudyWindow(StudyTarget target) {
        this.controller = new StudyController(target);
        this.canvas = new CircuitCanvasView(controller.editor(), true, false);
        this.inspector = new StudyInspectorView(controller);
        this.analyzerController = new LogicAnalyzerController(controller.editor());
        this.analyzer = new LogicAnalyzerView(analyzerController, signal ->
                analyzerController.location(signal).ifPresent(location -> {
                    controller.editor().navigateToRuntimeEndpoint(
                            location.parentPath(), location.endpoint());
                    canvas.zoomToFit();
                }));
        this.canvas.setAnalyzerListener(this::addToAnalyzer);
        this.canvas.setHierarchyOpenListener(instance -> {
            controller.descend(instance);
            updateControls();
            canvas.zoomToFit();
        });

        BorderPane root = new BorderPane();
        root.setTop(buildTop());
        SplitPane schematic = new SplitPane(canvas, inspector);
        schematic.setOrientation(Orientation.HORIZONTAL);
        schematic.setDividerPositions(0.76);
        SplitPane content = new SplitPane(schematic, analyzer);
        content.setOrientation(Orientation.VERTICAL);
        content.setDividerPositions(0.75);
        root.setCenter(content);
        root.setBottom(buildControls());
        setTitle("LogicForge Study — " + target.label());
        Scene scene = new Scene(root, 1220, 780);
        scene.getStylesheets().add(StudyWindow.class
                .getResource("/dev/logicforge/ui/logicforge.css").toExternalForm());
        setScene(scene);

        controller.editor().addChangeListener(() -> dirty = true);
        updateControls();
        this.refresher = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!dirty || now - lastRefresh < MIN_REFRESH_NANOS) {
                    return;
                }
                dirty = false;
                lastRefresh = now;
                canvas.redraw();
                inspector.refresh();
                updateControls();
            }
        };
        setOnShown(event -> {
            canvas.zoomToFit();
            refresher.start();
        });
        setOnHidden(event -> refresher.stop());
    }

    public StudyController controller() {
        return controller;
    }

    /** Adds the port or chip pin the user right-clicked to the analyzer. */
    private void addToAnalyzer(ElectricalEndpoint endpoint) {
        if (endpoint instanceof ElectricalEndpoint.ComponentEndpoint component) {
            PortEndpoint port = component.port();
            PortReference reference = port.port();
            String componentLabel = controller.editor().document().component(reference.componentId())
                    .map(ComponentInstance::label)
                    .filter(label -> !label.isBlank())
                    .orElseGet(() -> reference.componentId().toString().substring(0, 8));
            String pin = port.slice() instanceof dev.logicforge.circuit.document.PortSlice.Bit bit
                    ? reference.portName() + "[" + bit.index() + "]" : reference.portName();
            analyzerController.addSignal(port, componentLabel + "." + pin);
        } else if (endpoint instanceof ElectricalEndpoint.ChipPinEndpoint chipPin) {
            String designator = controller.editor().document().chip(chipPin.chipInstanceId())
                    .map(ChipInstance::referenceDesignator)
                    .filter(label -> !label.isBlank())
                    .orElseGet(() -> chipPin.chipInstanceId().toString().substring(0, 8));
            analyzerController.addSignal(chipPin, designator + ".pin" + chipPin.physicalPinNumber());
        }
    }

    private HBox buildTop() {
        back.setOnAction(event -> {
            controller.back();
            canvas.zoomToFit();
            updateControls();
        });
        forward.setOnAction(event -> {
            controller.forward();
            canvas.zoomToFit();
            updateControls();
        });
        Label targetMode = new Label(controller.isLive()
                ? "LIVE INSTANCE" : "REFERENCE IMPLEMENTATION");
        HBox top = new HBox(8, back, forward, targetMode, breadcrumbs);
        top.getStyleClass().add("study-toolbar");
        HBox.setHgrow(breadcrumbs, Priority.ALWAYS);
        return top;
    }

    private HBox buildControls() {
        run.setOnAction(event -> controller.setRunning(run.isSelected()));
        stepEvent.setOnAction(event -> controller.stepEvent());
        stepClock.setOnAction(event -> controller.stepClock());
        stepInstruction.setOnAction(event -> status.setText(controller.stepInstruction()
                ? "Advanced to the next instruction boundary"
                : "No LF-8 instruction boundary was reachable"));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox controls = new HBox(8, run, stepEvent, stepClock, stepInstruction, spacer, status);
        controls.getStyleClass().add("study-controls");
        return controls;
    }

    private void updateControls() {
        rebuildBreadcrumbs();
        back.setDisable(!controller.canBack());
        forward.setDisable(!controller.canForward());
        boolean live = controller.isLive();
        boolean running = live && controller.editor().isRunning();
        run.setDisable(!live);
        run.setSelected(running);
        run.setText(running ? "Pause" : "Run");
        stepEvent.setDisable(!live || running);
        stepClock.setDisable(!live || running);
        stepInstruction.setDisable(!live || running);
    }

    private void rebuildBreadcrumbs() {
        var labels = controller.breadcrumbs();
        breadcrumbs.getChildren().clear();
        for (int index = 0; index < labels.size(); index++) {
            if (index > 0) {
                breadcrumbs.getChildren().add(new Label("›"));
            }
            Button crumb = new Button(labels.get(index));
            crumb.setDisable(index == labels.size() - 1);
            int targetIndex = index;
            crumb.setOnAction(event -> {
                controller.navigateToBreadcrumb(targetIndex);
                canvas.zoomToFit();
                dirty = true;
                updateControls();
            });
            breadcrumbs.getChildren().add(crumb);
        }
    }
}
