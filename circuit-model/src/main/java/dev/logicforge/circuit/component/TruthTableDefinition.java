package dev.logicforge.circuit.component;

import java.util.List;
import java.util.Objects;

/** Structured educational truth or characteristic table independent of UI rendering. */
public record TruthTableDefinition(List<String> inputs, List<String> outputs, List<Row> rows) {
    public TruthTableDefinition {
        inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        if (inputs.isEmpty() || outputs.isEmpty() || rows.isEmpty()) {
            throw new IllegalArgumentException("Truth table requires columns and rows");
        }
        for (Row row : rows) {
            if (row.inputs().size() != inputs.size() || row.outputs().size() != outputs.size()) {
                throw new IllegalArgumentException("Truth-table row width does not match columns");
            }
        }
    }

    public record Row(List<String> inputs, List<String> outputs, String note) {
        public Row {
            inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
            outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
            note = note == null ? "" : note.strip();
        }

        public Row(List<String> inputs, List<String> outputs) {
            this(inputs, outputs, "");
        }
    }
}
