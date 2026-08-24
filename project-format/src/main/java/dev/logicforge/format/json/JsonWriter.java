package dev.logicforge.format.json;

import java.math.BigDecimal;

/** Writes a {@link JsonValue} as indented, deterministic JSON text. */
public final class JsonWriter {

    private static final String INDENT = "  ";

    private JsonWriter() {
    }

    public static String write(JsonValue value) {
        StringBuilder out = new StringBuilder();
        writeValue(value, out, 0);
        out.append('\n');
        return out.toString();
    }

    private static void writeValue(JsonValue value, StringBuilder out, int depth) {
        switch (value) {
            case JsonValue.JsonObject object -> writeObject(object, out, depth);
            case JsonValue.JsonArray array -> writeArray(array, out, depth);
            case JsonValue.JsonString text -> writeString(text.value(), out);
            case JsonValue.JsonNumber number -> out.append(formatNumber(number.value()));
            case JsonValue.JsonBoolean bool -> out.append(bool.value());
            case JsonValue.JsonNull ignored -> out.append("null");
        }
    }

    private static void writeObject(JsonValue.JsonObject object, StringBuilder out, int depth) {
        if (object.members().isEmpty()) {
            out.append("{}");
            return;
        }
        out.append("{\n");
        int remaining = object.members().size();
        for (var entry : object.members().entrySet()) {
            indent(out, depth + 1);
            writeString(entry.getKey(), out);
            out.append(": ");
            writeValue(entry.getValue(), out, depth + 1);
            out.append(--remaining > 0 ? ",\n" : "\n");
        }
        indent(out, depth);
        out.append('}');
    }

    private static void writeArray(JsonValue.JsonArray array, StringBuilder out, int depth) {
        if (array.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append("[\n");
        for (int i = 0; i < array.elements().size(); i++) {
            indent(out, depth + 1);
            writeValue(array.elements().get(i), out, depth + 1);
            out.append(i < array.elements().size() - 1 ? ",\n" : "\n");
        }
        indent(out, depth);
        out.append(']');
    }

    private static void indent(StringBuilder out, int depth) {
        out.append(INDENT.repeat(depth));
    }

    private static void writeString(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20) {
                        out.append(String.format("\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        out.append('"');
    }

    /** Whole numbers are written without a decimal point, so coordinates stay readable. */
    static String formatNumber(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
