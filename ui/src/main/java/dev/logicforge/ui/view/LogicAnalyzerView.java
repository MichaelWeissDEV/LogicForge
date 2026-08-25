package dev.logicforge.ui.view;

import dev.logicforge.analyzer.SignalTrace;
import dev.logicforge.analyzer.SignalTransition;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.ui.edit.LogicAnalyzerController;
import dev.logicforge.ui.render.Theme;
import java.util.List;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * A minimal but usable logic analyzer dock: the signals the user chose to watch, drawn as
 * a digital waveform, with capture control and time-axis zoom.
 *
 * <p>Not an oscilloscope — no triggers, no cursors, no export. Watching a signal never
 * makes it part of the circuit; this only reads what {@link LogicAnalyzerController}
 * records.
 */
public final class LogicAnalyzerView extends BorderPane {

    private static final double ROW_HEIGHT = 28;
    private static final double LABEL_COLUMN_WIDTH = 130;
    private static final double TARGET_WIDTH = 900;
    private static final double MAX_CANVAS_WIDTH = 12_000;
    private static final double MIN_CANVAS_WIDTH = 200;

    private final LogicAnalyzerController controller;
    private final VBox labelColumn = new VBox();
    private final Canvas canvas = new Canvas(MIN_CANVAS_WIDTH, ROW_HEIGHT);
    private final ScrollPane waveformScroll = new ScrollPane(canvas);
    private final Label timeLabel = new Label("t = 0 ps");
    private final ToggleButton captureButton = new ToggleButton("Capturing");

    private double pixelsPerPicosecond = -1;

    public LogicAnalyzerView(LogicAnalyzerController controller) {
        this.controller = controller;
        getStyleClass().add("side-panel");

        setTop(buildHeader());
        setCenter(buildBody());

        waveformScroll.viewportBoundsProperty().addListener((observable, oldBounds, newBounds) -> redraw());
        controller.addListener(this::redraw);
        redraw();
    }

    private HBox buildHeader() {
        Label title = new Label("LOGIC ANALYZER");
        title.getStyleClass().add("panel-header");

        captureButton.setSelected(true);
        captureButton.getStyleClass().add("tool-button");
        captureButton.setOnAction(event -> {
            controller.setCapturing(captureButton.isSelected());
            captureButton.setText(captureButton.isSelected() ? "Capturing" : "Stopped");
        });

        Button clearButton = new Button("Clear");
        clearButton.getStyleClass().add("tool-button");
        clearButton.setOnAction(event -> controller.clear());

        Button zoomOut = new Button("−");
        zoomOut.getStyleClass().add("tool-button");
        zoomOut.setOnAction(event -> zoom(1.0 / 1.5));
        Button zoomIn = new Button("+");
        zoomIn.getStyleClass().add("tool-button");
        zoomIn.setOnAction(event -> zoom(1.5));
        Button zoomFit = new Button("Fit");
        zoomFit.getStyleClass().add("tool-button");
        zoomFit.setOnAction(event -> {
            pixelsPerPicosecond = -1;
            redraw();
        });

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(6, title, captureButton, clearButton, zoomOut, zoomIn, zoomFit, spacer, timeLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(6, 10, 6, 10));
        header.getStyleClass().add("toolbar");
        return header;
    }

    private HBox buildBody() {
        labelColumn.setFillWidth(true);
        labelColumn.setPrefWidth(LABEL_COLUMN_WIDTH);
        labelColumn.setMinWidth(LABEL_COLUMN_WIDTH);
        labelColumn.getStyleClass().add("panel-body");

        waveformScroll.setFitToHeight(true);
        waveformScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        waveformScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        HBox.setHgrow(waveformScroll, Priority.ALWAYS);

        HBox body = new HBox(labelColumn, waveformScroll);
        VBox.setVgrow(body, Priority.ALWAYS);
        return body;
    }

    private void zoom(double factor) {
        pixelsPerPicosecond = Math.max(1e-15, currentPixelsPerPicosecond() * factor);
        redraw();
    }

    /** Removes a watched signal; called from the row's own remove button. */
    private void remove(PortReference reference) {
        controller.removeSignal(reference);
    }

    private void redraw() {
        List<LogicAnalyzerController.WatchedSignal> signals = controller.watchedSignals();
        long now = controller.currentTime().orElse(0);
        timeLabel.setText("t = " + now + " ps");

        rebuildLabelColumn(signals);

        double rows = Math.max(1, signals.size());
        canvas.setHeight(rows * ROW_HEIGHT);

        long span = timeSpan(signals, now);
        double pxPerPs = pixelsPerPicosecond > 0 ? pixelsPerPicosecond : fitPixelsPerPicosecond(span);
        double width = clamp(span * pxPerPs, MIN_CANVAS_WIDTH, MAX_CANVAS_WIDTH);
        canvas.setWidth(width);

        paint(signals, now, pxPerPs);
    }

    private void rebuildLabelColumn(List<LogicAnalyzerController.WatchedSignal> signals) {
        labelColumn.getChildren().clear();
        for (LogicAnalyzerController.WatchedSignal signal : signals) {
            Label name = new Label(signal.label());
            name.getStyleClass().add("property-label");
            Button removeButton = new Button("✕");
            removeButton.getStyleClass().add("tool-button");
            removeButton.setOnAction(event -> remove(signal.reference()));
            javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(4, name, spacer, removeButton);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPrefHeight(ROW_HEIGHT);
            row.setMinHeight(ROW_HEIGHT);
            row.setPadding(new Insets(0, 4, 0, 6));
            labelColumn.getChildren().add(row);
        }
        if (signals.isEmpty()) {
            Label hint = new Label("Right-click a wire or\nport to add a signal.");
            hint.getStyleClass().add("inspector-subtitle");
            hint.setWrapText(true);
            hint.setPadding(new Insets(8));
            labelColumn.getChildren().add(hint);
        }
    }

    private long timeSpan(List<LogicAnalyzerController.WatchedSignal> signals, long now) {
        long earliest = now;
        for (LogicAnalyzerController.WatchedSignal signal : signals) {
            Optional<SignalTrace> trace = controller.traceFor(signal.reference());
            if (trace.isPresent() && !trace.get().isEmpty()) {
                earliest = Math.min(earliest, trace.get().transitions().get(0).time());
            }
        }
        return Math.max(1, now - earliest);
    }

    private double fitPixelsPerPicosecond(long span) {
        return TARGET_WIDTH / (double) span;
    }

    private double currentPixelsPerPicosecond() {
        if (pixelsPerPicosecond > 0) {
            return pixelsPerPicosecond;
        }
        List<LogicAnalyzerController.WatchedSignal> signals = controller.watchedSignals();
        long now = controller.currentTime().orElse(0);
        return fitPixelsPerPicosecond(timeSpan(signals, now));
    }

    private void paint(List<LogicAnalyzerController.WatchedSignal> signals, long now, double pxPerPs) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        g.setFill(Theme.CANVAS_BACKGROUND);
        g.fillRect(0, 0, width, height);

        long earliest = now - Math.round(width / pxPerPs);
        for (int row = 0; row < signals.size(); row++) {
            LogicAnalyzerController.WatchedSignal signal = signals.get(row);
            Optional<SignalTrace> trace = controller.traceFor(signal.reference());
            double y = row * ROW_HEIGHT;
            g.setStroke(Theme.GRID_MINOR);
            g.setLineWidth(1);
            g.strokeLine(0, y + ROW_HEIGHT, width, y + ROW_HEIGHT);
            trace.ifPresent(t -> paintRow(g, t, earliest, now, pxPerPs, y));
        }
    }

    private void paintRow(GraphicsContext g, SignalTrace trace, long earliest, long now, double pxPerPs, double top) {
        List<SignalTransition> transitions = trace.transitions();
        if (transitions.isEmpty()) {
            return;
        }
        if (trace.width().isSingleBit()) {
            paintDigitalRow(g, transitions, earliest, now, pxPerPs, top);
        } else {
            paintBusRow(g, transitions, earliest, now, pxPerPs, top);
        }
    }

    private void paintDigitalRow(GraphicsContext g, List<SignalTransition> transitions, long earliest, long now,
                                 double pxPerPs, double top) {
        double highY = top + 5;
        double lowY = top + ROW_HEIGHT - 7;
        double midY = top + ROW_HEIGHT / 2;
        g.setLineWidth(1.6);

        for (int i = 0; i < transitions.size(); i++) {
            SignalTransition current = transitions.get(i);
            long segmentEnd = i + 1 < transitions.size() ? transitions.get(i + 1).time() : now;
            if (segmentEnd < earliest) {
                continue;
            }
            double x0 = x(Math.max(current.time(), earliest), earliest, pxPerPs);
            double x1 = x(segmentEnd, earliest, pxPerPs);
            LogicState state = current.value().getBit(0);
            g.setStroke(Theme.signalColor(state));
            double y = switch (state) {
                case ONE -> highY;
                case ZERO -> lowY;
                default -> midY;
            };
            g.strokeLine(x0, y, Math.max(x0, x1), y);
            if (i > 0) {
                LogicState previous = transitions.get(i - 1).value().getBit(0);
                double previousY = switch (previous) {
                    case ONE -> highY;
                    case ZERO -> lowY;
                    default -> midY;
                };
                g.strokeLine(x0, previousY, x0, y);
            }
        }
    }

    private void paintBusRow(GraphicsContext g, List<SignalTransition> transitions, long earliest, long now,
                             double pxPerPs, double top) {
        double boxTop = top + 5;
        double boxBottom = top + ROW_HEIGHT - 5;
        g.setLineWidth(1.4);
        g.setFont(Font.font(10));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);

        for (int i = 0; i < transitions.size(); i++) {
            SignalTransition current = transitions.get(i);
            long segmentEnd = i + 1 < transitions.size() ? transitions.get(i + 1).time() : now;
            if (segmentEnd < earliest) {
                continue;
            }
            double x0 = x(Math.max(current.time(), earliest), earliest, pxPerPs);
            double x1 = Math.max(x0 + 2, x(segmentEnd, earliest, pxPerPs));
            LogicVector value = current.value();
            Color color = value.isFullyDefined() ? Theme.TEXT_PRIMARY
                    : Theme.signalColor(value.isHighImpedance() ? LogicState.HIGH_IMPEDANCE : LogicState.UNKNOWN);
            g.setStroke(color);
            g.strokeRect(x0, boxTop, x1 - x0, boxBottom - boxTop);
            if (x1 - x0 > 18) {
                g.setFill(color);
                g.fillText(hexOf(value), (x0 + x1) / 2, (boxTop + boxBottom) / 2, x1 - x0 - 4);
            }
        }
    }

    private String hexOf(LogicVector value) {
        if (value.isHighImpedance()) {
            return "Z";
        }
        return value.toUnsignedLong()
                .stream().mapToObj(v -> Long.toHexString(v).toUpperCase(java.util.Locale.ROOT))
                .findFirst()
                .orElse("X");
    }

    private double x(long time, long earliest, double pxPerPs) {
        return (time - earliest) * pxPerPs;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
