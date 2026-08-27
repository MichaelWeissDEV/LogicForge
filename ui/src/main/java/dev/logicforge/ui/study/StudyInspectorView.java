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
    private final StudyController controller;
    private final VBox body = new VBox(5);

    public StudyInspectorView(StudyController controller) {
        this.controller = controller;
        this.editor = controller.editor();
        setPadding(new Insets(8));
        setMinWidth(270);
        getChildren().add(new Label("STUDY INSPECTOR"));
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);
        editor.selection().addListener(this::refresh);
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
            showDocumentation(definition.documentation());
            definition.ports(instance.parameters()).forEach(port ->
                    editor.valueAt(new PortReference(instance.id(), port.name())).ifPresent(value ->
                            body.getChildren().add(row(port.name(), format(value)))));
            editor.debugSnapshot(instance.id()).ifPresent(this::showDebug);
        }));
    }

    private void showDocumentation(
            dev.logicforge.circuit.component.ComponentDocumentation documentation) {
        if (documentation.isEmpty()) {
            return;
        }
        body.getChildren().add(section("Operation"));
        if (!documentation.operation().isBlank()) {
            body.getChildren().add(wrapped(documentation.operation()));
        }
        documentation.truthTable().ifPresent(table -> {
            body.getChildren().add(section("Truth table"));
            body.getChildren().add(row(String.join("  ", table.inputs()),
                    String.join("  ", table.outputs())));
            table.rows().forEach(values -> body.getChildren().add(row(
                    String.join("  ", values.inputs()), String.join("  ", values.outputs()))));
        });
        if (!documentation.timingNotes().isBlank()) {
            body.getChildren().add(row("Timing", documentation.timingNotes()));
        }
        if (!documentation.invalidStates().isBlank()) {
            body.getChildren().add(row("Invalid states", documentation.invalidStates()));
        }
    }

    private static Label wrapped(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
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
        debug.textValues().forEach((name, value) -> body.getChildren().add(row(name, value)));
    }

    private void showLf8State() {
        var probe = controller.lf8Probe().orElse(null);
        if (probe == null) {
            return;
        }
        body.getChildren().add(section("LF-8 architectural state"));
        addOutput(probe, "PC", "DATAPATH/PC", "COUNT");
        addOutput(probe, "SP", "DATAPATH/SP", "COUNT");
        addOutput(probe, "IR", "DATAPATH/IR", "Q");
        addOutput(probe, "STATUS flags", "DATAPATH/FLAGS_REGISTER", "Q");
        addOutput(probe, "IE", "DATAPATH/IE_REGISTER", "Q");
        addOutput(probe, "Microstep", "CONTROL/MICROSTEP", "COUNT");
        addOutput(probe, "IRQ", "CONTROL/IRQ_PROBE", "IN");
        addOutput(probe, "NMI taken", "CONTROL/NMI_TAKEN_LATCH", "Q");
        addOutput(probe, "HALT", "CONTROL/HALT_LATCH", "Q");

        probe.registerFile().ifPresent(snapshot -> {
            var registers = snapshot.registers();
            for (int i = 0; i < registers.size(); i++) {
                body.getChildren().add(row("R" + i, format(registers.get(i))));
            }
        });

        var ir = probe.ir();
        ir.ifPresent(value -> value.toUnsignedLong().ifPresent(opcode -> {
                    String decoded = Lf8Isa.byOpcode((int) opcode)
                            .map(instruction -> instruction.mnemonic() + " (0x"
                                    + Integer.toHexString(instruction.opcode()) + ")")
                            .orElse("Unknown opcode 0x" + Long.toHexString(opcode));
                    body.getChildren().add(row("Instruction", decoded));
                }));

        probe.value("CONTROL/MICROCODE_ROM", "DATA").ifPresent(value ->
                value.toUnsignedLong().ifPresent(word -> {
                    body.getChildren().add(section("Microcode"));
                    probe.value("CONTROL/MICROCODE_ROM", "ADDRESS").ifPresent(address ->
                            body.getChildren().add(row("ROM address", format(address))));
                    body.getChildren().add(row("Raw control word", "0x"
                            + Long.toUnsignedString(word, 16).toUpperCase(Locale.ROOT)));
                    long alu = (word >>> Lf8ControlField.ALU_OP.lsb())
                            & ((1L << Lf8ControlField.ALU_OP.width()) - 1);
                    body.getChildren().add(row("ALU_OP", String.valueOf(alu)));
                    showControlGroup(word, "Register File", "DESTINATION", "SOURCE_REGISTER",
                            "REGISTER_FILE", "MOV_SOURCE", "ALTERNATE_SOURCE", "SOURCE_TO_DATA");
                    showControlGroup(word, "PC", "PC_");
                    showControlGroup(word, "Memory", "MEMORY_", "MAR_", "ADDRESS_FROM_MAR");
                    showControlGroup(word, "Stack", "SP_", "ADDRESS_FROM_SP", "STATUS_TO_DATA");
                    showControlGroup(word, "Flags", "FLAGS_", "IE_", "STATUS_FROM_DATA");
                    showControlGroup(word, "Interrupt", "IRQ_", "VECTOR_", "RESET_",
                            "ADDRESS_FROM_VECTOR");
                    showControlGroup(word, "ALU", "ALU_");
                    showControlGroup(word, "Instruction / flow", "IR_", "HALT");
                }));
    }

    private void showControlGroup(long word, String group, String... prefixes) {
        String active = java.util.Arrays.stream(Lf8ControlSignal.values())
                .filter(signal -> (word & signal.mask()) != 0)
                .filter(signal -> java.util.Arrays.stream(prefixes)
                        .anyMatch(prefix -> signal.name().startsWith(prefix)))
                .map(Enum::name)
                .collect(java.util.stream.Collectors.joining(", "));
        body.getChildren().add(row(group, active.isBlank() ? "—" : active));
    }

    private void addOutput(Lf8RuntimeProbe probe, String display, String path, String port) {
        probe.value(path, port)
                .ifPresent(value -> body.getChildren().add(row(display, format(value))));
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
