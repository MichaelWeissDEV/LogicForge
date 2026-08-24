package dev.logicforge.format.json;

/** Thrown when a file is not valid JSON. */
public class JsonParseException extends RuntimeException {

    public JsonParseException(String message, int position) {
        super(message + " (at character " + position + ")");
    }
}
