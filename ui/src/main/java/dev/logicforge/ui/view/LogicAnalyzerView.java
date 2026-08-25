package dev.logicforge.ui.view;

import dev.logicforge.analyzer.SignalTrace;
import dev.logicforge.analyzer.SignalTransition;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.ui.edit.LogicAnalyzerController;
import dev.logicforge.ui.render.Theme;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * A minimal but usable logic analyzer dock: the signals the user chose to watch, drawn as
 * a digital waveform against a real time ruler, with capture control, zoom and auto-follow.
 *
 * <p>Not an oscilloscope — no triggers, no cursors, no export. Watching a signal never
 * makes it part of the circuit; this only reads what {@link LogicAnalyzerController}
 * records.
 *
 * <p>Layout: labels+current-value sit in a fixed-width column, the ruler and waveform sit
 * in a horizontally scrollable column next to it, and both columns live inside one shared
 * vertical scroll pane — so with many signals, labels and waveforms scroll together instead
 * of drifting apart.
 */
public final class LogicAnalyzerView extends BorderPane {

    /** How a multi-bit value is written in the current-value column and bus segments. */
    public enum BusDisplayMode {
        HEX, BIN, UNSIGNED
    }

    private static final double ROW_HEIGHT = 28;
    private static final double RULER_HEIGHT = 20;
    private static final double LABEL_COLUMN_WIDTH = 170;
    private static final double TARGET_WIDTH = 900;
    private static final double MAX_CANVAS_WIDTH = 12_000;
    private static final double MIN_CANVAS_WIDTH = 200;
    private static final double MIN_PIXELS_PER_TICK = 70;

    private final LogicAnalyzerController controller;
    private final VBox labelColumn = new VBox();
    private final Canvas ruler = new Canvas(MIN_CANVAS_WIDTH, RULER_HEIGHT);
    private final Canvas canvas = new Canvas(MIN_CANVAS_WIDTH, ROW_HEIGHT);
    private final ScrollPane waveformScroll = new ScrollPane(new VBox(ruler, canvas));
    private final Label timeLabel = new Label("t = 0 ps");
    private final ToggleButton captureButton = new ToggleButton("Capturing");
    private final ToggleButton followButton = new ToggleButton("Follow");
    private final ComboBox<BusDisplayMode> busModeBox =
            new ComboBox<>(FXCollections.observableArrayList(BusDisplayMode.values()));

    private double pixelsPerPicosecond = -1;
    private boolean autoFollow = true;
    private boolean scrollingProgrammatically;

    public LogicAnalyzerView(LogicAnalyzerController controller) {
        this.controller = controller;
        getStyleClass().add("side-panel");

        setTop(buildHeader());
        setCenter(buildBody());

        waveformScroll.viewportBoundsProperty().addListener((observable, oldBounds, newBounds) -> redraw());
        waveformScroll.hvalueProperty().addListener((observable, oldValue, newValue) -> {
            if (!scrollingProgrammatically) {
                setAutoFollow(false);
            }
        });
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

        followButton.setSelected(true);
        followButton.getStyleClass().add("tool-button");
        followButton.setOnAction(event -> setAutoFollow(followButton.isSelected()));

        busModeBox.setValue(BusDisplayMode.HEX);
        busModeBox.getStyleClass().add("tool-button");
        busModeBox.setOnAction(event -> redraw());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(6, title, captureButton, clearButton, zoomOut, zoomIn, zoomFit, followButton,
                busModeBox, spacer, timeLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(6, 10, 6, 10));
        header.getStyleClass().add("toolbar");
        return header;
    }

    private ScrollPane buildBody() {
        labelColumn.setFillWidth(true);
        labelColumn.setPrefWidth(LABEL_COLUMN_WIDTH);
        labelColumn.setMinWidth(LABEL_COLUMN_WIDTH);
        labelColumn.getStyleClass().add("panel-body");

        waveformScroll.setFitToHeight(false);
        waveformScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        waveformScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        HBox.setHgrow(waveformScroll, Priority.ALWAYS);

        HBox row = new HBox(labelColumn, waveformScroll);

        ScrollPane outer = new ScrollPane(row);
        outer.setFitToWidth(true);
        outer.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        outer.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return outer;
    }

    private void zoom(double factor) {
        pixelsPerPicosecond = Math.max(1e-15, currentPixelsPerPicosecond() * factor);
        redraw();
    }

    private void setAutoFollow(boolean follow) {
        autoFollow = follow;
        followButton.setSelected(follow);
        if (follow) {
            redraw();
        }
    }

    /** Removes a watched signal; called from the row's own remove button. */
    private void remove(PortReference reference) {
        controller.removeSignal(reference);
    }

    private void redraw() {
        List<LogicAnalyzerController.WatchedSignal> signals = controller.watchedSignals();
        long now = controller.currentTime().orElse(0);
        timeLabel.setText("t = " + formatTime(now));

        BusDisplayMode busMode = busModeBox.getValue();
        rebuildLabelColumn(signals, busMode);

        double rows = Math.max(1, signals.size());
        canvas.setHeight(rows * ROW_HEIGHT);
        double contentHeight = RULER_HEIGHT + rows * ROW_HEIGHT;
        waveformScroll.setPrefViewportHeight(contentHeight);
        waveformScroll.setMinViewportHeight(contentHeight);

        long span = timeSpan(signals, now);
        double pxPerPs = pixelsPerPicosecond > 0 ? pixelsPerPicosecond : fitPixelsPerPicosecond(span);
        double width = clamp(span * pxPerPs, MIN_CANVAS_WIDTH, MAX_CANVAS_WIDTH);
        canvas.setWidth(width);
        ruler.setWidth(width);

        paintRuler(now, pxPerPs, width);
        paint(signals, now, pxPerPs, busMode);

        if (autoFollow) {
            scrollingProgrammatically = true;
            waveformScroll.setHvalue(1.0);
            scrollingProgrammatically = false;
        }
    }

    private void rebuildLabelColumn(List<LogicAnalyzerController.WatchedSignal> signals, BusDisplayMode busMode) {
        labelColumn.getChildren().clear();
        // Matches the ruler's height above the waveforms, so row i's label lines up with
        // row i's waveform even though the waveform column has a ruler row the labels don't.
        Region rulerCorner = new Region();
        rulerCorner.setMinHeight(RULER_HEIGHT);
        rulerCorner.setPrefHeight(RULER_HEIGHT);
        labelColumn.getChildren().add(rulerCorner);
        for (LogicAnalyzerController.WatchedSignal signal : signals) {
            Label name = new Label(signal.label());
            name.getStyleClass().add("property-label");
            name.setMinWidth(70);

            Optional<SignalTrace> trace = controller.traceFor(signal.reference());
            Label value = new Label(trace.flatMap(this::currentValue)
                    .map(v -> displayValue(v, busMode))
                    .orElse("–"));
            value.setFont(Font.font("Monospace", 12));
            value.setTextFill(trace.flatMap(this::currentValue).map(this::colorFor).orElse(Theme.TEXT_MUTED));
            value.setMinWidth(46);

            Button removeButton = new Button("✕");
            removeButton.getStyleClass().add("tool-button");
            removeButton.setOnAction(event -> remove(signal.reference()));

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(4, name, value, spacer, removeButton);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPrefHeight(ROW_HEIGHT);
            row.setMinHeight(ROW_HEIGHT);
            row.setPadding(new Insets(0, 4, 0, 6));
            labelColumn.getChildren().add(row);
        }
        if (signals.isEmpty()) {
            Label hint = new Label("Right-click a wire or port to add a signal.");
            hint.getStyleClass().add("inspector-subtitle");
            hint.setWrapText(true);
            hint.setPadding(new Insets(8));
            labelColumn.getChildren().add(hint);
        }
    }

    private Optional<LogicVector> currentValue(SignalTrace trace) {
        List<SignalTransition> transitions = trace.transitions();
        return transitions.isEmpty() ? Optional.empty() : Optional.of(transitions.get(transitions.size() - 1).value());
    }

    private Color colorFor(LogicVector value) {
        if (value.width() == 1) {
            return Theme.signalColor(value.getBit(0));
        }
        if (value.isHighImpedance()) {
            return Theme.signalColor(LogicState.HIGH_IMPEDANCE);
        }
        return value.isFullyDefined() ? Theme.TEXT_PRIMARY : Theme.signalColor(LogicState.UNKNOWN);
    }

    private String displayValue(LogicVector value, BusDisplayMode busMode) {
        if (value.width() == 1) {
            return String.valueOf(value.getBit(0).symbol());
        }
        return formatBus(value, busMode);
    }

    private String formatBus(LogicVector value, BusDisplayMode mode) {
        if (value.isHighImpedance()) {
            return "Z";
        }
        if (!value.isFullyDefined()) {
            return "X";
        }
        return value.toUnsignedLong()
                .stream()
                .mapToObj(v -> switch (mode) {
                    case HEX -> "0x" + Long.toHexString(v).toUpperCase(Locale.ROOT);
                    case UNSIGNED -> String.valueOf(v);
                    case BIN -> value.toBinaryString();
                })
                .findFirst()
                .orElseGet(value::toBinaryString);
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

    // ------------------------------------------------------------------ ruler

    private void paintRuler(long now, double pxPerPs, double width) {
        GraphicsContext g = ruler.getGraphicsContext2D();
        ruler.setHeight(RULER_HEIGHT);
        g.setFill(Theme.CANVAS_BACKGROUND);
        g.fillRect(0, 0, width, RULER_HEIGHT);

        long earliest = now - Math.round(width / pxPerPs);
        long step = niceTickStep(pxPerPs);
        long firstTick = (earliest / step) * step;

        g.setFont(Font.font(10));
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.CENTER);
        for (long t = firstTick; t <= now + step; t += step) {
            double screenX = x(t, earliest, pxPerPs);
            if (screenX < 0 || screenX > width) {
                continue;
            }
            g.setStroke(Theme.GRID_MAJOR);
            g.setLineWidth(1);
            g.strokeLine(screenX, RULER_HEIGHT - 6, screenX, RULER_HEIGHT);
            g.setFill(Theme.TEXT_SECONDARY);
            g.fillText(formatTime(t), screenX + 2, RULER_HEIGHT / 2 - 2);
        }
    }

    /** A "nice" (1/2/5 x 10^n) tick step in picoseconds, at least {@link #MIN_PIXELS_PER_TICK} apart. */
    private long niceTickStep(double pxPerPs) {
        double rawStep = MIN_PIXELS_PER_TICK / pxPerPs;
        double magnitude = Math.pow(10, Math.floor(Math.log10(Math.max(1, rawStep))));
        double residual = rawStep / magnitude;
        double niceResidual = residual <= 1 ? 1 : residual <= 2 ? 2 : residual <= 5 ? 5 : 10;
        return Math.max(1, Math.round(niceResidual * magnitude));
    }

    private static String formatTime(long picoseconds) {
        double value;
        String unit;
        long magnitude = Math.abs(picoseconds);
        if (magnitude >= 1_000_000_000_000L) {
            value = picoseconds / 1e12;
            unit = "s";
        } else if (magnitude >= 1_000_000_000L) {
            value = picoseconds / 1e9;
            unit = "ms";
        } else if (magnitude >= 1_000_000L) {
            value = picoseconds / 1e6;
            unit = "us";
        } else if (magnitude >= 1_000L) {
            value = picoseconds / 1e3;
            unit = "ns";
        } else {
            value = picoseconds;
            unit = "ps";
        }
        String formatted = value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.2f", value);
        return formatted + " " + unit;
    }

    // ---------------------------------------------------------------- waveform

    private void paint(List<LogicAnalyzerController.WatchedSignal> signals, long now, double pxPerPs,
                       BusDisplayMode busMode) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        g.setFill(Theme.CANVAS_BACKGROUND);
        g.fillRect(0, 0, width, height);

        long earliest = now - Math.round(width / pxPerPs);
        long step = niceTickStep(pxPerPs);
        long firstTick = (earliest / step) * step;
        g.setLineWidth(1);
        for (long t = firstTick; t <= now + step; t += step) {
            double screenX = x(t, earliest, pxPerPs);
            g.setStroke(Theme.GRID_MINOR);
            g.strokeLine(screenX, 0, screenX, height);
        }

        for (int row = 0; row < signals.size(); row++) {
            LogicAnalyzerController.WatchedSignal signal = signals.get(row);
            Optional<SignalTrace> trace = controller.traceFor(signal.reference());
            double y = row * ROW_HEIGHT;
            g.setStroke(Theme.GRID_MINOR);
            g.setLineWidth(1);
            g.strokeLine(0, y + ROW_HEIGHT, width, y + ROW_HEIGHT);
            trace.ifPresent(t -> paintRow(g, t, earliest, now, pxPerPs, y, busMode));
        }
    }

    private void paintRow(GraphicsContext g, SignalTrace trace, long earliest, long now, double pxPerPs, double top,
                          BusDisplayMode busMode) {
        List<SignalTransition> transitions = trace.transitions();
        if (transitions.isEmpty()) {
            return;
        }
        if (trace.width().isSingleBit()) {
            paintDigitalRow(g, transitions, earliest, now, pxPerPs, top);
        } else {
            paintBusRow(g, transitions, earliest, now, pxPerPs, top, busMode);
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
                             double pxPerPs, double top, BusDisplayMode busMode) {
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
            Color color = colorFor(value);
            g.setStroke(color);
            g.strokeRect(x0, boxTop, x1 - x0, boxBottom - boxTop);
            if (x1 - x0 > 24) {
                g.setFill(color);
                g.fillText(formatBus(value, busMode), (x0 + x1) / 2, (boxTop + boxBottom) / 2, x1 - x0 - 4);
            }
        }
    }

    private double x(long time, long earliest, double pxPerPs) {
        return (time - earliest) * pxPerPs;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
