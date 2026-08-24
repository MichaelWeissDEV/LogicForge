package dev.logicforge.circuit.document;

/** Descriptive information about a circuit that does not affect its behaviour. */
public record CircuitMetadata(String name, String description) {

    public static final CircuitMetadata DEFAULT = new CircuitMetadata("main", "");

    public CircuitMetadata {
        name = name == null || name.isBlank() ? "main" : name;
        description = description == null ? "" : description;
    }

    public CircuitMetadata withName(String newName) {
        return new CircuitMetadata(newName, description);
    }

    public CircuitMetadata withDescription(String newDescription) {
        return new CircuitMetadata(name, newDescription);
    }
}
