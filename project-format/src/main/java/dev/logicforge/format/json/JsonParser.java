package dev.logicforge.format.json;

/** A small recursive-descent JSON parser, sufficient for LogicForge project files. */
public final class JsonParser {

    private final String text;
    private int position;

    private JsonParser(String text) {
        this.text = text;
    }

    public static JsonValue parse(String text) {
        JsonParser parser = new JsonParser(text);
        parser.skipWhitespace();
        JsonValue value = parser.readValue();
        parser.skipWhitespace();
        if (parser.position < text.length()) {
            throw new JsonParseException("Unexpected trailing content", parser.position);
        }
        return value;
    }

    private JsonValue readValue() {
        if (position >= text.length()) {
            throw new JsonParseException("Unexpected end of input", position);
        }
        char character = text.charAt(position);
        return switch (character) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> new JsonValue.JsonString(readString());
            case 't', 'f' -> readBoolean();
            case 'n' -> readNull();
            default -> readNumber();
        };
    }

    private JsonValue readObject() {
        expect('{');
        JsonValue.JsonObject object = new JsonValue.JsonObject();
        skipWhitespace();
        if (peek() == '}') {
            position++;
            return object;
        }
        while (true) {
            skipWhitespace();
            String name = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            object.put(name, readValue());
            skipWhitespace();
            char next = peek();
            position++;
            if (next == '}') {
                return object;
            }
            if (next != ',') {
                throw new JsonParseException("Expected ',' or '}' in object", position);
            }
        }
    }

    private JsonValue readArray() {
        expect('[');
        JsonValue.JsonArray array = new JsonValue.JsonArray();
        skipWhitespace();
        if (peek() == ']') {
            position++;
            return array;
        }
        while (true) {
            skipWhitespace();
            array.add(readValue());
            skipWhitespace();
            char next = peek();
            position++;
            if (next == ']') {
                return array;
            }
            if (next != ',') {
                throw new JsonParseException("Expected ',' or ']' in array", position);
            }
        }
    }

    private String readString() {
        expect('"');
        StringBuilder value = new StringBuilder();
        while (true) {
            if (position >= text.length()) {
                throw new JsonParseException("Unterminated string", position);
            }
            char character = text.charAt(position++);
            if (character == '"') {
                return value.toString();
            }
            if (character != '\\') {
                value.append(character);
                continue;
            }
            char escape = text.charAt(position++);
            switch (escape) {
                case '"' -> value.append('"');
                case '\\' -> value.append('\\');
                case '/' -> value.append('/');
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'u' -> {
                    value.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                    position += 4;
                }
                default -> throw new JsonParseException("Unknown escape '\\" + escape + "'", position);
            }
        }
    }

    private JsonValue readNumber() {
        int start = position;
        while (position < text.length() && "-+.eE0123456789".indexOf(text.charAt(position)) >= 0) {
            position++;
        }
        if (start == position) {
            throw new JsonParseException("Expected a value", position);
        }
        try {
            return new JsonValue.JsonNumber(Double.parseDouble(text.substring(start, position)));
        } catch (NumberFormatException failure) {
            throw new JsonParseException("Invalid number '" + text.substring(start, position) + "'", start);
        }
    }

    private JsonValue readBoolean() {
        if (text.startsWith("true", position)) {
            position += 4;
            return new JsonValue.JsonBoolean(true);
        }
        if (text.startsWith("false", position)) {
            position += 5;
            return new JsonValue.JsonBoolean(false);
        }
        throw new JsonParseException("Expected a value", position);
    }

    private JsonValue readNull() {
        if (!text.startsWith("null", position)) {
            throw new JsonParseException("Expected a value", position);
        }
        position += 4;
        return JsonValue.JsonNull.INSTANCE;
    }

    private char peek() {
        if (position >= text.length()) {
            throw new JsonParseException("Unexpected end of input", position);
        }
        return text.charAt(position);
    }

    private void expect(char expected) {
        if (peek() != expected) {
            throw new JsonParseException("Expected '" + expected + "'", position);
        }
        position++;
    }

    private void skipWhitespace() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
    }
}
