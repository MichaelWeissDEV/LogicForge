package dev.logicforge.ui.view;

import dev.logicforge.compiler.ValidationIssue;
import dev.logicforge.simulation.SimulationMetrics;
import dev.logicforge.simulation.SimulationStatus;
import dev.logicforge.simulation.SimulationTime;
import dev.logicforge.ui.edit.CircuitEditor;
import dev.logicforge.ui.viewport.ViewportTransform;
import java.util.Locale;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/** The line at the bottom: what the circuit contains and what the simulation is doing. */
public final class StatusBarView extends HBox {

    private final CircuitEditor editor;
    private final ViewportTransform viewport;

    private final Label message = new Label("Ready");
    private final Label components = new Label();
    private final Label nets = new Label();
    private final Label zoom = new Label();
    private final Label simulation = new Label();
    private final Label time = new Label();
    private final Tooltip metricsTooltip = new Tooltip();

    public StatusBarView(CircuitEditor editor, ViewportTransform viewport) {
        this.editor = editor;
        this.viewport = viewport;
        getStyleClass().add("status-bar");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        for (Label label : new Label[]{message, components, nets, zoom, simulation, time}) {
            label.getStyleClass().add("status-item");
        }
        getChildren().addAll(message, spacer, components, nets, zoom, simulation, time);
        metricsTooltip.setShowDelay(javafx.util.Duration.millis(200));
        Tooltip.install(time, metricsTooltip);

        editor.addChangeListener(this::update);
        update();
    }

    public void update() {
        components.setText("Components: " + editor.document().componentCount());
        nets.setText("Nets: " + editor.compilation()
                .map(compilation -> String.valueOf(compilation.circuit().netCount()))
                .orElse("–"));
        zoom.setText("Zoom: " + Math.round(viewport.scale() * 100) + "%");

        time.setText("t = " + SimulationTime.ofPicoseconds(editor.currentTime()));
        metricsTooltip.setText(editor.metrics().map(StatusBarView::formatMetrics)
                .orElse("No simulation metrics yet"));

        SimulationStatus status = editor.status();
        simulation.setText("Simulation: " + status.displayName());
        simulation.getStyleClass().removeAll("status-warning", "status-error");
        if (status == SimulationStatus.OSCILLATING) {
            simulation.getStyleClass().add("status-error");
        } else if (status == SimulationStatus.PENDING) {
            simulation.getStyleClass().add("status-warning");
        }

        updateMessage();
    }

    private void updateMessage() {
        message.getStyleClass().removeAll("status-warning", "status-error");
        if (editor.status() == SimulationStatus.OSCILLATING) {
            message.setText("Combinational oscillation detected");
            message.getStyleClass().add("status-error");
            return;
        }
        if (editor.compileError().isPresent()) {
            message.setText(editor.compileError().get());
            message.getStyleClass().add("status-error");
            return;
        }
        long warnings = editor.issues().stream()
                .filter(issue -> issue.severity() == ValidationIssue.Severity.WARNING)
                .count();
        if (warnings > 0) {
            message.setText(warnings + (warnings == 1 ? " warning" : " warnings"));
            message.getStyleClass().add("status-warning");
            return;
        }
        message.setText("Ready");
    }

    /** Shows a transient message, e.g. after saving. */
    public void showMessage(String text) {
        message.getStyleClass().removeAll("status-warning", "status-error");
        message.setText(text);
    }

    /** Purely observational — see {@link SimulationMetrics}; hover the time label to see it. */
    private static String formatMetrics(SimulationMetrics metrics) {
        return String.format(Locale.ROOT,
                "Simulation metrics (since start or last reset)%n"
                        + "Events processed: %,d%n"
                        + "Component evaluations: %,d%n"
                        + "Net transitions: %,d%n"
                        + "Delta cycles: %,d (max depth at one timestamp: %,d)%n"
                        + "Scheduled wakeups: %,d",
                metrics.eventsProcessed(), metrics.componentEvaluations(), metrics.netTransitions(),
                metrics.deltaCycles(), metrics.maxDeltaDepth(), metrics.scheduledWakeups());
    }
}
