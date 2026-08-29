package dev.logicforge.ui.view;

import dev.logicforge.circuit.chip.ChipDefinition;
import dev.logicforge.circuit.chip.ChipDisplayMode;
import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.chip.ElectricalPinType;
import dev.logicforge.circuit.chip.PackagePin;
import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.component.ParameterSpec;
import dev.logicforge.circuit.component.PortSpec;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.ElectricalEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.PortDisplayMode;
import dev.logicforge.library.LibraryParameters;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.SimulationTime;
import dev.logicforge.ui.command.ChangeParameterCommand;
import dev.logicforge.ui.command.SetChipDisplayModeCommand;
import dev.logicforge.ui.command.SetChipReferenceDesignatorCommand;
import dev.logicforge.ui.command.SetLabelCommand;
import dev.logicforge.ui.command.SetPortDisplayModeCommand;
import dev.logicforge.ui.edit.CircuitEditor;
import dev.logicforge.ui.render.PinLayoutView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import dev.logicforge.structures.ImplementationLevel;
import dev.logicforge.structures.StructuralImplementationRegistry;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The property panel.
 *
 * <p>Editors are built from the parameter declarations of a component definition, so the
 * inspector contains no knowledge about any particular component: a new component with new
 * properties gets a working editor for free. Changing a value goes through the undo
 * history like every other edit.
 */
public final class InspectorView extends VBox {

    private final CircuitEditor editor;
    private final Consumer<ComponentInstance> inspectInternals;
    private final Consumer<ComponentInstance> studyImplementation;
    private final StructuralImplementationRegistry structuralImplementations =
            StructuralImplementationRegistry.standard();
    private final VBox body = new VBox();

    public InspectorView(CircuitEditor editor) {
        this(editor, instance -> editor.openSubcircuit(instance), instance -> { });
    }

    public InspectorView(CircuitEditor editor, Consumer<ComponentInstance> inspectInternals,
                         Consumer<ComponentInstance> studyImplementation) {
        this.editor = editor;
        this.inspectInternals = inspectInternals;
        this.studyImplementation = studyImplementation;
        getStyleClass().add("side-panel");

        Label header = new Label("INSPECTOR");
        header.getStyleClass().add("panel-header");

        body.getStyleClass().add("panel-body");
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(header, scroll);
        editor.selection().addListener(this::refresh);
        editor.addChangeListener(this::refresh);
        refresh();
    }

    private void refresh() {
        body.getChildren().clear();
        int components = editor.selection().components().size();
        int chips = editor.selection().chips().size();
        int total = components + chips;
        if (total == 0) {
            showCircuitProperties();
        } else if (total == 1 && components == 1) {
            UUID id = editor.selection().components().iterator().next();
            editor.document().component(id).ifPresentOrElse(this::showComponent, this::showCircuitProperties);
        } else if (total == 1) {
            UUID id = editor.selection().chips().iterator().next();
            editor.document().chip(id).ifPresentOrElse(this::showChip, this::showCircuitProperties);
        } else {
            showMultiSelection(components, chips);
        }
    }

    private void showCircuitProperties() {
        body.getChildren().add(title("Circuit"));
        body.getChildren().add(subtitle(editor.document().metadata().name()));
        body.getChildren().add(spacer());
        body.getChildren().add(readOnly("Components", String.valueOf(editor.document().componentCount())));
        body.getChildren().add(readOnly("Wires", String.valueOf(editor.document().connectionCount())));
        editor.compilation().ifPresent(compilation ->
                body.getChildren().add(readOnly("Nets", String.valueOf(compilation.circuit().netCount()))));
        editor.compileError().ifPresent(message -> {
            Label error = new Label(message);
            error.getStyleClass().addAll("inspector-subtitle", "status-error");
            error.setWrapText(true);
            body.getChildren().addAll(spacer(), error);
        });
    }

    private void showMultiSelection(int components, int chips) {
        StringBuilder label = new StringBuilder();
        if (components > 0) {
            label.append(components).append(components == 1 ? " component" : " components");
        }
        if (components > 0 && chips > 0) {
            label.append(" + ");
        }
        if (chips > 0) {
            label.append(chips).append(chips == 1 ? " chip" : " chips");
        }
        body.getChildren().add(title(label.toString()));
        body.getChildren().add(subtitle("Move, rotate or delete them together."));
    }

    // ---------------------------------------------------------------- chips

    private void showChip(ChipInstance instance) {
        Optional<ChipDefinition> definition = editor.chipDefinitionOf(instance);
        if (definition.isEmpty()) {
            body.getChildren().add(title("Unknown chip"));
            body.getChildren().add(subtitle(instance.chipDefinitionId()));
            return;
        }
        ChipDefinition chip = definition.get();
        body.getChildren().add(title(chip.metadata().partNumber()));
        body.getChildren().add(readOnly("Family", chip.metadata().family()));
        body.getChildren().add(subtitle(chip.metadata().summary()));
        body.getChildren().add(readOnly("Package", chip.packageDefinition().type().name()));
        body.getChildren().add(readOnly("Pin count", String.valueOf(chip.packageDefinition().pins().size())));
        body.getChildren().add(subtitle(
                "Digital function model, implicitly powered — no analog supply or transistor-level simulation."));
        body.getChildren().add(spacer());

        body.getChildren().add(propertyLabel("Reference designator"));
        TextField designator = new TextField(instance.referenceDesignator());
        designator.setOnAction(event -> commitDesignator(instance, designator.getText()));
        designator.focusedProperty().addListener((observable, was, focused) -> {
            if (!focused) {
                commitDesignator(instance, designator.getText());
            }
        });
        body.getChildren().add(designator);

        body.getChildren().add(spacer());
        body.getChildren().add(propertyLabel("Display mode"));
        ComboBox<ChipDisplayMode> displayMode = new ComboBox<>();
        displayMode.getItems().setAll(ChipDisplayMode.PACKAGE, ChipDisplayMode.SYMBOL);
        displayMode.setValue(instance.displayMode() == ChipDisplayMode.STRUCTURAL
                ? ChipDisplayMode.PACKAGE : instance.displayMode());
        displayMode.setMaxWidth(Double.MAX_VALUE);
        displayMode.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ChipDisplayMode mode) {
                return mode == ChipDisplayMode.SYMBOL ? "Symbol" : "Package";
            }
            @Override public ChipDisplayMode fromString(String value) {
                return "Symbol".equals(value) ? ChipDisplayMode.SYMBOL : ChipDisplayMode.PACKAGE;
            }
        });
        displayMode.valueProperty().addListener((observable, oldMode, newMode) -> {
            if (newMode != null && newMode != oldMode) {
                editor.document().chip(instance.id()).ifPresent(current ->
                        editor.execute(new SetChipDisplayModeCommand(editor.document(), current, newMode)));
            }
        });
        body.getChildren().add(displayMode);

        body.getChildren().add(spacer());
        body.getChildren().add(sectionHeader("PINS"));
        for (PackagePin pin : chip.packageDefinition().pins()) {
            body.getChildren().add(chipPinRow(instance, chip, pin));
        }

        body.getChildren().add(spacer());
        body.getChildren().add(sectionHeader("PLACEMENT"));
        body.getChildren().add(readOnly("Rotation", instance.rotation().degrees() + "°"));
        body.getChildren().add(readOnly("Position",
                (int) instance.position().x() + ", " + (int) instance.position().y()));
    }

    /** One physical pin: number, name, electrical role, its logical mapping and live value. */
    private javafx.scene.Node chipPinRow(ChipInstance instance, ChipDefinition chip, PackagePin pin) {
        String mapping = chip.logicalMapping(pin.number())
                .map(m -> m.unitName() + "." + m.portName() + (m.bitIndex() >= 0 ? "[" + m.bitIndex() + "]" : ""))
                .orElse("—");
        String value = pin.electricalType() == ElectricalPinType.SIGNAL
                ? editor.valueAt(new ElectricalEndpoint.ChipPinEndpoint(instance.id(), pin.number()))
                        .map(this::formatState).orElse("–")
                : "—";
        Label heading = new Label(pin.number() + "  " + pin.name() + "   " + pin.electricalType()
                + "   " + mapping + "   " + value);
        heading.getStyleClass().add("property-label");
        heading.setWrapText(true);
        VBox row = new VBox(heading);
        VBox.setMargin(row, new Insets(2, 0, 0, 0));
        return row;
    }

    private void commitDesignator(ChipInstance instance, String designator) {
        editor.document().chip(instance.id()).ifPresent(current -> {
            String trimmed = designator.strip();
            if (!trimmed.isEmpty() && !current.referenceDesignator().equals(trimmed)) {
                editor.execute(new SetChipReferenceDesignatorCommand(editor.document(), current, trimmed));
            }
        });
    }

    private void showComponent(ComponentInstance instance) {
        Optional<ComponentDefinition> definition = editor.definitionOf(instance);
        if (definition.isEmpty()) {
            body.getChildren().add(title("Unknown component"));
            body.getChildren().add(subtitle(instance.definitionId()));
            return;
        }
        ComponentDefinition component = definition.get();
        body.getChildren().add(title(component.displayName()));
        body.getChildren().add(readOnly("Category", component.category().displayName()));
        body.getChildren().add(subtitle(component.description()));
        if (dev.logicforge.circuit.document.SubcircuitSupport
                .isInstanceDefinition(instance.definitionId())) {
            String child = dev.logicforge.circuit.document.SubcircuitSupport
                    .circuitName(instance.definitionId());
            body.getChildren().add(readOnly("Source circuit", child));
            String segment = instance.label().isBlank() ? instance.id().toString() : instance.label();
            body.getChildren().add(readOnly("Instance path",
                    editor.activeInstancePath().map(path -> path + "/" + segment)
                            .orElse(segment + " (not instantiated)")));
            Button open = new Button("Open Internals");
            open.setOnAction(event -> editor.openSubcircuit(instance));
            Button inspect = new Button("Inspect Internals");
            inspect.setOnAction(event -> inspectInternals.accept(instance));
            body.getChildren().addAll(open, inspect);
        }
        structuralImplementations.find(component.id(), instance.parameters(),
                ImplementationLevel.GATE).ifPresent(implementation -> {
                    Button study = new Button("Study Implementation");
                    study.setOnAction(event -> studyImplementation.accept(instance));
                    body.getChildren().add(study);
                });
        body.getChildren().add(spacer());

        body.getChildren().add(propertyLabel("Port display"));
        ComboBox<PortDisplayMode> portDisplay = new ComboBox<>();
        portDisplay.getItems().setAll(PortDisplayMode.COMPACT, PortDisplayMode.EXPANDED);
        portDisplay.setValue(instance.portDisplayMode());
        portDisplay.setMaxWidth(Double.MAX_VALUE);
        portDisplay.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(PortDisplayMode mode) {
                return mode == PortDisplayMode.EXPANDED ? "Expanded" : "Compact";
            }
            @Override public PortDisplayMode fromString(String value) {
                return "Expanded".equals(value) ? PortDisplayMode.EXPANDED : PortDisplayMode.COMPACT;
            }
        });
        portDisplay.valueProperty().addListener((observable, oldMode, newMode) -> {
            if (newMode != null && newMode != oldMode) {
                editor.document().component(instance.id()).ifPresent(current ->
                        editor.execute(new SetPortDisplayModeCommand(
                                editor.document(), current, newMode)));
            }
        });
        body.getChildren().add(portDisplay);

        body.getChildren().add(spacer());

        body.getChildren().add(sectionHeader("PIN LAYOUT"));
        body.getChildren().add(new PinLayoutView(component, instance, 220));

        body.getChildren().add(spacer());
        body.getChildren().add(sectionHeader("PINS"));
        for (PortSpec port : component.ports(instance.parameters())) {
            body.getChildren().add(pinRow(port));
            if (instance.portDisplayMode() == PortDisplayMode.EXPANDED
                    && !port.width().isSingleBit()) {
                for (int bit = 0; bit < port.width().bits(); bit++) {
                    Label bitRow = new Label("    " + port.name() + "[" + bit + "]");
                    bitRow.getStyleClass().add("inspector-subtitle");
                    body.getChildren().add(bitRow);
                }
            }
        }

        body.getChildren().add(spacer());
        body.getChildren().add(propertyLabel("Label"));
        TextField label = new TextField(instance.label());
        label.setPromptText("unnamed");
        label.setOnAction(event -> commitLabel(instance, label.getText()));
        label.focusedProperty().addListener((observable, was, focused) -> {
            if (!focused) {
                commitLabel(instance, label.getText());
            }
        });
        body.getChildren().add(label);

        if (!component.parameters().isEmpty()) {
            body.getChildren().add(spacer());
            body.getChildren().add(sectionHeader("PARAMETERS"));
            for (ParameterSpec<?> parameter : component.parameters()) {
                body.getChildren().add(propertyLabel(parameter.displayName()));
                body.getChildren().add(editorFor(instance, component, parameter));
            }
        }

        if (component.id().equals("source.clock")) {
            showClockTiming(instance);
        }
        
        if (editor.memorySnapshot(instance.id()).isPresent()) {
            body.getChildren().add(spacer());
            Button memBtn = new Button("Open Memory");
            memBtn.setOnAction(e -> {
                String title = instance.label().isBlank() ? component.displayName() : instance.label();
                new MemoryView(editor, instance.id(), title).show();
            });
            body.getChildren().add(memBtn);
        }

        showState(instance, component);
        showDebugState(instance);

        body.getChildren().add(spacer());
        body.getChildren().add(sectionHeader("PLACEMENT"));
        body.getChildren().add(readOnly("Rotation", instance.rotation().degrees() + "°"));
        body.getChildren().add(readOnly("Position",
                (int) instance.position().x() + ", " + (int) instance.position().y()));
    }

    /** One pin's name, direction/width and description — built only from its PortSpec. */
    private VBox pinRow(PortSpec port) {
        String width = port.width().isSingleBit() ? "1 bit" : port.width().bits() + " bits";
        Label heading = new Label(port.name() + "  —  " + directionName(port.direction()) + ", " + width);
        heading.getStyleClass().add("property-label");
        VBox row = new VBox(2, heading);
        if (!port.description().isBlank()) {
            Label description = subtitle(port.description());
            row.getChildren().add(description);
        }
        VBox.setMargin(row, new Insets(4, 0, 0, 0));
        return row;
    }

    private String directionName(dev.logicforge.circuit.component.PortDirection direction) {
        return switch (direction) {
            case INPUT -> "Input";
            case OUTPUT -> "Output";
            case INOUT -> "Input/Output";
        };
    }

    private void showClockTiming(ComponentInstance instance) {
        int frequencyHz = instance.parameters().get(LibraryParameters.FREQUENCY_HZ);
        int dutyPercent = instance.parameters().get(LibraryParameters.DUTY_CYCLE_PERCENT);
        long period = SimulationTime.PICOSECONDS_PER_SECOND / Math.max(1, frequencyHz);
        long high = Math.max(1, Math.min(period - 1, period * dutyPercent / 100));
        long low = period - high;

        body.getChildren().add(spacer());
        body.getChildren().add(sectionHeader("TIMING"));
        body.getChildren().add(readOnly("Period", SimulationTime.ofPicoseconds(period).toString()));
        body.getChildren().add(readOnly("High", SimulationTime.ofPicoseconds(high).toString()));
        body.getChildren().add(readOnly("Low", SimulationTime.ofPicoseconds(low).toString()));
    }

    /** The live value of every pin, while the circuit compiles and a simulation exists. */
    private void showState(ComponentInstance instance, ComponentDefinition component) {
        if (editor.simulation().isEmpty()) {
            return;
        }
        if (editor.isDefinitionMode()) {
            body.getChildren().add(spacer());
            body.getChildren().add(subtitle("Definition view — no runtime instance selected"));
            return;
        }
        List<PortSpec> ports = component.ports(instance.parameters());
        List<javafx.scene.Node> rows = new ArrayList<>();
        for (PortSpec port : ports) {
            Optional<LogicVector> value = editor.valueAt(new PortReference(instance.id(), port.name()));
            value.ifPresent(v -> rows.add(readOnly(port.name(), formatState(v))));
        }
        if (rows.isEmpty()) {
            return;
        }
        body.getChildren().add(spacer());
        body.getChildren().add(sectionHeader("STATE"));
        body.getChildren().addAll(rows);
    }

    /**
     * A component's internal state (a register's stored word, a register file's contents,
     * a memory's last access) rendered generically from {@link
     * dev.logicforge.simulation.ComponentDebugSnapshot} — never by casting the UI down to a
     * specific behavior's state class, so any future stateful component gets an inspector
     * view for free just by populating the snapshot.
     */
    private void showDebugState(ComponentInstance instance) {
        if (editor.isDefinitionMode()) {
            return;
        }
        Optional<dev.logicforge.simulation.ComponentDebugSnapshot> snapshot = editor.debugSnapshot(instance.id());
        if (snapshot.isEmpty()) {
            return;
        }
        dev.logicforge.simulation.ComponentDebugSnapshot debug = snapshot.get();
        List<javafx.scene.Node> rows = new ArrayList<>();
        debug.namedValues().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> rows.add(readOnly(entry.getKey(), formatState(entry.getValue()))));
        List<LogicVector> registers = debug.registers();
        for (int i = 0; i < registers.size(); i++) {
            rows.add(readOnly("R" + i, formatState(registers.get(i))));
        }
        debug.counters().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> rows.add(readOnly(entry.getKey(), String.valueOf(entry.getValue()))));
        debug.memoryOptional().ifPresent(memory -> {
            if (memory.lastReadAddress() >= 0) {
                rows.add(readOnly("Last read", "0x" + Integer.toHexString(memory.lastReadAddress())));
            }
            if (memory.lastWriteAddress() >= 0) {
                rows.add(readOnly("Last write", "0x" + Integer.toHexString(memory.lastWriteAddress())));
                if (memory.lastWrittenValue() != null) {
                    rows.add(readOnly("Last value", formatState(memory.lastWrittenValue())));
                }
            }
        });
        if (rows.isEmpty()) {
            return;
        }
        body.getChildren().add(spacer());
        body.getChildren().add(sectionHeader("DEBUG STATE"));
        body.getChildren().addAll(rows);
    }

    private String formatState(LogicVector value) {
        if (value.width() == 1) {
            return String.valueOf(value.getBit(0).symbol());
        }
        if (value.isHighImpedance()) {
            return "Z";
        }
        if (!value.isFullyDefined()) {
            return "X (" + value.toBinaryString() + ")";
        }
        return value.toUnsignedLong()
                .stream()
                .mapToObj(v -> "0x" + Long.toHexString(v).toUpperCase(Locale.ROOT)
                        + "  (" + value.toBinaryString() + ")")
                .findFirst()
                .orElseGet(value::toBinaryString);
    }

    /** Builds the right editor for a parameter, driven only by its declaration. */
    private javafx.scene.Node editorFor(ComponentInstance instance, ComponentDefinition definition,
                                        ParameterSpec<?> parameter) {
        return switch (parameter) {
            case ParameterSpec.IntegerParameter integer -> {
                Spinner<Integer> spinner = new Spinner<>();
                spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                        integer.min(), integer.max(), instance.parameters().get(integer)));
                spinner.setEditable(true);
                spinner.setMaxWidth(Double.MAX_VALUE);
                spinner.valueProperty().addListener((observable, old, value) ->
                        change(instance, definition, integer.key(), value));
                yield spinner;
            }
            case ParameterSpec.BooleanParameter bool -> {
                CheckBox check = new CheckBox("enabled");
                check.setSelected(instance.parameters().get(bool));
                check.selectedProperty().addListener((observable, old, value) ->
                        change(instance, definition, bool.key(), value));
                yield check;
            }
            case ParameterSpec.EnumParameter choice -> {
                ComboBox<String> combo = new ComboBox<>();
                combo.getItems().setAll(choice.options());
                combo.setValue(instance.parameters().get(choice));
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.valueProperty().addListener((observable, old, value) ->
                        change(instance, definition, choice.key(), value));
                yield combo;
            }
            case ParameterSpec.StringParameter text -> {
                TextField field = new TextField(instance.parameters().get(text));
                field.setOnAction(event -> change(instance, definition, text.key(), field.getText()));
                yield field;
            }
        };
    }

    private void change(ComponentInstance instance, ComponentDefinition definition, String key,
                        Object value) {
        editor.document().component(instance.id()).ifPresent(current -> {
            if (!current.parameters().asMap().getOrDefault(key, "").equals(value)) {
                editor.execute(new ChangeParameterCommand(editor.document(), definition, current, key, value));
            }
        });
    }

    private void commitLabel(ComponentInstance instance, String label) {
        editor.document().component(instance.id()).ifPresent(current -> {
            if (!current.label().equals(label)) {
                editor.execute(new SetLabelCommand(editor.document(), current, label));
            }
        });
    }

    // --------------------------------------------------------------- widgets

    private Label title(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("inspector-title");
        return label;
    }

    private Label subtitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("inspector-subtitle");
        label.setWrapText(true);
        return label;
    }

    private Label propertyLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("property-label");
        VBox.setMargin(label, new Insets(6, 0, 0, 0));
        return label;
    }

    private Label sectionHeader(String text) {
        Label label = new Label(text.toUpperCase(Locale.ROOT));
        label.getStyleClass().add("section-header");
        return label;
    }

    private javafx.scene.Node readOnly(String name, String value) {
        Label label = new Label(name + ": " + value);
        label.getStyleClass().add("inspector-subtitle");
        return label;
    }

    private javafx.scene.Node spacer() {
        javafx.scene.layout.Region region = new javafx.scene.layout.Region();
        region.setMinHeight(6);
        return region;
    }
}
