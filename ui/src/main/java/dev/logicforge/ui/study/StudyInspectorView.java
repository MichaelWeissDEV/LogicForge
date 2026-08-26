package dev.logicforge.ui.study;

import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.processor.lf8.Lf8ControlField;
import dev.logicforge.processor.lf8.Lf8ControlSignal;
import dev.logicforge.processor.lf8.Lf8Isa;
import dev.logicforge.simulation.ComponentDebugSnapshot;
import dev.logicforge.simulation.Simulation;
import dev.logicforge.ui.edit.CircuitEditor;
import java.util.Locale;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Read-only generic component state plus LF-8 architectural/microcode convenience data. */
public final class StudyInspectorView extends VBox {

    private final CircuitEditor editor;
    private final VBox body = new VBox(5);

    public StudyInspectorView(CircuitEditor editor) {
        this.editor = editor;
        setPadding(new Insets(8));
        setMinWidth(270);
        getChildren().add(new Label("STUDY INSPECTOR"));
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);
        editor.selection().addListener(this::refresh);
        editor.addChangeListener(this::refresh);
        refresh();
    }

    public void refresh() {
        body.getChildren().clear();
        Label mode = new Label(editor.isDefinitionMode()
                ? "REFERENCE DEFINITION — no live instance implied"
                : "LIVE INSTANCE — " + editor.activeInstancePath().orElse("main"));
        mode.setWrapText(true);
        body.getChildren().add(mode);
        showSelectedComponent();
        showLf8State();
    }

    private void showSelectedComponent() {
        if (editor.selection().components().size() != 1) {
            body.getChildren().add(section("Selected component"));
            body.getChildren().add(new Label("Select one component to inspect its pins and state."));
            return;
        }
        var id = editor.selection().components().iterator().next();
        editor.document().component(id).ifPresent(instance -> editor.definitionOf(instance).ifPresent(definition -> {
            body.getChildren().add(section("Selected component"));
            body.getChildren().add(row("Name", instance.label().isBlank()
                    ? definition.displayName() : instance.label()));
            body.getChildren().add(row("Definition", definition.id()));
            Label description = new Label(definition.description());
            description.setWrapText(true);
            body.getChildren().add(description);
            definition.ports(instance.parameters()).forEach(port ->
                    editor.valueAt(new PortReference(instance.id(), port.name())).ifPresent(value ->
                            body.getChildren().add(row(port.name(), format(value)))));
            editor.debugSnapshot(instance.id()).ifPresent(this::showDebug);
        }));
    }

    private void showDebug(ComponentDebugSnapshot debug) {
        if (debug.isEmpty()) {
            return;
        }
        body.getChildren().add(section("Runtime state"));
        debug.namedValues().forEach((name, value) -> body.getChildren().add(row(name, format(value))));
        for (int register = 0; register < debug.registers().size(); register++) {
            body.getChildren().add(row("R" + register, format(debug.registers().get(register))));
        }
    }

    private void showLf8State() {
        var compilation = editor.compilation().orElse(null);
        Simulation simulation = editor.simulation().orElse(null);
        if (compilation == null || simulation == null || compilation.componentByLabel("PC").isEmpty()) {
            return;
        }
        body.getChildren().add(section("LF-8 architectural state"));
        addOutput(compilation, simulation, "PC", "PC", 0);
        addOutput(compilation, simulation, "SP", "SP", 0);
        addOutput(compilation, simulation, "IR", "IR", 0);
        addOutput(compilation, simulation, "STATUS flags", "FLAGS_REGISTER", 0);
        addOutput(compilation, simulation, "IE", "IE_REGISTER", 0);
        addOutput(compilation, simulation, "Microstep", "MICROSTEP", 0);
        addOutput(compilation, simulation, "IRQ", "IRQ", 0);
        addOutput(compilation, simulation, "NMI", "NMI", 0);
        addOutput(compilation, simulation, "HALT", "HALT_LATCH", 0);

        var registerFile = compilation.componentByLabel("REGISTER_FILE");
        if (registerFile.isPresent()) {
            var registers = simulation.debugSnapshot(registerFile.getAsInt()).registers();
            for (int i = 0; i < registers.size(); i++) {
                body.getChildren().add(row("R" + i, format(registers.get(i))));
            }
        }

        var ir = read(compilation, simulation, "IR", 0);
        ir.ifPresent(value -> value.toUnsignedLong().ifPresent(opcode -> {
                    String decoded = Lf8Isa.byOpcode((int) opcode)
                            .map(instruction -> instruction.mnemonic() + " (0x"
                                    + Integer.toHexString(instruction.opcode()) + ")")
                            .orElse("Unknown opcode 0x" + Long.toHexString(opcode));
                    body.getChildren().add(row("Instruction", decoded));
                }));

        read(compilation, simulation, "MICROCODE_ROM", 0).ifPresent(value ->
                value.toUnsignedLong().ifPresent(word -> {
                    body.getChildren().add(section("Microcode"));
                    body.getChildren().add(row("Raw control word", "0x"
                            + Long.toUnsignedString(word, 16).toUpperCase(Locale.ROOT)));
                    long alu = (word >>> Lf8ControlField.ALU_OP.lsb())
                            & ((1L << Lf8ControlField.ALU_OP.width()) - 1);
                    body.getChildren().add(row("ALU_OP", String.valueOf(alu)));
                    String active = java.util.Arrays.stream(Lf8ControlSignal.values())
                            .filter(signal -> (word & signal.mask()) != 0)
                            .map(Enum::name)
                            .collect(java.util.stream.Collectors.joining("\n"));
                    Label controls = new Label(active.isBlank() ? "(no active controls)" : active);
                    controls.setWrapText(true);
                    body.getChildren().add(controls);
                }));
    }

    private void addOutput(dev.logicforge.compiler.CompilationResult compilation,
                           Simulation simulation, String display, String label, int output) {
        read(compilation, simulation, label, output)
                .ifPresent(value -> body.getChildren().add(row(display, format(value))));
    }

    private java.util.Optional<LogicVector> read(
            dev.logicforge.compiler.CompilationResult compilation, Simulation simulation,
            String label, int output) {
        var component = compilation.componentByLabel(label);
        return component.isPresent()
                ? java.util.Optional.of(simulation.readOutput(component.getAsInt(), output))
                : java.util.Optional.empty();
    }

    private static Label section(String text) {
        Label label = new Label(text.toUpperCase(Locale.ROOT));
        VBox.setMargin(label, new Insets(10, 0, 2, 0));
        return label;
    }

    private static Label row(String name, String value) {
        Label label = new Label(name + ": " + value);
        label.setWrapText(true);
        return label;
    }

    private static String format(LogicVector value) {
        if (value.width() == 1) {
            return String.valueOf(value.singleBit().symbol());
        }
        if (!value.isFullyDefined()) {
            return value.toBinaryString();
        }
        return value.toUnsignedLong().stream()
                .mapToObj(number -> "0x" + Long.toHexString(number).toUpperCase(Locale.ROOT)
                        + " (" + value.toBinaryString() + ")")
                .findFirst().orElse(value.toBinaryString());
    }
}
