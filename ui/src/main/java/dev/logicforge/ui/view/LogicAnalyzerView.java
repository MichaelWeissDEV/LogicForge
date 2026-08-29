package dev.logicforge.ui.view;

import dev.logicforge.analyzer.SignalTrace;
import dev.logicforge.analyzer.SignalTransition;
import dev.logicforge.analyzer.WaveformSegment;
import dev.logicforge.analyzer.WaveformSegments;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.ui.analyzer.TimeGridCalculator;
import dev.logicforge.ui.analyzer.TimelineTransform;
import dev.logicforge.ui.edit.LogicAnalyzerController;
import dev.logicforge.ui.render.Theme;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/** Timestamp-accurate, edge-preserving digital waveform viewer for runtime endpoint probes. */
public final class LogicAnalyzerView extends BorderPane {

    public enum BusDisplayMode {
        HEX, BIN, UNSIGNED
    }

    private enum DragCursor {
        NONE, A, B
    }

    private static final double ROW_HEIGHT = 34;
    private static final double RULER_HEIGHT = 28;
    private static final double LABEL_COLUMN_WIDTH = 238;
    private static final double MIN_CANVAS_WIDTH = 240;
    private static final double MAX_CANVAS_WIDTH = 48_000;
    private static final double EDGE_HIT_PIXELS = 9;

    private final LogicAnalyzerController controller;
    private final java.util.function.Consumer<LogicAnalyzerController.WatchedSignal> showSignal;
    private final VBox labelColumn = new VBox();
    private final Canvas ruler = new Canvas(MIN_CANVAS_WIDTH, RULER_HEIGHT);
    private final Canvas canvas = new Canvas(MIN_CANVAS_WIDTH, ROW_HEIGHT);
    private final VBox waveformColumn = new VBox(ruler, canvas);
    private final ScrollPane waveformScroll = new ScrollPane(waveformColumn);

    private final Label timeLabel = new Label("t = 0 ps");
    private final Label zoomLabel = new Label("100%");
    private final Label measurementLabel = new Label("Enable cursors for Δt");
    private final ToggleButton captureButton = new ToggleButton("Capturing");
    private final ToggleButton followButton = new ToggleButton("Follow");
    private final ToggleButton gridButton = new ToggleButton("Grid");
    private final ToggleButton edgeSnapButton = new ToggleButton("Edges");
    private final ToggleButton cursorAButton = new ToggleButton("Cursor A");
    private final ToggleButton cursorBButton = new ToggleButton("Cursor B");
    private final ComboBox<BusDisplayMode> busModeBox =
            new ComboBox<>(FXCollections.observableArrayList(BusDisplayMode.values()));
    private final TextField searchField = new TextField();
    private final Set<LogicAnalyzerController.WatchedSignal> disabledSignals = new HashSet<>();
    private final Map<LogicAnalyzerController.WatchedSignal, Label> valueLabels = new HashMap<>();
    private final Map<LogicAnalyzerController.WatchedSignal, Label> stateDots = new HashMap<>();

    private List<LogicAnalyzerController.WatchedSignal> displayedSignals = List.of();
    private TimelineTransform timeline = new TimelineTransform(0, 1, MIN_CANVAS_WIDTH);
    private double zoomFactor = 1;
    private boolean autoFollow = true;
    private boolean scrollingProgrammatically;
    private boolean redrawPending;
    private Long cursorATime;
    private Long cursorBTime;
    private DragCursor draggedCursor = DragCursor.NONE;

    public LogicAnalyzerView(LogicAnalyzerController controller) {
        this(controller, ignored -> { });
    }

    public LogicAnalyzerView(
            LogicAnalyzerController controller,
            java.util.function.Consumer<LogicAnalyzerController.WatchedSignal> showSignal) {
        this.controller = java.util.Objects.requireNonNull(controller, "controller");
        this.showSignal = java.util.Objects.requireNonNull(showSignal, "showSignal");
        getStyleClass().addAll("side-panel", "logic-analyzer");

        setTop(buildHeader());
        setCenter(buildBody());
        installCursorHandlers(canvas);
        installCursorHandlers(ruler);

        waveformScroll.viewportBoundsProperty().addListener((ignored, oldBounds, newBounds) -> requestRedraw());
        waveformScroll.hvalueProperty().addListener((ignored, oldValue, newValue) -> {
            if (!scrollingProgrammatically) setAutoFollow(false);
            requestRedraw();
        });
        controller.addListener(this::requestRedraw);
        redraw();
    }

    private VBox buildHeader() {
        Label title = new Label("LOGIC ANALYZER");
        title.getStyleClass().add("panel-header");

        captureButton.setSelected(true);
        configureToggle(captureButton, () -> {
            controller.setCapturing(captureButton.isSelected());
            captureButton.setText(captureButton.isSelected() ? "Capturing" : "Stopped");
        });
        Button clearButton = toolButton("Clear", controller::clear);
        Button zoomOut = toolButton("−", () -> zoom(1 / 1.5));
        Button zoomIn = toolButton("+", () -> zoom(1.5));
        Button zoomFit = toolButton("Fit", () -> {
            zoomFactor = 1;
            requestRedraw();
        });

        followButton.setSelected(true);
        configureToggle(followButton, () -> setAutoFollow(followButton.isSelected()));
        gridButton.setSelected(true);
        configureToggle(gridButton, this::requestRedraw);
        edgeSnapButton.setSelected(true);
        configureToggle(edgeSnapButton, this::requestRedraw);
        configureCursorToggle(cursorAButton, true);
        configureCursorToggle(cursorBButton, false);

        busModeBox.setValue(BusDisplayMode.HEX);
        busModeBox.setButtonCell(new ListCell<>() {
            {
                getStyleClass().add("analyzer-mode-cell");
            }

            @Override
            protected void updateItem(BusDisplayMode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.name());
            }
        });
        busModeBox.getStyleClass().addAll("tool-button", "analyzer-mode");
        busModeBox.setOnAction(event -> requestRedraw());
        zoomLabel.getStyleClass().add("analyzer-zoom-label");

        searchField.setPromptText("Search signals…");
        searchField.getStyleClass().add("search-field");
        searchField.setPrefColumnCount(12);
        searchField.textProperty().addListener((ignored, oldValue, newValue) -> requestRedraw());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox controls = new HBox(6, title, captureButton, clearButton, separator(),
                zoomFit, zoomOut, zoomLabel, zoomIn, separator(), gridButton, edgeSnapButton,
                separator(), cursorAButton, cursorBButton, followButton, busModeBox, spacer,
                searchField, timeLabel);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.getStyleClass().addAll("toolbar", "analyzer-toolbar");

        measurementLabel.getStyleClass().add("analyzer-measurement");
        HBox measurements = new HBox(measurementLabel);
        measurements.setAlignment(Pos.CENTER_RIGHT);
        measurements.getStyleClass().add("analyzer-measurement-bar");
        return new VBox(controls, measurements);
    }

    private ScrollPane buildBody() {
        labelColumn.setFillWidth(true);
        labelColumn.setPrefWidth(LABEL_COLUMN_WIDTH);
        labelColumn.setMinWidth(LABEL_COLUMN_WIDTH);
        labelColumn.setMaxWidth(LABEL_COLUMN_WIDTH);
        labelColumn.getStyleClass().add("analyzer-channel-list");
        waveformColumn.getStyleClass().add("analyzer-waveform");

        waveformScroll.setFitToHeight(false);
        waveformScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        waveformScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        HBox.setHgrow(waveformScroll, Priority.ALWAYS);

        HBox row = new HBox(labelColumn, waveformScroll);
        ScrollPane outer = new ScrollPane(row);
        outer.setFitToWidth(true);
        outer.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        outer.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        outer.getStyleClass().add("analyzer-body");
        return outer;
    }

    private void requestRedraw() {
        if (redrawPending) return;
        redrawPending = true;
        Platform.runLater(() -> {
            redrawPending = false;
            redraw();
        });
    }

    private void zoom(double factor) {
        zoomFactor = clamp(zoomFactor * factor, 1, 64);
        setAutoFollow(false);
        requestRedraw();
    }

    private void setAutoFollow(boolean follow) {
        autoFollow = follow;
        followButton.setSelected(follow);
        if (follow) requestRedraw();
    }

    private void configureToggle(ToggleButton button, Runnable action) {
        button.getStyleClass().add("tool-button");
        button.setOnAction(event -> action.run());
    }

    private void configureCursorToggle(ToggleButton button, boolean cursorA) {
        configureToggle(button, () -> {
            if (button.isSelected()) {
                long initial = timeline.xToTime(timeline.pixelWidth() * (cursorA ? 0.35 : 0.65));
                if (cursorA && cursorATime == null) cursorATime = initial;
                if (!cursorA && cursorBTime == null) cursorBTime = initial;
            }
            requestRedraw();
        });
    }

    private Button toolButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("tool-button");
        button.setOnAction(event -> action.run());
        return button;
    }

    private Region separator() {
        Region separator = new Region();
        separator.getStyleClass().add("toolbar-separator");
        HBox.setMargin(separator, new Insets(0, 4, 0, 4));
        return separator;
    }

    private void redraw() {
        List<LogicAnalyzerController.WatchedSignal> allSignals = controller.watchedSignals();
        String filter = searchField.getText() == null
                ? "" : searchField.getText().strip().toLowerCase(Locale.ROOT);
        List<LogicAnalyzerController.WatchedSignal> signals = allSignals.stream()
                .filter(signal -> filter.isEmpty() || signal.label().toLowerCase(Locale.ROOT).contains(filter))
                .toList();
        long now = controller.currentTime().orElse(0);
        timeLabel.setText("t = " + TimeGridCalculator.formatTime(now));

        refreshLabelColumn(signals, busModeBox.getValue());
        double rows = Math.max(1, signals.size());
        canvas.setHeight(rows * ROW_HEIGHT);
        double contentHeight = RULER_HEIGHT + rows * ROW_HEIGHT;
        waveformScroll.setPrefViewportHeight(contentHeight);
        waveformScroll.setMinViewportHeight(contentHeight);

        long start = earliestTime(signals, now);
        long end = Math.max(start + 1, latestTime(signals, now));
        double viewportWidth = Math.max(MIN_CANVAS_WIDTH, waveformScroll.getViewportBounds().getWidth());
        double width = clamp(viewportWidth * zoomFactor, viewportWidth, MAX_CANVAS_WIDTH);
        timeline = new TimelineTransform(start, end, width);
        canvas.setWidth(width);
        ruler.setWidth(width);
        zoomLabel.setText(Math.round(width / viewportWidth * 100) + "%");

        if (autoFollow) {
            scrollingProgrammatically = true;
            waveformScroll.setHvalue(1);
            scrollingProgrammatically = false;
        }
        paintRuler();
        paintWaveforms(signals, end, busModeBox.getValue());
        updateMeasurement();
    }

    private long earliestTime(List<LogicAnalyzerController.WatchedSignal> signals, long now) {
        long earliest = now;
        for (LogicAnalyzerController.WatchedSignal signal : signals) {
            List<SignalTransition> transitions = controller.traceFor(signal)
                    .map(SignalTrace::transitions).orElse(List.of());
            if (!transitions.isEmpty()) earliest = Math.min(earliest, transitions.getFirst().time());
        }
        return earliest;
    }

    private long latestTime(List<LogicAnalyzerController.WatchedSignal> signals, long now) {
        long latest = now;
        for (LogicAnalyzerController.WatchedSignal signal : signals) {
            List<SignalTransition> transitions = controller.traceFor(signal)
                    .map(SignalTrace::transitions).orElse(List.of());
            if (!transitions.isEmpty()) latest = Math.max(latest, transitions.getLast().time());
        }
        return latest;
    }

    private void refreshLabelColumn(
            List<LogicAnalyzerController.WatchedSignal> signals, BusDisplayMode busMode) {
        if (!signals.equals(displayedSignals)) {
            displayedSignals = List.copyOf(signals);
            rebuildLabelColumn(signals);
        }
        for (LogicAnalyzerController.WatchedSignal signal : signals) {
            Optional<LogicVector> value = controller.traceFor(signal).flatMap(this::currentValue);
            Label valueLabel = valueLabels.get(signal);
            Label dot = stateDots.get(signal);
            if (valueLabel != null) {
                valueLabel.setText(value.map(v -> displayValue(v, busMode)).orElse("–"));
                valueLabel.setTextFill(value.map(this::colorFor).orElse(Theme.TEXT_MUTED));
            }
            if (dot != null) dot.setTextFill(value.map(this::colorFor).orElse(Theme.TEXT_MUTED));
        }
    }

    private void rebuildLabelColumn(List<LogicAnalyzerController.WatchedSignal> signals) {
        labelColumn.getChildren().clear();
        valueLabels.clear();
        stateDots.clear();
        Region corner = new Region();
        corner.setMinHeight(RULER_HEIGHT);
        corner.setPrefHeight(RULER_HEIGHT);
        corner.getStyleClass().add("analyzer-ruler-corner");
        labelColumn.getChildren().add(corner);

        for (int index = 0; index < signals.size(); index++) {
            LogicAnalyzerController.WatchedSignal signal = signals.get(index);
            CheckBox enabled = new CheckBox(String.format(Locale.ROOT, "%02d", index + 1));
            enabled.setSelected(!disabledSignals.contains(signal));
            enabled.getStyleClass().add("analyzer-channel-toggle");
            enabled.setOnAction(event -> {
                if (enabled.isSelected()) disabledSignals.remove(signal);
                else disabledSignals.add(signal);
                requestRedraw();
            });

            Label dot = new Label("●");
            dot.getStyleClass().add("analyzer-channel-dot");
            stateDots.put(signal, dot);
            Label name = new Label(signal.label());
            name.setTextOverrun(OverrunStyle.ELLIPSIS);
            name.setMaxWidth(104);
            name.setTooltip(new Tooltip(signal.hierarchyPath()));
            name.getStyleClass().add("analyzer-channel-name");

            Label value = new Label("–");
            value.getStyleClass().add("analyzer-channel-value");
            valueLabels.put(signal, value);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button showButton = toolButton("↗", () -> showSignal.accept(signal));
            showButton.setTooltip(new Tooltip("Show source endpoint"));
            showButton.getStyleClass().add("analyzer-row-action");
            Button removeButton = toolButton("×", () -> controller.removeSignal(signal));
            removeButton.setTooltip(new Tooltip("Remove channel"));
            removeButton.getStyleClass().add("analyzer-row-action");

            HBox row = new HBox(4, enabled, dot, name, value, spacer, showButton, removeButton);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setMinHeight(ROW_HEIGHT);
            row.setPrefHeight(ROW_HEIGHT);
            row.setMaxHeight(ROW_HEIGHT);
            row.getStyleClass().add("analyzer-channel-row");
            labelColumn.getChildren().add(row);
        }
        if (signals.isEmpty()) {
            Label hint = new Label(controller.watchedSignals().isEmpty()
                    ? "Right-click a wire or port to add a runtime signal."
                    : "No signals match the current filter.");
            hint.getStyleClass().add("inspector-subtitle");
            hint.setWrapText(true);
            hint.setPadding(new Insets(12));
            labelColumn.getChildren().add(hint);
        }
    }

    private Optional<LogicVector> currentValue(SignalTrace trace) {
        List<SignalTransition> transitions = trace.transitions();
        return transitions.isEmpty() ? Optional.empty() : Optional.of(transitions.getLast().value());
    }

    private Color colorFor(LogicVector value) {
        if (value.width() == 1) return Theme.signalColor(value.getBit(0));
        if (value.isHighImpedance()) return Theme.signalColor(LogicState.HIGH_IMPEDANCE);
        return value.isFullyDefined() ? Theme.BUS_DEFINED : Theme.signalColor(LogicState.UNKNOWN);
    }

    private String displayValue(LogicVector value, BusDisplayMode mode) {
        if (value.width() == 1) return String.valueOf(value.getBit(0).symbol());
        if (value.isHighImpedance()) return "Z";
        if (!value.isFullyDefined()) return "X";
        return value.toUnsignedLong().stream().mapToObj(unsigned -> switch (mode) {
            case HEX -> "0x" + Long.toHexString(unsigned).toUpperCase(Locale.ROOT);
            case UNSIGNED -> String.valueOf(unsigned);
            case BIN -> value.toBinaryString();
        }).findFirst().orElseGet(value::toBinaryString);
    }

    private void paintRuler() {
        GraphicsContext g = ruler.getGraphicsContext2D();
        g.setFill(Theme.CANVAS_BACKGROUND);
        g.fillRect(0, 0, ruler.getWidth(), RULER_HEIGHT);
        double[] visiblePixels = visiblePixelRange();
        long visibleStart = timeline.xToTime(visiblePixels[0]);
        long visibleEnd = timeline.xToTime(visiblePixels[1]);
        TimeGridCalculator.Grid grid = TimeGridCalculator.calculate(timeline);

        g.setFont(Font.font("System", 10));
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.CENTER);
        if (gridButton.isSelected()) {
            forEachTick(visibleStart, visibleEnd, grid.majorStep(), time -> {
                double x = crisp(timeline.timeToX(time));
                g.setStroke(Theme.GRID_MAJOR);
                g.strokeLine(x, RULER_HEIGHT - 8, x, RULER_HEIGHT);
                g.setFill(Theme.TEXT_SECONDARY);
                g.fillText(TimeGridCalculator.formatTime(time), x + 4, RULER_HEIGHT / 2 - 2);
            });
        }
        paintCursorOnRuler(g, cursorAButton.isSelected(), cursorATime, "A", Theme.CURSOR_A);
        paintCursorOnRuler(g, cursorBButton.isSelected(), cursorBTime, "B", Theme.CURSOR_B);
    }

    private void paintWaveforms(
            List<LogicAnalyzerController.WatchedSignal> signals, long captureEnd, BusDisplayMode busMode) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        g.setFill(Theme.CANVAS_BACKGROUND);
        g.fillRect(0, 0, width, height);

        double[] visiblePixels = visiblePixelRange();
        long visibleStart = timeline.xToTime(visiblePixels[0]);
        long visibleEnd = timeline.xToTime(visiblePixels[1]);
        paintGrid(g, height, visibleStart, visibleEnd);

        for (int row = 0; row < signals.size(); row++) {
            double top = row * ROW_HEIGHT;
            g.setStroke(Theme.GRID_MINOR);
            g.setLineWidth(1);
            g.strokeLine(visiblePixels[0], top + ROW_HEIGHT, visiblePixels[1], top + ROW_HEIGHT);
            LogicAnalyzerController.WatchedSignal signal = signals.get(row);
            if (disabledSignals.contains(signal)) continue;
            controller.traceFor(signal).ifPresent(trace -> {
                List<WaveformSegment> segments = WaveformSegments.visible(
                        WaveformSegments.fromTransitions(trace.transitions(), captureEnd),
                        visibleStart, visibleEnd);
                if (trace.width().isSingleBit()) paintDigitalRow(g, segments, top);
                else paintBusRow(g, segments, top, busMode);
            });
        }

        paintCursorLine(g, cursorAButton.isSelected(), cursorATime, Theme.CURSOR_A, height);
        paintCursorLine(g, cursorBButton.isSelected(), cursorBTime, Theme.CURSOR_B, height);
    }

    private void paintGrid(GraphicsContext g, double height, long visibleStart, long visibleEnd) {
        if (!gridButton.isSelected()) return;
        TimeGridCalculator.Grid grid = TimeGridCalculator.calculate(timeline);
        forEachTick(visibleStart, visibleEnd, grid.minorStep(), time -> {
            boolean major = Math.floorMod(time, grid.majorStep()) == 0;
            g.setStroke(major ? Theme.GRID_MAJOR : Theme.GRID_MINOR);
            g.setLineWidth(1);
            double x = crisp(timeline.timeToX(time));
            g.strokeLine(x, 0, x, height);
        });
    }

    private void paintDigitalRow(GraphicsContext g, List<WaveformSegment> segments, double top) {
        double highY = crisp(top + 7);
        double lowY = crisp(top + ROW_HEIGHT - 7);
        double midY = crisp(top + ROW_HEIGHT / 2);
        g.setStroke(Theme.DIGITAL_REFERENCE);
        g.setLineWidth(1);
        double[] visible = visiblePixelRange();
        g.strokeLine(visible[0], highY, visible[1], highY);
        g.strokeLine(visible[0], lowY, visible[1], lowY);

        LogicState previous = null;
        for (WaveformSegment segment : segments) {
            LogicState state = segment.value().getBit(0);
            double rawX0 = timeline.timeToX(segment.startTime());
            double rawX1 = timeline.timeToX(segment.endTime());
            double x0 = crisp(rawX0);
            double x1 = crisp(Math.max(rawX1, rawX0 + 1.5));
            if (previous != null) {
                paintTransition(g, x0, yFor(previous, highY, lowY, midY),
                        yFor(state, highY, lowY, midY), state);
            }
            paintDigitalSegment(g, state, x0, x1, highY, lowY, midY);
            if (rawX1 - rawX0 < 1.5 || state == LogicState.UNKNOWN
                    || state == LogicState.HIGH_IMPEDANCE) {
                g.setFill(Theme.signalColor(state));
                g.fillOval(x0 - 1.5, midY - 1.5, 3, 3);
            }
            previous = state;
        }
    }

    private void paintTransition(GraphicsContext g, double x, double fromY, double toY, LogicState state) {
        g.setLineDashes();
        g.setStroke(Theme.signalColor(state));
        g.setLineWidth(2);
        if (Math.abs(fromY - toY) < 0.5) g.strokeLine(x, fromY - 3, x, fromY + 3);
        else g.strokeLine(x, fromY, x, toY);
    }

    private void paintDigitalSegment(
            GraphicsContext g, LogicState state, double x0, double x1,
            double highY, double lowY, double midY) {
        g.setStroke(Theme.signalColor(state));
        g.setFill(Theme.signalColor(state));
        g.setLineWidth(2);
        g.setLineDashes();
        switch (state) {
            case ONE -> g.strokeLine(x0, highY, x1, highY);
            case ZERO -> g.strokeLine(x0, lowY, x1, lowY);
            case HIGH_IMPEDANCE -> {
                g.setLineWidth(1.4);
                g.setLineDashes(4, 3);
                g.strokeLine(x0, midY - 3, x1, midY - 3);
                g.strokeLine(x0, midY + 3, x1, midY + 3);
                g.setLineDashes();
                if (x1 - x0 > 18) drawStateLabel(g, "Z", x0, x1, midY);
            }
            case UNKNOWN -> {
                double width = Math.max(2, x1 - x0);
                g.save();
                g.setGlobalAlpha(0.16);
                g.fillRect(x0, midY - 6, width, 12);
                g.restore();
                g.setLineWidth(1.2);
                for (double x = x0; x < x1; x += 7) {
                    g.strokeLine(x, midY - 6, Math.min(x + 7, x1), midY + 6);
                    g.strokeLine(x, midY + 6, Math.min(x + 7, x1), midY - 6);
                }
                if (x1 - x0 > 18) drawStateLabel(g, "X", x0, x1, midY);
            }
        }
        g.setLineDashes();
    }

    private void drawStateLabel(GraphicsContext g, String label, double x0, double x1, double y) {
        g.setFont(Font.font("Monospace", 9));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(label, (x0 + x1) / 2, y);
    }

    private double yFor(LogicState state, double highY, double lowY, double midY) {
        return switch (state) {
            case ONE -> highY;
            case ZERO -> lowY;
            case UNKNOWN, HIGH_IMPEDANCE -> midY;
        };
    }

    private void paintBusRow(
            GraphicsContext g, List<WaveformSegment> segments, double top, BusDisplayMode busMode) {
        double boxTop = top + 7;
        double boxBottom = top + ROW_HEIGHT - 7;
        g.setFont(Font.font("Monospace", 10));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        for (WaveformSegment segment : segments) {
            double x0 = timeline.timeToX(segment.startTime());
            double x1 = Math.max(x0 + 2, timeline.timeToX(segment.endTime()));
            Color color = colorFor(segment.value());
            g.setStroke(color);
            g.setLineWidth(1.5);
            g.strokeRect(x0, boxTop, x1 - x0, boxBottom - boxTop);
            if (x1 - x0 > 28) {
                g.setFill(color);
                g.fillText(displayValue(segment.value(), busMode),
                        (x0 + x1) / 2, (boxTop + boxBottom) / 2, x1 - x0 - 5);
            }
        }
    }

    private void installCursorHandlers(Node node) {
        node.addEventHandler(MouseEvent.MOUSE_MOVED, event ->
                node.setCursor(cursorNear(event.getX()) != DragCursor.NONE ? Cursor.H_RESIZE : Cursor.CROSSHAIR));
        node.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            draggedCursor = cursorNear(event.getX());
            if (draggedCursor == DragCursor.NONE) {
                if (cursorAButton.isSelected() && !cursorBButton.isSelected()) draggedCursor = DragCursor.A;
                else if (cursorBButton.isSelected() && !cursorAButton.isSelected()) draggedCursor = DragCursor.B;
                else if (cursorAButton.isSelected() && cursorBButton.isSelected()) {
                    draggedCursor = distanceToCursor(event.getX(), cursorATime)
                            <= distanceToCursor(event.getX(), cursorBTime) ? DragCursor.A : DragCursor.B;
                }
            }
            moveDraggedCursor(event.getX());
            event.consume();
        });
        node.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            moveDraggedCursor(event.getX());
            event.consume();
        });
        node.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> draggedCursor = DragCursor.NONE);
    }

    private DragCursor cursorNear(double x) {
        if (cursorAButton.isSelected() && distanceToCursor(x, cursorATime) <= EDGE_HIT_PIXELS) return DragCursor.A;
        if (cursorBButton.isSelected() && distanceToCursor(x, cursorBTime) <= EDGE_HIT_PIXELS) return DragCursor.B;
        return DragCursor.NONE;
    }

    private double distanceToCursor(double x, Long time) {
        return time == null ? Double.POSITIVE_INFINITY : Math.abs(x - timeline.timeToX(time));
    }

    private void moveDraggedCursor(double x) {
        if (draggedCursor == DragCursor.NONE) return;
        long time = timeline.xToTime(clamp(x, 0, timeline.pixelWidth()));
        time = snapTime(time);
        if (draggedCursor == DragCursor.A) cursorATime = time;
        else cursorBTime = time;
        requestRedraw();
    }

    private long snapTime(long requested) {
        long best = requested;
        double bestDistance = EDGE_HIT_PIXELS + 1;
        if (edgeSnapButton.isSelected()) {
            for (LogicAnalyzerController.WatchedSignal signal : displayedSignals) {
                for (SignalTransition transition : controller.traceFor(signal)
                        .map(SignalTrace::transitions).orElse(List.of())) {
                    double distance = Math.abs(
                            timeline.timeToX(transition.time()) - timeline.timeToX(requested));
                    if (distance < bestDistance) {
                        best = transition.time();
                        bestDistance = distance;
                    }
                }
            }
        }
        if (bestDistance <= EDGE_HIT_PIXELS) return best;
        if (gridButton.isSelected()) {
            long step = TimeGridCalculator.calculate(timeline).minorStep();
            return Math.round(requested / (double) step) * step;
        }
        return requested;
    }

    private void paintCursorOnRuler(
            GraphicsContext g, boolean enabled, Long time, String label, Color color) {
        if (!enabled || time == null) return;
        double x = crisp(timeline.timeToX(time));
        g.setStroke(color);
        g.setFill(color);
        g.setLineWidth(1.3);
        g.strokeLine(x, 0, x, RULER_HEIGHT);
        g.fillRoundRect(x + 2, 2, 15, 13, 3, 3);
        g.setFill(Theme.CANVAS_BACKGROUND);
        g.setFont(Font.font("System", 9));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(label, x + 9.5, 8.5);
    }

    private void paintCursorLine(GraphicsContext g, boolean enabled, Long time, Color color, double height) {
        if (!enabled || time == null) return;
        double x = crisp(timeline.timeToX(time));
        g.setStroke(color);
        g.setLineWidth(1.3);
        g.setLineDashes(5, 3);
        g.strokeLine(x, 0, x, height);
        g.setLineDashes();
    }

    private void updateMeasurement() {
        List<String> parts = new ArrayList<>();
        if (cursorAButton.isSelected() && cursorATime != null) {
            parts.add("A: " + TimeGridCalculator.formatTime(cursorATime));
        }
        if (cursorBButton.isSelected() && cursorBTime != null) {
            parts.add("B: " + TimeGridCalculator.formatTime(cursorBTime));
        }
        if (cursorAButton.isSelected() && cursorBButton.isSelected()
                && cursorATime != null && cursorBTime != null) {
            long delta = Math.abs(cursorBTime - cursorATime);
            parts.add("Δt: " + TimeGridCalculator.formatTime(delta));
            parts.add("1 / Δt: " + TimeGridCalculator.formatFrequency(delta));
        }
        measurementLabel.setText(parts.isEmpty()
                ? "Enable cursors for Δt" : String.join("   |   ", parts));
    }

    private double[] visiblePixelRange() {
        double viewport = Math.max(MIN_CANVAS_WIDTH, waveformScroll.getViewportBounds().getWidth());
        double overflow = Math.max(0, timeline.pixelWidth() - viewport);
        double start = clamp(waveformScroll.getHvalue(), 0, 1) * overflow;
        return new double[]{start, Math.min(timeline.pixelWidth(), start + viewport)};
    }

    private void forEachTick(long start, long end, long step, java.util.function.LongConsumer consumer) {
        long first = Math.floorDiv(start, step) * step;
        for (long time = first; time <= end; ) {
            if (time >= start) consumer.accept(time);
            if (Long.MAX_VALUE - step < time) break;
            time += step;
        }
    }

    private static double crisp(double value) {
        return Math.floor(value) + 0.5;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
