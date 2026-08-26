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
        assertTrue(json.contains("\"formatVersion\": " + ProjectFormat.FORMAT_VERSION));
        assertTrue(json.contains("\"application\": \"LogicForge\""));
    }

    @Test
    void filesFromANewerVersionAreRefusedClearly() {
        String json = ProjectFormat.toJson(CircuitProject.empty("future"))
                .replace("\"formatVersion\": " + ProjectFormat.FORMAT_VERSION,
                        "\"formatVersion\": 99");

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
    void rangeEndpointsRoundTripWithoutChangingOldEndpointShapes() {
        CircuitDocument circuit = new CircuitDocument();
        ComponentInstance source = ComponentInstance.create("routing.bus_constant",
                new CircuitPoint(0, 0), ComponentRegistry.standard()
                        .require("routing.bus_constant").definition().defaultParameters());
        ComponentInstance sink = ComponentInstance.create("routing.bus_probe",
                new CircuitPoint(100, 0), ComponentRegistry.standard()
                        .require("routing.bus_probe").definition().defaultParameters());
        circuit.addComponent(source);
        circuit.addComponent(sink);
        PortEndpoint from = PortEndpoint.range(new PortReference(source.id(), "OUT"), 7, 4);
        PortEndpoint to = PortEndpoint.range(new PortReference(sink.id(), "IN"), 3, 0);
        circuit.addConnection(Connection.create(from, to));

        CircuitProject loaded = roundTrip(CircuitProject.of("ranges", circuit));
        Connection loadedWire = loaded.mainCircuit().connections().iterator().next();

        assertEquals(from, loadedWire.from());
        assertEquals(to, loadedWire.to());
        assertTrue(ProjectFormat.toJson(loaded).contains("\"range\""));
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

    /**
     * The real blind spot in this test file: every other hierarchy/bit-mode/memory-contents
     * test here builds documents in memory and compiles them directly, never touching
     * save/load. This combines all three in one project — a bit-mode connection crossing
     * into a nested subcircuit's interface port, driving a ROM with non-trivial contents
     * inside it — round-trips it through the actual file format, then recompiles and
     * simulates the *loaded* project to prove the whole pipeline survives serialization,
     * not just the document shape.
     */
    @Test
    void nestedBitModeAndRomContentsSurviveARoundTripAndStillSimulateCorrectly() {
        CircuitDocument child = new CircuitDocument(new CircuitMetadata("Mem", ""));
        ComponentInstance addrIn = ComponentInstance.create(SubcircuitSupport.INPUT_DEFINITION_ID,
                new CircuitPoint(0, 0), ParameterValues.defaultsOf(java.util.List.of(
                                SubcircuitSupport.INTERFACE_NAME, SubcircuitSupport.INTERFACE_WIDTH))
                        .with(SubcircuitSupport.INTERFACE_NAME, "ADDR")
                        .with(SubcircuitSupport.INTERFACE_WIDTH, 1));
        ComponentInstance enIn = ComponentInstance.create(SubcircuitSupport.INPUT_DEFINITION_ID,
                new CircuitPoint(0, 60), ParameterValues.defaultsOf(java.util.List.of(
                                SubcircuitSupport.INTERFACE_NAME, SubcircuitSupport.INTERFACE_WIDTH))
                        .with(SubcircuitSupport.INTERFACE_NAME, "EN")
                        .with(SubcircuitSupport.INTERFACE_WIDTH, 1));
        ComponentInstance dataOut = ComponentInstance.create(SubcircuitSupport.OUTPUT_DEFINITION_ID,
                new CircuitPoint(300, 0), ParameterValues.defaultsOf(java.util.List.of(
                                SubcircuitSupport.INTERFACE_NAME, SubcircuitSupport.INTERFACE_WIDTH))
                        .with(SubcircuitSupport.INTERFACE_NAME, "DATA")
                        .with(SubcircuitSupport.INTERFACE_WIDTH, 8));
        ComponentInstance rom = ComponentInstance.create("memory.rom", new CircuitPoint(150, 0),
                ComponentRegistry.standard().require("memory.rom").definition().defaultParameters()
                        .with(LibraryParameters.ADDRESS_WIDTH, 1)
                        .with(LibraryParameters.WIDTH, 8)
                        .with(LibraryParameters.ROM_CONTENTS, "AB,CD"));
        child.addComponent(addrIn);
        child.addComponent(enIn);
        child.addComponent(dataOut);
        child.addComponent(rom);
        child.addConnection(Connection.create(new PortReference(addrIn.id(), "OUT"), new PortReference(rom.id(), "ADDRESS")));
        child.addConnection(Connection.create(new PortReference(enIn.id(), "OUT"), new PortReference(rom.id(), "ENABLE")));
        child.addConnection(Connection.create(new PortReference(rom.id(), "DATA"), new PortReference(dataOut.id(), "IN")));

        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance addrSource = ComponentInstance.create("routing.bus_constant", new CircuitPoint(0, 0),
                ComponentRegistry.standard().require("routing.bus_constant").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8)
                        .with(LibraryParameters.BUS_CONSTANT_VALUE, "01"));
        ComponentInstance enable = ComponentInstance.create("source.toggle",
                new CircuitPoint(0, 100), ParameterValues.empty()).withLabel("EN_SWITCH");
        ComponentInstance memInstance = SubcircuitSupport.instantiate("Mem", new CircuitPoint(150, 50));
        ComponentInstance probe = ComponentInstance.create("routing.bus_probe", new CircuitPoint(400, 0),
                ComponentRegistry.standard().require("routing.bus_probe").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8));
        main.addComponent(addrSource);
        main.addComponent(enable);
        main.addComponent(memInstance);
        main.addComponent(probe);
        // A bit endpoint on addrSource forces it into bit mode, crossing straight into the
        // nested subcircuit's 1-bit ADDR interface port.
        main.addConnection(Connection.create(PortEndpoint.bit(new PortReference(addrSource.id(), "OUT"), 0),
                PortEndpoint.whole(new PortReference(memInstance.id(), "ADDR"))));
        main.addConnection(Connection.create(new PortReference(enable.id(), "OUT"),
                new PortReference(memInstance.id(), "EN")));
        main.addConnection(Connection.create(new PortReference(memInstance.id(), "DATA"),
                new PortReference(probe.id(), "IN")));

        CircuitProject project = CircuitProject.of("nested-rom", main);
        project.putCircuit(child);

        CircuitProject loaded = roundTrip(project);

        dev.logicforge.compiler.CompilationResult compiled = new dev.logicforge.compiler.CircuitCompiler(
                ComponentRegistry.standard()).compile(loaded, "main");
        dev.logicforge.simulation.Simulation simulation = new dev.logicforge.simulation.Simulation(compiled.circuit());
        int enableId = compiled.componentByLabel("EN_SWITCH").orElseThrow();

        simulation.setInput(enableId, dev.logicforge.logic.LogicState.ONE);
        int dataNet = compiled.sourceMap().netOf(new PortReference(probe.id(), "IN")).orElseThrow();

        assertEquals(dev.logicforge.logic.LogicVector.fromUnsignedLong(0xCD, 8), simulation.readNet(dataNet),
                "ADDR bit 0 of constant 0x01 selects contents[1] = 0xCD, even after a save/load cycle");
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
