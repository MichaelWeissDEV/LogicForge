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
 * version 4 adds stable, non-display semantic component roles; version 5 adds physical chip
 * packages and wires to their pins. The reader accepts every version.
 */
public final class ProjectFormat {

    /** The version this build writes. */
    public static final int FORMAT_VERSION = 5;

    /** File extension used by the file choosers. */
    public static final String EXTENSION = "logic";

    /**
     * Upper bound for a project file. The largest shipped example is well under 2 MiB; the
     * limit only stops an accidentally chosen huge file from exhausting memory.
     */
    static final long MAX_FILE_BYTES = 256L * 1024 * 1024;

    private ProjectFormat() {
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    /**
     * Writes the project to {@code file}. The file is replaced atomically: a failed or
     * interrupted save leaves the previous content untouched.
     */
    public static void save(CircuitProject project, Path file) {
        save(project, file, AtomicFileWriter.Backup.NONE);
    }

    /**
     * Writes the project to {@code file} atomically and, with
     * {@link AtomicFileWriter.Backup#KEEP_PREVIOUS}, keeps the version it replaces as
     * {@code <file>.bak}.
     */
    public static void save(CircuitProject project, Path file, AtomicFileWriter.Backup backup) {
        // Serialise first: nothing on disk is touched until the complete content exists.
        byte[] content = toJson(project).getBytes(StandardCharsets.UTF_8);
        try {
            AtomicFileWriter.write(file, content, backup);
        } catch (IOException | UncheckedIOException | SecurityException failure) {
            throw new ProjectFormatException("Could not write " + file + ": " + describe(failure),
                    failure);
        }
    }

    /**
     * The file a "Save As" to {@code chosen} should write: {@code my-project} becomes
     * {@code my-project.logic}, while a name that already ends in {@code .logic} (in any case)
     * is kept as it is.
     */
    public static Path withExtension(Path chosen) {
        String name = chosen.getFileName().toString();
        String suffix = "." + EXTENSION;
        if (name.length() > suffix.length()
                && name.regionMatches(true, name.length() - suffix.length(), suffix, 0, suffix.length())) {
            return chosen;
        }
        String base = name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
        return chosen.resolveSibling(base + suffix);
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

        JsonValue.JsonArray chips = new JsonValue.JsonArray();
        for (dev.logicforge.circuit.chip.ChipInstance chip : circuit.chips()) {
            chips.add(writeChip(chip));
        }
        if (!chips.isEmpty()) {
            object.put("chips", chips);
        }

        JsonValue.JsonArray connections = new JsonValue.JsonArray();
        for (Connection connection : circuit.connections()) {
            connections.add(writeConnection(connection));
        }
        object.put("connections", connections);
        return object;
    }

    private static JsonValue.JsonObject writeChip(dev.logicforge.circuit.chip.ChipInstance instance) {
        JsonValue.JsonObject object = new JsonValue.JsonObject();
        object.put("id", instance.id().toString());
        object.put("type", instance.chipDefinitionId());
        object.put("x", instance.position().x());
        object.put("y", instance.position().y());
        if (instance.rotation() != Rotation.DEG_0) {
            object.put("rotation", instance.rotation().degrees());
        }
        object.put("ref", instance.referenceDesignator());
        object.put("display", instance.displayMode().name());
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

    private static JsonValue.JsonObject writeEndpoint(dev.logicforge.circuit.document.ElectricalEndpoint electricalEndpoint) {
        if (electricalEndpoint instanceof dev.logicforge.circuit.document.ElectricalEndpoint.ChipPinEndpoint chipPin) {
            return new JsonValue.JsonObject()
                    .put("chip", chipPin.chipInstanceId().toString())
                    .put("pin", chipPin.physicalPinNumber());
        }
        
        dev.logicforge.circuit.document.ElectricalEndpoint.ComponentEndpoint compEndpoint = (dev.logicforge.circuit.document.ElectricalEndpoint.ComponentEndpoint) electricalEndpoint;
        dev.logicforge.circuit.document.PortEndpoint endpoint = compEndpoint.port();
        
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
        String text;
        try {
            if (!Files.exists(file)) {
                throw new ProjectFormatException("The file " + file + " does not exist");
            }
            if (!Files.isRegularFile(file)) {
                throw new ProjectFormatException(file + " is not a file");
            }
            if (Files.size(file) > MAX_FILE_BYTES) {
                throw new ProjectFormatException(file.getFileName() + " is too large to be a"
                        + " LogicForge project (" + Files.size(file) / (1024 * 1024) + " MiB)");
            }
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException | UncheckedIOException | SecurityException failure) {
            throw new ProjectFormatException("Could not read " + file + ": " + describe(failure),
                    failure);
        }
        return fromJson(text, fileName(file));
    }

    /** A short reason for an I/O failure, e.g. "permission denied" rather than a class name. */
    private static String describe(Exception failure) {
        return switch (failure) {
            case java.nio.file.AccessDeniedException ignored -> "permission denied";
            case java.nio.file.NoSuchFileException ignored -> "the file or its folder does not exist";
            case java.nio.charset.CharacterCodingException ignored -> "the file is not UTF-8 text";
            case java.nio.file.FileSystemException fileSystem when fileSystem.getReason() != null ->
                    fileSystem.getReason();
            default -> failure.getMessage() != null ? failure.getMessage()
                    : failure.getClass().getSimpleName();
        };
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
        if (version < 1) {
            throw new ProjectFormatException("Missing formatVersion: this is not a LogicForge project");
        }
        if (version > FORMAT_VERSION) {
            throw new ProjectFormatException("This project was saved with a newer version of LogicForge"
                    + " (format " + version + ", this build reads up to " + FORMAT_VERSION + ")");
        }

        try {
            return readProject(root, version, fallbackName);
        } catch (ProjectFormatException failure) {
            throw failure;
        } catch (RuntimeException damaged) {
            // The model rejects inconsistent content (a duplicate id, a 45° rotation, a chip
            // without a designator) with ordinary argument exceptions. For a file on disk those
            // all mean the same thing to the user: the project is damaged.
            throw new ProjectFormatException("This project file is damaged: " + damaged.getMessage(),
                    damaged);
        }
    }

    private static CircuitProject readProject(JsonValue.JsonObject root, int version,
                                              String fallbackName) {
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
        for (JsonValue element : object.array("chips")) {
            if (element instanceof JsonValue.JsonObject chip) {
                circuit.addChip(readChip(chip));
            }
        }
        for (JsonValue element : object.array("connections")) {
            if (element instanceof JsonValue.JsonObject connection) {
                circuit.addConnection(readConnection(connection, circuit, version));
            }
        }
        return circuit;
    }

    private static dev.logicforge.circuit.chip.ChipInstance readChip(JsonValue.JsonObject object) {
        String type = object.string("type", "");
        if (type.isBlank()) {
            throw new ProjectFormatException("A chip without a type id cannot be loaded");
        }
        
        dev.logicforge.circuit.chip.ChipDisplayMode displayMode = dev.logicforge.circuit.chip.ChipDisplayMode.PACKAGE;
        String displayStr = object.string("display", "");
        if (!displayStr.isBlank()) {
            try {
                displayMode = dev.logicforge.circuit.chip.ChipDisplayMode.valueOf(displayStr);
            } catch (IllegalArgumentException e) {
                // Ignore invalid enum values and fallback to PACKAGE
            }
        }
        
        return new dev.logicforge.circuit.chip.ChipInstance(
                readId(object, "chip"),
                type,
                new CircuitPoint(object.number("x", 0), object.number("y", 0)),
                Rotation.ofDegrees(object.integer("rotation", 0)),
                object.string("ref", ""),
                displayMode);
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
        dev.logicforge.circuit.document.ElectricalEndpoint from = readEndpoint(object.object("from"), version);
        dev.logicforge.circuit.document.ElectricalEndpoint to = readEndpoint(object.object("to"), version);
        
        if (!endpointTargetExists(from, circuit) || !endpointTargetExists(to, circuit)) {
            throw new ProjectFormatException("A wire refers to a component or chip that is not in the file");
        }
        
        List<CircuitPoint> waypoints = new ArrayList<>();
        for (JsonValue element : object.array("waypoints")) {
            if (element instanceof JsonValue.JsonObject point) {
                waypoints.add(new CircuitPoint(point.number("x", 0), point.number("y", 0)));
            }
        }
        return new Connection(readId(object, "wire"), from, to, waypoints);
    }
    
    private static boolean endpointTargetExists(dev.logicforge.circuit.document.ElectricalEndpoint endpoint, CircuitDocument circuit) {
        if (endpoint instanceof dev.logicforge.circuit.document.ElectricalEndpoint.ComponentEndpoint comp) {
            return circuit.component(comp.port().componentId()).isPresent();
        } else if (endpoint instanceof dev.logicforge.circuit.document.ElectricalEndpoint.ChipPinEndpoint chipPin) {
            return circuit.chip(chipPin.chipInstanceId()).isPresent();
        }
        return false;
    }

    private static dev.logicforge.circuit.document.ElectricalEndpoint readEndpoint(
            JsonValue.JsonObject object, int version) {
            
        if (version >= 5 && object.members().containsKey("chip") && object.members().containsKey("pin")) {
            return new dev.logicforge.circuit.document.ElectricalEndpoint.ChipPinEndpoint(
                    parseUuid(object.string("chip", ""), "wire endpoint"),
                    object.integer("pin", 0)
            );
        }
            
        String component = object.string("component", "");
        String port = object.string("port", "");
        if (component.isBlank() || port.isBlank()) {
            throw new ProjectFormatException("A wire endpoint is missing its component or port");
        }
        PortReference ref = new PortReference(parseUuid(component, "wire endpoint"), port);
        dev.logicforge.circuit.document.PortEndpoint portEndpoint;
        if (object.members().containsKey("bit")) {
            if (version < 2) {
                throw new ProjectFormatException(
                        "Bit wire endpoints require formatVersion 2 or newer");
            }
            portEndpoint = dev.logicforge.circuit.document.PortEndpoint.bit(ref, object.integer("bit", 0));
        } else if (object.members().containsKey("range")) {
            if (version < 3) {
                throw new ProjectFormatException(
                        "Range wire endpoints require formatVersion 3 or newer");
            }
            JsonValue.JsonObject range = object.object("range");
            try {
                portEndpoint = dev.logicforge.circuit.document.PortEndpoint.range(ref,
                        range.integer("msb", -1), range.integer("lsb", -1));
            } catch (IllegalArgumentException invalid) {
                throw new ProjectFormatException("Invalid range on " + port + ": "
                        + invalid.getMessage(), invalid);
            }
        } else {
            portEndpoint = dev.logicforge.circuit.document.PortEndpoint.whole(ref);
        }
        return new dev.logicforge.circuit.document.ElectricalEndpoint.ComponentEndpoint(portEndpoint);
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
