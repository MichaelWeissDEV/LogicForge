package dev.logicforge.ui.study;

import dev.logicforge.processor.lf8.Lf8ComponentRoles;
import dev.logicforge.processor.lf8.runtime.Lf8RuntimeProbe;

import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.processor.lf8.Lf8ControlField;
import dev.logicforge.processor.lf8.Lf8ControlSignal;
import dev.logicforge.processor.lf8.Lf8Isa;
import dev.logicforge.processor.lf8.runtime.Breakpoint;
import dev.logicforge.processor.lf8.runtime.MemoryReadBreakpoint;
import dev.logicforge.processor.lf8.runtime.MemoryWriteBreakpoint;
import dev.logicforge.processor.lf8.runtime.PcBreakpoint;
import dev.logicforge.simulation.ComponentDebugSnapshot;
import dev.logicforge.simulation.Simulation;
import dev.logicforge.ui.edit.CircuitEditor;
import java.util.Locale;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
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
        showBreakpoints();
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
        String datapath = Lf8ComponentRoles.CPU_DATAPATH + "/";
        String control = Lf8ComponentRoles.CPU_CONTROL + "/";
        addOutput(probe, "PC", datapath + Lf8ComponentRoles.DATAPATH_PC, "COUNT");
        addOutput(probe, "SP", datapath + Lf8ComponentRoles.DATAPATH_SP, "COUNT");
        addOutput(probe, "IR", datapath + Lf8ComponentRoles.DATAPATH_IR, "Q");
        addOutput(probe, "STATUS flags", datapath + Lf8ComponentRoles.DATAPATH_FLAGS, "Q");
        addOutput(probe, "IE", datapath + Lf8ComponentRoles.DATAPATH_IE, "Q");
        addOutput(probe, "Microstep", control + Lf8ComponentRoles.CONTROL_MICROSTEP, "COUNT");
        addOutput(probe, "IRQ", control + Lf8ComponentRoles.CONTROL_IRQ_INPUT, "IN");
        addOutput(probe, "NMI taken", control + Lf8ComponentRoles.CONTROL_NMI_TAKEN, "Q");
        addOutput(probe, "HALT", control + Lf8ComponentRoles.CONTROL_HALT, "Q");

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

        String microcode = Lf8ComponentRoles.path(Lf8ComponentRoles.CPU_CONTROL,
                Lf8ComponentRoles.CONTROL_MICROCODE);
        probe.value(microcode, "DATA").ifPresent(value ->
                value.toUnsignedLong().ifPresent(word -> {
                    body.getChildren().add(section("Microcode"));
                    probe.value(microcode, "ADDRESS").ifPresent(address ->
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

    private void showBreakpoints() {
        if (!controller.canAddBreakpoint()) {
            return;
        }
        body.getChildren().add(section("Breakpoints"));

        var path = controller.lf8Probe().orElseThrow().cpuPath();
        var breakpoints = controller.breakpoints().stream()
                .filter(breakpoint -> breakpoint.cpuPath().equals(path))
                .toList();
        if (breakpoints.isEmpty()) {
            body.getChildren().add(new Label("No breakpoints on this CPU yet."));
        }
        for (Breakpoint breakpoint : breakpoints) {
            body.getChildren().add(breakpointRow(breakpoint));
        }

        Button addPc = smallButton("+ PC", this::promptAddPcBreakpoint);
        Button addRead = smallButton("+ Read", () -> promptAddMemoryBreakpoint(true));
        Button addWrite = smallButton("+ Write", () -> promptAddMemoryBreakpoint(false));
        HBox addRow = new HBox(4, addPc, addRead, addWrite);
        body.getChildren().add(addRow);
    }

    private HBox breakpointRow(Breakpoint breakpoint) {
        CheckBox enabled = new CheckBox();
        enabled.setSelected(breakpoint.enabled());
        enabled.setOnAction(event -> controller.setBreakpointEnabled(breakpoint, enabled.isSelected()));

        Label description = new Label(describe(breakpoint));
        description.setWrapText(true);
        HBox.setHgrow(description, Priority.ALWAYS);

        Button remove = smallButton("×", () -> controller.removeBreakpoint(breakpoint));
        remove.setTooltip(new Tooltip("Remove breakpoint"));

        HBox row = new HBox(4, enabled, description, remove);
        return row;
    }

    private static String describe(Breakpoint breakpoint) {
        return switch (breakpoint) {
            case PcBreakpoint pc -> "PC = " + hex(pc.address());
            case MemoryReadBreakpoint read -> "Read " + range(read.firstAddress(), read.lastAddress());
            case MemoryWriteBreakpoint write -> "Write " + range(write.firstAddress(), write.lastAddress());
        };
    }

    private static String range(int first, int last) {
        return first == last ? hex(first) : hex(first) + "-" + hex(last);
    }

    private static String hex(int address) {
        return "0x" + Integer.toHexString(address).toUpperCase(Locale.ROOT);
    }

    private void promptAddPcBreakpoint() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add PC Breakpoint");
        dialog.setHeaderText("Stop when the program counter reaches this address");
        dialog.setContentText("Address (hex, e.g. 0x0040):");
        dialog.showAndWait().ifPresent(text -> {
            Integer address = parseHex(text);
            if (address == null) {
                showAddressError();
                return;
            }
            controller.addPcBreakpoint(address);
            refresh();
        });
    }

    private void promptAddMemoryBreakpoint(boolean read) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(read ? "Add Memory Read Breakpoint" : "Add Memory Write Breakpoint");
        dialog.setHeaderText("Stop when memory in this range is "
                + (read ? "read" : "written"));
        dialog.setContentText("Address or range (hex, e.g. 0x8000 or 0x8000-0x80FF):");
        dialog.showAndWait().ifPresent(text -> {
            int[] range = parseHexRange(text);
            if (range == null) {
                showAddressError();
                return;
            }
            if (read) {
                controller.addMemoryReadBreakpoint(range[0], range[1]);
            } else {
                controller.addMemoryWriteBreakpoint(range[0], range[1]);
            }
            refresh();
        });
    }

    private void showAddressError() {
        new Alert(Alert.AlertType.ERROR,
                "Enter a hex address (0x0000-0xFFFF), or a range like 0x8000-0x80FF.",
                ButtonType.OK).showAndWait();
    }

    private static Integer parseHex(String text) {
        String trimmed = text.strip();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("0x")) {
            trimmed = trimmed.substring(2);
        }
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            long value = Long.parseLong(trimmed, 16);
            return value >= 0 && value <= 0xffff ? (int) value : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static int[] parseHexRange(String text) {
        String[] parts = text.strip().split("-", 2);
        Integer first = parseHex(parts[0]);
        if (first == null) {
            return null;
        }
        Integer last = parts.length == 2 ? parseHex(parts[1]) : first;
        if (last == null || last < first) {
            return null;
        }
        return new int[]{first, last};
    }

    private static Button smallButton(String text, Runnable action) {
        Button button = new Button(text);
        button.setOnAction(event -> action.run());
        return button;
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
