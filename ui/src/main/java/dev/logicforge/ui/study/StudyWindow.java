package dev.logicforge.ui.study;

import dev.logicforge.ui.view.CircuitCanvasView;
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

    private final StudyController controller;
    private final CircuitCanvasView canvas;
    private final StudyInspectorView inspector;
    private final Label breadcrumb = new Label();
    private final Label status = new Label();
    private final Button back = new Button("Back");
    private final Button forward = new Button("Forward");
    private final ToggleButton run = new ToggleButton("Run");
    private final Button stepEvent = new Button("Step Event");
    private final Button stepClock = new Button("Clock");
    private final Button stepInstruction = new Button("Instruction");
    private final AnimationTimer refresher;

    public StudyWindow(StudyTarget target) {
        this.controller = new StudyController(target);
        this.canvas = new CircuitCanvasView(controller.editor(), true);
        this.inspector = new StudyInspectorView(controller.editor());
        this.canvas.setHierarchyOpenListener(instance -> {
            controller.descend(instance);
            updateControls();
            canvas.zoomToFit();
        });

        BorderPane root = new BorderPane();
        root.setTop(buildTop());
        SplitPane content = new SplitPane(canvas, inspector);
        content.setOrientation(Orientation.HORIZONTAL);
        content.setDividerPositions(0.76);
        root.setCenter(content);
        root.setBottom(buildControls());
        setTitle("LogicForge Study — " + target.label());
        setScene(new Scene(root, 1220, 780));

        controller.editor().addChangeListener(this::updateControls);
        updateControls();
        this.refresher = new AnimationTimer() {
            @Override
            public void handle(long now) {
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
        Label targetMode = new Label(controller.isLive() ? "LIVE INSTANCE" : "REFERENCE");
        HBox top = new HBox(8, back, forward, targetMode, breadcrumb);
        top.setStyle("-fx-padding: 8;");
        HBox.setHgrow(breadcrumb, Priority.ALWAYS);
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
        controls.setStyle("-fx-padding: 8;");
        return controls;
    }

    private void updateControls() {
        breadcrumb.setText(String.join("  >  ", controller.editor().navigationLabels()));
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
}
