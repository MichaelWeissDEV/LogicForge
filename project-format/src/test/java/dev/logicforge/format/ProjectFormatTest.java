package dev.logicforge.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitMetadata;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortDisplayMode;
import dev.logicforge.circuit.document.SubcircuitSupport;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.Rotation;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.LibraryParameters;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectFormatTest {

    @TempDir
    Path directory;

    @Test
    void savingAndLoadingKeepsTheCircuitIdentical() {
        CircuitProject project = CircuitProject.of("half-adder", halfAdder());
        Path file = directory.resolve("half-adder.logic");

        ProjectFormat.save(project, file);
        CircuitProject loaded = ProjectFormat.load(file);

        assertEquals("half-adder", loaded.name());
        assertTrue(project.mainCircuit().structurallyEquals(loaded.mainCircuit()),
                "components, positions, rotations, parameters, labels and wires all survive");
    }

    @Test
    void identityIsStableAcrossASaveAndLoadCycle() {
        CircuitDocument circuit = halfAdder();
        ComponentInstance gate = circuit.components().stream()
                .filter(component -> component.definitionId().equals("logic.and"))
                .findFirst().orElseThrow();

        CircuitProject loaded = roundTrip(CircuitProject.of("ids", circuit));

        ComponentInstance sameGate = loaded.mainCircuit().requireComponent(gate.id());
        assertEquals(gate.definitionId(), sameGate.definitionId());
        assertEquals(gate.position(), sameGate.position());
        assertEquals(gate.label(), sameGate.label());
    }

    @Test
    void everythingAboutAComponentIsPreserved() {
        CircuitDocument circuit = new CircuitDocument();
        ComponentInstance gate = ComponentInstance
                .create("logic.nand", new CircuitPoint(112.5, -48), ParameterValues.empty()
                        .with(LibraryParameters.INPUT_COUNT, 5))
                .withRotation(Rotation.DEG_270)
                .withLabel("ENABLE_LOGIC");
        circuit.addComponent(gate);

        ComponentInstance loaded = roundTrip(CircuitProject.of("p", circuit))
                .mainCircuit().requireComponent(gate.id());

        assertEquals(new CircuitPoint(112.5, -48), loaded.position());
        assertEquals(Rotation.DEG_270, loaded.rotation());
        assertEquals("ENABLE_LOGIC", loaded.label());
        assertEquals(5, loaded.parameters().getInt(LibraryParameters.INPUT_COUNT));
    }

    @Test
    void manualWireRoutingIsPreserved() {
        CircuitDocument circuit = new CircuitDocument();
        ComponentInstance source = ComponentInstance.create("source.toggle",
                new CircuitPoint(0, 0), ParameterValues.empty());
        ComponentInstance sink = ComponentInstance.create("output.led",
                new CircuitPoint(200, 0), ParameterValues.empty());
        circuit.addComponent(source);
        circuit.addComponent(sink);
        circuit.addConnection(new Connection(java.util.UUID.randomUUID(),
                dev.logicforge.circuit.document.PortEndpoint.whole(new PortReference(source.id(), "OUT")), dev.logicforge.circuit.document.PortEndpoint.whole(new PortReference(sink.id(), "IN")),
                java.util.List.of(new CircuitPoint(64, 0), new CircuitPoint(64, 48))));

        CircuitDocument loaded = roundTrip(CircuitProject.of("p", circuit)).mainCircuit();

        Connection wire = loaded.connections().iterator().next();
        assertEquals(java.util.List.of(new CircuitPoint(64, 0), new CircuitPoint(64, 48)),
                wire.waypoints());
    }

    @Test
    void savingIsReproducible() {
        CircuitProject project = CircuitProject.of("stable", halfAdder());
        assertEquals(ProjectFormat.toJson(project), ProjectFormat.toJson(project));
        assertEquals(ProjectFormat.toJson(project),
                ProjectFormat.toJson(roundTrip(project)), "a load/save cycle changes nothing");
    }

    @Test
    void theFileCarriesItsFormatVersion() {
        String json = ProjectFormat.toJson(CircuitProject.empty("empty"));
        assertTrue(json.contains("\"formatVersion\": 2"));
        assertTrue(json.contains("\"application\": \"LogicForge\""));
    }

    @Test
    void filesFromANewerVersionAreRefusedClearly() {
        String json = ProjectFormat.toJson(CircuitProject.empty("future"))
                .replace("\"formatVersion\": 2", "\"formatVersion\": 99");

        ProjectFormatException failure = assertThrows(ProjectFormatException.class,
                () -> ProjectFormat.fromJson(json, "future"));
        assertTrue(failure.getMessage().contains("newer version"));
    }

    @Test
    void brokenFilesFailWithAReadableMessage() {
        assertThrows(ProjectFormatException.class, () -> ProjectFormat.fromJson("{ nonsense", "x"));
        assertThrows(ProjectFormatException.class, () -> ProjectFormat.fromJson("{}", "x"));
        assertThrows(ProjectFormatException.class,
                () -> ProjectFormat.fromJson("{\"formatVersion\": 1, \"circuits\": []}", "x"));
        assertThrows(ProjectFormatException.class, () -> ProjectFormat.load(directory.resolve("missing.logic")));
    }

    @Test
    void aWireWithoutItsComponentsIsRejected() {
        String json = """
                {
                  "formatVersion": 1,
                  "name": "broken",
                  "circuits": [
                    {
                      "name": "main",
                      "components": [],
                      "connections": [
                        {
                          "id": "00000000-0000-0000-0000-000000000001",
                          "from": {"component": "00000000-0000-0000-0000-000000000002", "port": "OUT"},
                          "to": {"component": "00000000-0000-0000-0000-000000000003", "port": "IN"}
                        }
                      ]
                    }
                  ]
                }
                """;
        assertThrows(ProjectFormatException.class, () -> ProjectFormat.fromJson(json, "broken"));
    }

    @Test
    void versionOneWholeEndpointsLoadAsCurrentModel() {
        String sourceId = "00000000-0000-0000-0000-000000000001";
        String sinkId = "00000000-0000-0000-0000-000000000002";
        String json = """
                {
                  "formatVersion": 1,
                  "name": "legacy",
                  "circuits": [{
                    "name": "main",
                    "components": [
                      {"id": "%s", "type": "source.toggle", "x": 0, "y": 0},
                      {"id": "%s", "type": "output.led", "x": 100, "y": 0}
                    ],
                    "connections": [{
                      "from": {"component": "%s", "port": "OUT"},
                      "to": {"component": "%s", "port": "IN"}
                    }]
                  }]
                }
                """.formatted(sourceId, sinkId, sourceId, sinkId);

        Connection loaded = ProjectFormat.fromJson(json, "legacy").mainCircuit()
                .connections().iterator().next();
        assertTrue(loaded.from().isWhole());
        assertTrue(loaded.to().isWhole());
    }

    @Test
    void versionTwoBitEndpointsAndPresentationRoundTrip() {
        CircuitDocument circuit = new CircuitDocument();
        ComponentInstance source = ComponentInstance.create("source.toggle",
                new CircuitPoint(0, 0), ParameterValues.empty());
        ComponentInstance register = ComponentInstance.create("sequential.register",
                new CircuitPoint(100, 0), ComponentRegistry.standard()
                        .require("sequential.register").definition().defaultParameters())
                .withPortDisplayMode(PortDisplayMode.EXPANDED);
        circuit.addComponent(source);
        circuit.addComponent(register);
        PortEndpoint endpoint = PortEndpoint.bit(new PortReference(register.id(), "DATA"), 3);
        circuit.addConnection(Connection.create(
                PortEndpoint.whole(new PortReference(source.id(), "OUT")), endpoint));

        CircuitDocument loaded = roundTrip(CircuitProject.of("bits", circuit)).mainCircuit();
        ComponentInstance loadedRegister = loaded.requireComponent(register.id());
        Connection loadedWire = loaded.connections().iterator().next();

        assertEquals(PortDisplayMode.EXPANDED, loadedRegister.portDisplayMode());
        assertEquals(endpoint, loadedWire.to());
    }

    @Test
    void versionOneCannotClaimBitEndpointSemantics() {
        String json = """
                {
                  "formatVersion": 1,
                  "circuits": [{
                    "name": "main",
                    "components": [
                      {"id": "00000000-0000-0000-0000-000000000001", "type": "source.toggle"},
                      {"id": "00000000-0000-0000-0000-000000000002", "type": "output.led"}
                    ],
                    "connections": [{
                      "from": {"component": "00000000-0000-0000-0000-000000000001", "port": "OUT"},
                      "to": {"component": "00000000-0000-0000-0000-000000000002", "port": "IN", "bit": 0}
                    }]
                  }]
                }
                """;
        ProjectFormatException error = assertThrows(ProjectFormatException.class,
                () -> ProjectFormat.fromJson(json, "bad-v1"));
        assertTrue(error.getMessage().contains("formatVersion 2"));
    }

    @Test
    void childCircuitAndParentInstanceRoundTripTogether() {
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance childInstance = SubcircuitSupport.instantiate(
                "BytePipe", new CircuitPoint(120, 80));
        main.addComponent(childInstance);
        CircuitDocument child = new CircuitDocument(new CircuitMetadata("BytePipe", ""));
        child.addComponent(ComponentInstance.create(SubcircuitSupport.INPUT_DEFINITION_ID,
                new CircuitPoint(0, 0), ParameterValues.defaultsOf(java.util.List.of(
                                SubcircuitSupport.INTERFACE_NAME, SubcircuitSupport.INTERFACE_WIDTH))
                        .with(SubcircuitSupport.INTERFACE_NAME, "DATA")
                        .with(SubcircuitSupport.INTERFACE_WIDTH, 8)));
        CircuitProject project = CircuitProject.of("hierarchy", main);
        project.putCircuit(child);

        CircuitProject loaded = roundTrip(project);

        assertTrue(loaded.circuit("BytePipe").isPresent());
        assertEquals(SubcircuitSupport.definitionId("BytePipe"),
                loaded.mainCircuit().requireComponent(childInstance.id()).definitionId());
    }

    @Test
    void everyComponentOfTheLibrarySurvivesARoundTrip() {
        CircuitDocument circuit = new CircuitDocument(new CircuitMetadata("main", "one of everything"));
        ComponentRegistry registry = ComponentRegistry.standard();
        double x = 0;
        for (var type : registry.all()) {
            circuit.addComponent(ComponentInstance.create(type.id(), new CircuitPoint(x += 80, 80),
                    type.definition().defaultParameters()));
        }

        CircuitDocument loaded = roundTrip(CircuitProject.of("all", circuit)).mainCircuit();

        assertEquals(registry.size(), loaded.componentCount());
        assertTrue(circuit.structurallyEquals(loaded));
    }

    @Test
    void loadedFilesReadTheirNameFromTheFileWhenItIsMissing() throws Exception {
        Path file = directory.resolve("my-circuit.logic");
        Files.writeString(file, """
                {
                  "formatVersion": 1,
                  "circuits": [{"name": "main", "components": [], "connections": []}]
                }
                """);

        assertEquals("my-circuit", ProjectFormat.load(file).name());
    }

    private CircuitProject roundTrip(CircuitProject project) {
        Path file = directory.resolve(project.name() + "." + ProjectFormat.EXTENSION);
        ProjectFormat.save(project, file);
        return ProjectFormat.load(file);
    }

    /** Two switches, an XOR and an AND, two LEDs — the same circuit the examples use. */
    private static CircuitDocument halfAdder() {
        CircuitDocument circuit = new CircuitDocument(new CircuitMetadata("main", "Half adder"));
        ComponentInstance a = add(circuit, "source.toggle", 0, 0, "A");
        ComponentInstance b = add(circuit, "source.toggle", 0, 100, "B");
        ComponentInstance xor = add(circuit, "logic.xor", 150, 30, "SUM_XOR");
        ComponentInstance and = add(circuit, "logic.and", 150, 130, "CARRY_AND");
        ComponentInstance sum = add(circuit, "output.led", 300, 30, "SUM");
        ComponentInstance carry = add(circuit, "output.led", 300, 130, "CARRY");
        wire(circuit, a, "OUT", xor, "IN0");
        wire(circuit, b, "OUT", xor, "IN1");
        wire(circuit, a, "OUT", and, "IN0");
        wire(circuit, b, "OUT", and, "IN1");
        wire(circuit, xor, "OUT", sum, "IN");
        wire(circuit, and, "OUT", carry, "IN");
        return circuit;
    }

    private static ComponentInstance add(CircuitDocument circuit, String type, double x, double y,
                                         String label) {
        ComponentInstance instance = ComponentInstance
                .create(type, new CircuitPoint(x, y), ParameterValues.empty()).withLabel(label);
        circuit.addComponent(instance);
        return instance;
    }

    private static void wire(CircuitDocument circuit, ComponentInstance from, String fromPort,
                             ComponentInstance to, String toPort) {
        circuit.addConnection(Connection.create(new PortReference(from.id(), fromPort),
                new PortReference(to.id(), toPort)));
    }
}
