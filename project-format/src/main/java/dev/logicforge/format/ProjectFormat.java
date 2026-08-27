package dev.logicforge.format;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitMetadata;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.PortDisplayMode;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.Rotation;
import dev.logicforge.format.json.JsonParseException;
import dev.logicforge.format.json.JsonParser;
import dev.logicforge.format.json.JsonValue;
import dev.logicforge.format.json.JsonWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads and writes {@code .logic} project files.
 *
 * <p>The format is plain JSON and describes the circuit in the same terms the user sees:
 * components with a type id, a position, a rotation, parameters and a label, plus wires
 * between named ports. No Java class names, no serialised objects, nothing from the
 * runtime — a project file survives any refactoring of the code that reads it.
 *
 * <p>Every file carries a {@code formatVersion}. Version 1 contains whole-port wires;
 * version 2 adds bit endpoints and port presentation; version 3 adds range endpoints;
 * version 4 adds stable, non-display semantic component roles.
 * The reader accepts every version.
 */
public final class ProjectFormat {

    /** The version this build writes. */
    public static final int FORMAT_VERSION = 4;

    /** File extension used by the file choosers. */
    public static final String EXTENSION = "logic";

    private ProjectFormat() {
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    public static void save(CircuitProject project, Path file) {
        try {
            Files.writeString(file, toJson(project), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new ProjectFormatException("Could not write " + file, failure);
        }
    }

    public static String toJson(CircuitProject project) {
        JsonValue.JsonObject root = new JsonValue.JsonObject();
        root.put("formatVersion", FORMAT_VERSION);
        root.put("application", "LogicForge");
        root.put("name", project.name());

        JsonValue.JsonArray circuits = new JsonValue.JsonArray();
        for (CircuitDocument circuit : project.circuits()) {
            circuits.add(writeCircuit(circuit));
        }
        root.put("circuits", circuits);
        return JsonWriter.write(root);
    }

    private static JsonValue.JsonObject writeCircuit(CircuitDocument circuit) {
        JsonValue.JsonObject object = new JsonValue.JsonObject();
        object.put("name", circuit.metadata().name());
        if (!circuit.metadata().description().isBlank()) {
            object.put("description", circuit.metadata().description());
        }

        JsonValue.JsonArray components = new JsonValue.JsonArray();
        for (ComponentInstance instance : circuit.components()) {
            components.add(writeComponent(instance));
        }
        object.put("components", components);

        JsonValue.JsonArray connections = new JsonValue.JsonArray();
        for (Connection connection : circuit.connections()) {
            connections.add(writeConnection(connection));
        }
        object.put("connections", connections);
        return object;
    }

    private static JsonValue.JsonObject writeComponent(ComponentInstance instance) {
        JsonValue.JsonObject object = new JsonValue.JsonObject();
        object.put("id", instance.id().toString());
        object.put("type", instance.definitionId());
        object.put("x", instance.position().x());
        object.put("y", instance.position().y());
        if (instance.rotation() != Rotation.DEG_0) {
            object.put("rotation", instance.rotation().degrees());
        }
        if (!instance.label().isBlank()) {
            object.put("label", instance.label());
        }
        if (!instance.semanticRole().isBlank()) {
            object.put("role", instance.semanticRole());
        }
        if (instance.portDisplayMode() == PortDisplayMode.EXPANDED) {
            object.put("portDisplay", "expanded");
        }
        if (!instance.parameters().isEmpty()) {
            JsonValue.JsonObject parameters = new JsonValue.JsonObject();
            for (Map.Entry<String, Object> entry : instance.parameters().asMap().entrySet()) {
                parameters.put(entry.getKey(), toJsonValue(entry.getValue()));
            }
            object.put("parameters", parameters);
        }
        return object;
    }

    private static JsonValue.JsonObject writeConnection(Connection connection) {
        JsonValue.JsonObject object = new JsonValue.JsonObject();
        object.put("id", connection.id().toString());
        object.put("from", writeEndpoint(connection.from()));
        object.put("to", writeEndpoint(connection.to()));
        if (!connection.waypoints().isEmpty()) {
            JsonValue.JsonArray waypoints = new JsonValue.JsonArray();
            for (CircuitPoint point : connection.waypoints()) {
                waypoints.add(new JsonValue.JsonObject().put("x", point.x()).put("y", point.y()));
            }
            object.put("waypoints", waypoints);
        }
        return object;
    }

    private static JsonValue.JsonObject writeEndpoint(dev.logicforge.circuit.document.PortEndpoint endpoint) {
        JsonValue.JsonObject obj = new JsonValue.JsonObject()
                .put("component", endpoint.componentId().toString())
                .put("port", endpoint.portName());
        if (endpoint.isBit()) {
            obj.put("bit", ((dev.logicforge.circuit.document.PortSlice.Bit) endpoint.slice()).index());
        } else if (endpoint.slice() instanceof dev.logicforge.circuit.document.PortSlice.Range range) {
            obj.put("range", new JsonValue.JsonObject()
                    .put("msb", range.msb()).put("lsb", range.lsb()));
        }
        return obj;
    }

    private static JsonValue toJsonValue(Object value) {
        return switch (value) {
            case null -> JsonValue.JsonNull.INSTANCE;
            case Boolean bool -> new JsonValue.JsonBoolean(bool);
            case Number number -> new JsonValue.JsonNumber(number.doubleValue());
            default -> new JsonValue.JsonString(value.toString());
        };
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    public static CircuitProject load(Path file) {
        try {
            return fromJson(Files.readString(file, StandardCharsets.UTF_8), fileName(file));
        } catch (IOException | UncheckedIOException failure) {
            throw new ProjectFormatException("Could not read " + file, failure);
        }
    }

    public static CircuitProject fromJson(String text, String fallbackName) {
        JsonValue parsed;
        try {
            parsed = JsonParser.parse(text);
        } catch (JsonParseException failure) {
            throw new ProjectFormatException("This is not a valid LogicForge project: "
                    + failure.getMessage(), failure);
        }
        if (!(parsed instanceof JsonValue.JsonObject root)) {
            throw new ProjectFormatException("A project file must contain a JSON object");
        }
        int version = root.integer("formatVersion", 0);
        if (version == 0) {
            throw new ProjectFormatException("Missing formatVersion: this is not a LogicForge project");
        }
        if (version > FORMAT_VERSION) {
            throw new ProjectFormatException("This project was saved with a newer version of LogicForge"
                    + " (format " + version + ", this build reads up to " + FORMAT_VERSION + ")");
        }

        CircuitProject project = new CircuitProject(root.string("name", fallbackName));
        List<JsonValue> circuits = root.array("circuits");
        if (circuits.isEmpty()) {
            throw new ProjectFormatException("The project contains no circuits");
        }
        for (JsonValue circuit : circuits) {
            if (circuit instanceof JsonValue.JsonObject object) {
                project.putCircuit(readCircuit(object, version));
            }
        }
        if (project.circuit(CircuitProject.MAIN_CIRCUIT).isEmpty()) {
            throw new ProjectFormatException("The project has no '" + CircuitProject.MAIN_CIRCUIT
                    + "' circuit");
        }
        return project;
    }

    private static CircuitDocument readCircuit(JsonValue.JsonObject object, int version) {
        CircuitDocument circuit = new CircuitDocument(new CircuitMetadata(
                object.string("name", CircuitProject.MAIN_CIRCUIT), object.string("description", "")));

        for (JsonValue element : object.array("components")) {
            if (element instanceof JsonValue.JsonObject component) {
                circuit.addComponent(readComponent(component));
            }
        }
        for (JsonValue element : object.array("connections")) {
            if (element instanceof JsonValue.JsonObject connection) {
                circuit.addConnection(readConnection(connection, circuit, version));
            }
        }
        return circuit;
    }

    private static ComponentInstance readComponent(JsonValue.JsonObject object) {
        String type = object.string("type", "");
        if (type.isBlank()) {
            throw new ProjectFormatException("A component without a type id cannot be loaded");
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        JsonValue.JsonObject stored = object.object("parameters");
        for (Map.Entry<String, JsonValue> entry : stored.members().entrySet()) {
            parameters.put(entry.getKey(), entry.getValue().asJavaValue());
        }
        return new ComponentInstance(
                readId(object, "component"),
                type,
                new CircuitPoint(object.number("x", 0), object.number("y", 0)),
                Rotation.ofDegrees(object.integer("rotation", 0)),
                ParameterValues.of(parameters),
                object.string("label", ""),
                "expanded".equalsIgnoreCase(object.string("portDisplay", "compact"))
                        ? PortDisplayMode.EXPANDED : PortDisplayMode.COMPACT,
                object.string("role", ""));
    }

    private static Connection readConnection(JsonValue.JsonObject object, CircuitDocument circuit,
                                             int version) {
        dev.logicforge.circuit.document.PortEndpoint from = readEndpoint(object.object("from"), version);
        dev.logicforge.circuit.document.PortEndpoint to = readEndpoint(object.object("to"), version);
        if (circuit.component(from.componentId()).isEmpty() || circuit.component(to.componentId()).isEmpty()) {
            throw new ProjectFormatException("A wire refers to a component that is not in the file");
        }
        List<CircuitPoint> waypoints = new ArrayList<>();
        for (JsonValue element : object.array("waypoints")) {
            if (element instanceof JsonValue.JsonObject point) {
                waypoints.add(new CircuitPoint(point.number("x", 0), point.number("y", 0)));
            }
        }
        return new Connection(readId(object, "wire"), from, to, waypoints);
    }

    private static dev.logicforge.circuit.document.PortEndpoint readEndpoint(
            JsonValue.JsonObject object, int version) {
        String component = object.string("component", "");
        String port = object.string("port", "");
        if (component.isBlank() || port.isBlank()) {
            throw new ProjectFormatException("A wire endpoint is missing its component or port");
        }
        PortReference ref = new PortReference(parseUuid(component, "wire endpoint"), port);
        if (object.members().containsKey("bit")) {
            if (version < 2) {
                throw new ProjectFormatException(
                        "Bit wire endpoints require formatVersion 2 or newer");
            }
            return dev.logicforge.circuit.document.PortEndpoint.bit(ref, object.integer("bit", 0));
        }
        if (object.members().containsKey("range")) {
            if (version < 3) {
                throw new ProjectFormatException(
                        "Range wire endpoints require formatVersion 3 or newer");
            }
            JsonValue.JsonObject range = object.object("range");
            try {
                return dev.logicforge.circuit.document.PortEndpoint.range(ref,
                        range.integer("msb", -1), range.integer("lsb", -1));
            } catch (IllegalArgumentException invalid) {
                throw new ProjectFormatException("Invalid range on " + port + ": "
                        + invalid.getMessage(), invalid);
            }
        }
        return dev.logicforge.circuit.document.PortEndpoint.whole(ref);
    }

    private static UUID readId(JsonValue.JsonObject object, String what) {
        String id = object.string("id", "");
        return id.isBlank() ? UUID.randomUUID() : parseUuid(id, what);
    }

    private static UUID parseUuid(String text, String what) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException failure) {
            throw new ProjectFormatException("Invalid " + what + " id '" + text + "'", failure);
        }
    }

    private static String fileName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
