package dev.logicforge.format.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JsonParserTest {

    @Test
    void nestingUpToTheLimitIsAccepted() {
        String text = "[".repeat(JsonParser.MAX_DEPTH) + "]".repeat(JsonParser.MAX_DEPTH);

        assertInstanceOf(JsonValue.JsonArray.class, JsonParser.parse(text));
    }

    @Test
    void deeperNestingIsAParseErrorNotAStackOverflow() {
        int depth = JsonParser.MAX_DEPTH + 1;
        String text = "{\"a\":".repeat(depth) + "1" + "}".repeat(depth);

        JsonParseException failure = assertThrows(JsonParseException.class, () -> JsonParser.parse(text));

        assertEquals("Nesting deeper than " + JsonParser.MAX_DEPTH + " levels (at character "
                + (JsonParser.MAX_DEPTH * 5) + ")", failure.getMessage());
    }

    @Test
    void unicodeEscapesAreDecoded() {
        JsonValue value = JsonParser.parse("\"\\u00e4\\u2192\"");

        assertEquals(new JsonValue.JsonString("ä→"), value);
    }

    @Test
    void brokenEscapesAreParseErrors() {
        assertThrows(JsonParseException.class, () -> JsonParser.parse("\"\\u12\""));
        assertThrows(JsonParseException.class, () -> JsonParser.parse("\"\\u12"));
        assertThrows(JsonParseException.class, () -> JsonParser.parse("\"\\uXYZW\""));
        assertThrows(JsonParseException.class, () -> JsonParser.parse("\"\\"));
    }
}
