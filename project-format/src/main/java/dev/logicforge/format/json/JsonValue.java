package dev.logicforge.format.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A minimal JSON tree.
 *
 * <p>LogicForge writes its own JSON instead of pulling in a document library: the project
 * format is small, and doing it here keeps the output byte-for-byte reproducible (members
 * in a fixed order, integral numbers without a decimal point) which the round-trip tests
 * rely on.
 */
public sealed interface JsonValue {

    record JsonObject(Map<String, JsonValue> members) implements JsonValue {

        public JsonObject() {
            this(new LinkedHashMap<>());
        }

        public JsonObject put(String name, JsonValue value) {
            members.put(name, value);
            return this;
        }

        public JsonObject put(String name, String value) {
            return put(name, new JsonString(value));
        }

        public JsonObject put(String name, double value) {
            return put(name, new JsonNumber(value));
        }

        public JsonObject put(String name, boolean value) {
            return put(name, new JsonBoolean(value));
        }

        public Optional<JsonValue> get(String name) {
            return Optional.ofNullable(members.get(name));
        }

        public String string(String name, String fallback) {
            return get(name).map(JsonValue::asString).orElse(fallback);
        }

        public double number(String name, double fallback) {
            return get(name).map(JsonValue::asDouble).orElse(fallback);
        }

        public int integer(String name, int fallback) {
            return (int) number(name, fallback);
        }

        public JsonObject object(String name) {
            return get(name).filter(JsonObject.class::isInstance).map(JsonObject.class::cast)
                    .orElseGet(JsonObject::new);
        }

        public List<JsonValue> array(String name) {
            return get(name).filter(JsonArray.class::isInstance).map(JsonArray.class::cast)
                    .map(JsonArray::elements).orElseGet(List::of);
        }

        public boolean has(String name) {
            return members.containsKey(name);
        }
    }

    record JsonArray(List<JsonValue> elements) implements JsonValue {

        public JsonArray() {
            this(new ArrayList<>());
        }

        public JsonArray add(JsonValue value) {
            elements.add(value);
            return this;
        }

        public boolean isEmpty() {
            return elements.isEmpty();
        }
    }

    record JsonString(String value) implements JsonValue {
    }

    record JsonNumber(double value) implements JsonValue {
    }

    record JsonBoolean(boolean value) implements JsonValue {
    }

    record JsonNull() implements JsonValue {

        public static final JsonNull INSTANCE = new JsonNull();
    }

    /** The value as a string, whatever its JSON type was. */
    default String asString() {
        return switch (this) {
            case JsonString text -> text.value();
            case JsonNumber number -> JsonWriter.formatNumber(number.value());
            case JsonBoolean bool -> String.valueOf(bool.value());
            default -> "";
        };
    }

    default double asDouble() {
        return switch (this) {
            case JsonNumber number -> number.value();
            case JsonString text -> parse(text.value());
            case JsonBoolean bool -> bool.value() ? 1 : 0;
            default -> 0;
        };
    }

    private static double parse(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /** The plain Java value, used for component parameters. */
    default Object asJavaValue() {
        return switch (this) {
            case JsonString text -> text.value();
            case JsonNumber number -> number.value();
            case JsonBoolean bool -> bool.value();
            default -> null;
        };
    }
}
