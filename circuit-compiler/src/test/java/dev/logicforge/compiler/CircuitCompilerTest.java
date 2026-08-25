package dev.logicforge.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.component.ComponentCategory;
import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.component.ParameterSpec;
import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.component.PortDirection;
import dev.logicforge.circuit.component.PortSpec;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.CircuitSize;
import dev.logicforge.circuit.geometry.PortSide;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.ComponentType;
import dev.logicforge.library.LibraryParameters;
import dev.logicforge.library.behavior.ConstantBehavior;
import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.CompiledCircuit;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.Simulation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CircuitCompilerTest {

    @Test
    void bitEndpointOnlyAffectsSelectedBusBitAndLeavesOthersUndriven() {
        ComponentRegistry registry = createWidthTestRegistry();
        CircuitDocument document = new CircuitDocument();
        ComponentInstance source = ComponentInstance.create(
                "test.output8", new CircuitPoint(0, 0), ParameterValues.empty());
        ComponentInstance sink = ComponentInstance.create(
                "test.input8", new CircuitPoint(100, 0), ParameterValues.empty());
        document.addComponent(source);
        document.addComponent(sink);
        PortEndpoint sourceBit = PortEndpoint.bit(new PortReference(source.id(), "OUT"), 0);
        PortEndpoint sinkBit = PortEndpoint.bit(new PortReference(sink.id(), "IN"), 3);
        document.addConnection(Connection.create(sourceBit, sinkBit));

        CompilationResult result = new CircuitCompiler(registry).compile(document);
        Simulation simulation = new Simulation(result.circuit());
        int sinkId = result.sourceMap().componentId(sink.id()).orElseThrow();

        assertEquals(LogicVector.of("ZZZZ1ZZZ"), simulation.readInput(sinkId, 0));
        assertEquals(1, result.circuit().net(
                result.sourceMap().netOf(sinkBit).orElseThrow()).width().bits());
        assertTrue(result.sourceMap().netOf(new PortReference(sink.id(), "IN")).isEmpty(),
                "a bit-bound bus has no single whole-port net");
    }

    @Test
    void multipleBitEndpointsAssembleOneLogicalVector() {
        ComponentRegistry registry = createWidthTestRegistry();
        CircuitDocument document = new CircuitDocument();
        ComponentInstance source = ComponentInstance.create(
                "test.output8", new CircuitPoint(0, 0), ParameterValues.empty());
        ComponentInstance sink = ComponentInstance.create(
                "test.input8", new CircuitPoint(100, 0), ParameterValues.empty());
        document.addComponent(source);
        document.addComponent(sink);
        for (int bit = 0; bit < 8; bit++) {
            document.addConnection(Connection.create(
                    PortEndpoint.bit(new PortReference(source.id(), "OUT"), bit),
                    PortEndpoint.bit(new PortReference(sink.id(), "IN"), bit)));
        }

        CompilationResult result = new CircuitCompiler(registry).compile(document);
        int sinkId = result.sourceMap().componentId(sink.id()).orElseThrow();

        assertEquals(LogicVector.repeat(LogicState.ONE, 8),
                new Simulation(result.circuit()).readInput(sinkId, 0));
    }

    @Test
    void wholeAndBitConnectionsOnSameLogicalPortAreRejected() {
        ComponentRegistry registry = createWidthTestRegistry();
        CircuitDocument document = new CircuitDocument();
        ComponentInstance source = ComponentInstance.create(
                "test.output8", new CircuitPoint(0, 0), ParameterValues.empty());
        ComponentInstance first = ComponentInstance.create(
                "test.input8", new CircuitPoint(100, 0), ParameterValues.empty());
        ComponentInstance scalar = ComponentInstance.create(
                "test.input1", new CircuitPoint(100, 50), ParameterValues.empty());
        document.addComponent(source);
        document.addComponent(first);
        document.addComponent(scalar);
        PortReference output = new PortReference(source.id(), "OUT");
        document.addConnection(Connection.create(output, new PortReference(first.id(), "IN")));
        document.addConnection(Connection.create(PortEndpoint.bit(output, 3),
                PortEndpoint.whole(new PortReference(scalar.id(), "IN"))));

        CircuitCompileException error = assertThrows(CircuitCompileException.class,
                () -> new CircuitCompiler(registry).compile(document));
        assertTrue(error.getMessage().contains("whole-port and bit-level"));
    }

    @Test
    void outOfRangeBitEndpointIsRejected() {
        ComponentRegistry registry = createWidthTestRegistry();
        CircuitDocument document = new CircuitDocument();
        ComponentInstance source = ComponentInstance.create(
                "test.output8", new CircuitPoint(0, 0), ParameterValues.empty());
        ComponentInstance scalar = ComponentInstance.create(
                "test.input1", new CircuitPoint(100, 0), ParameterValues.empty());
        document.addComponent(source);
        document.addComponent(scalar);
        document.addConnection(Connection.create(
                PortEndpoint.bit(new PortReference(source.id(), "OUT"), 8),
                PortEndpoint.whole(new PortReference(scalar.id(), "IN"))));

        CircuitCompileException error = assertThrows(CircuitCompileException.class,
                () -> new CircuitCompiler(registry).compile(document));
        assertTrue(error.getMessage().contains("outside"));
    }

    @Test
    void oneBitWholePortConnectsToBusBit() {
        ComponentRegistry registry = createWidthTestRegistry();
        CircuitDocument document = new CircuitDocument();
        ComponentInstance source = ComponentInstance.create(
                "test.output8", new CircuitPoint(0, 0), ParameterValues.empty());
        ComponentInstance scalar = ComponentInstance.create(
                "test.input1", new CircuitPoint(100, 0), ParameterValues.empty());
        document.addComponent(source);
        document.addComponent(scalar);
        PortEndpoint bit = PortEndpoint.bit(new PortReference(source.id(), "OUT"), 5);
        PortEndpoint whole = PortEndpoint.whole(new PortReference(scalar.id(), "IN"));
        document.addConnection(Connection.create(bit, whole));

        CompilationResult result = new CircuitCompiler(registry).compile(document);
        int scalarId = result.sourceMap().componentId(scalar.id()).orElseThrow();
        assertEquals(LogicVector.ONE, new Simulation(result.circuit()).readInput(scalarId, 0));
        assertEquals(result.sourceMap().netOf(bit).orElseThrow(),
                result.sourceMap().netOf(whole).orElseThrow());
    }

    @Test
    void connectedPortsShareOneNet() {
        CircuitBuilder circuit = new CircuitBuilder();
        circuit.add("source.toggle", "A");
        circuit.add("source.toggle", "B");
        circuit.add("logic.and", "AND1");
        circuit.add("output.led", "LED1");
        circuit.wire("A", "OUT", "AND1", "IN0")
                .wire("B", "OUT", "AND1", "IN1")
                .wire("AND1", "OUT", "LED1", "IN");

        CompilationResult result = circuit.compile();
        CircuitSourceMap map = result.sourceMap();

        assertEquals(3, result.circuit().netCount(), "three wires, three nets, nothing left over");
        assertEquals(map.netOf(circuit.port("A", "OUT")).orElseThrow(),
                map.netOf(circuit.port("AND1", "IN0")).orElseThrow());
        assertEquals(map.netOf(circuit.port("AND1", "OUT")).orElseThrow(),
                map.netOf(circuit.port("LED1", "IN")).orElseThrow());
        assertNotEquals(map.netOf(circuit.port("AND1", "IN0")).orElseThrow(),
                map.netOf(circuit.port("AND1", "IN1")).orElseThrow());
    }

    @Test
    void everyUnconnectedPortStillGetsItsOwnNet() {
        CircuitBuilder circuit = new CircuitBuilder();
        circuit.add("logic.and", "AND1");

        CompilationResult result = circuit.compile();

        assertEquals(3, result.circuit().netCount(), "two inputs and one output, none of them wired");
        assertTrue(result.issues().stream()
                        .anyMatch(issue -> issue.severity() == ValidationIssue.Severity.INFO
                                && issue.message().contains("no driver")),
                "a net nobody drives is worth reporting");
    }

    @Test
    void driversAndConsumersAreRecordedPerNet() {
        CircuitBuilder circuit = new CircuitBuilder();
        circuit.add("source.one", "VCC");
        circuit.add("logic.not", "N1");
        circuit.add("logic.not", "N2");
        circuit.wire("VCC", "OUT", "N1", "A").wire("VCC", "OUT", "N2", "A");

        CompilationResult result = circuit.compile();
        int net = result.sourceMap().netOf(circuit.port("VCC", "OUT")).orElseThrow();

        assertEquals(1, result.circuit().net(net).driverCount());
        assertEquals(2, result.circuit().net(net).consumerCount(), "one source feeds two inverters");
        assertEquals(3, result.sourceMap().portsOf(net).size());
    }

    @Test
    void unknownComponentsStopTheCompilation() {
        CircuitBuilder circuit = new CircuitBuilder();
        circuit.add("logic.and", "AND1");
        circuit.document().addComponent(ComponentInstance.create("logic.does.not.exist",
                new dev.logicforge.circuit.geometry.CircuitPoint(0, 0), ParameterValues.empty()));

        CircuitCompileException failure = assertThrows(CircuitCompileException.class, circuit::compile);
        assertEquals(1, failure.errors().size());
        assertTrue(failure.getMessage().contains("logic.does.not.exist"));
    }

    @Test
    void wiresToPortsThatNoLongerExistAreReportedAndIgnored() {
        CircuitBuilder circuit = new CircuitBuilder();
        circuit.add("source.toggle", "A");
        ComponentInstance gate = circuit.add("logic.and", "AND1",
                ParameterValues.empty().with(LibraryParameters.INPUT_COUNT, 4));
        circuit.wire("A", "OUT", "AND1", "IN3");
        circuit.replace(gate.withParameters(ParameterValues.empty().with(LibraryParameters.INPUT_COUNT, 2)));

        CompilationResult result = circuit.compile();

        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).message().contains("no longer exists"));
        assertTrue(result.sourceMap().netOf(circuit.port("A", "OUT")).isPresent());
    }

    @Test
    void twoOutputsOnOneNetAreAllowedButFlagged() {
        CircuitBuilder circuit = new CircuitBuilder();
        circuit.add("source.one", "VCC");
        circuit.add("source.zero", "GND");
        circuit.add("output.probe", "P");
        circuit.wire("VCC", "OUT", "GND", "OUT").wire("VCC", "OUT", "P", "IN");

        CompilationResult result = circuit.compile();
        int net = result.sourceMap().netOf(circuit.port("VCC", "OUT")).orElseThrow();

        assertEquals(1, result.warnings().size(), "the editor should point this out");
        assertEquals(2, result.circuit().net(net).driverCount());

        Simulation simulation = new Simulation(result.circuit());
        assertEquals(LogicVector.UNKNOWN, simulation.readNet(net), "0 against 1 resolves to X");
        assertTrue(simulation.hasDriverConflict(net));
    }

    @Test
    void compilingTwiceProducesIdenticalStructure() {
        CircuitBuilder circuit = halfAdder();

        CompilationResult first = circuit.compile();
        CompilationResult second = circuit.compile();

        assertEquals(first.circuit().netCount(), second.circuit().netCount());
        assertEquals(first.circuit().componentCount(), second.circuit().componentCount());
        assertEquals(first.sourceMap().netByPort(), second.sourceMap().netByPort());
        for (int id = 0; id < first.circuit().componentCount(); id++) {
            assertEquals(first.circuit().component(id).definitionId(),
                    second.circuit().component(id).definitionId());
        }
    }

    @Test
    void aCompiledCircuitRunsHeadlessly() {
        CircuitBuilder circuit = halfAdder();
        CompilationResult result = circuit.compile();
        Simulation simulation = new Simulation(result.circuit());

        int switchA = result.componentByLabel("A").orElseThrow();
        int switchB = result.componentByLabel("B").orElseThrow();
        int sumNet = result.sourceMap().netOf(circuit.port("SUM", "IN")).orElseThrow();
        int carryNet = result.sourceMap().netOf(circuit.port("CARRY", "IN")).orElseThrow();

        assertHalfAdder(simulation, switchA, switchB, sumNet, carryNet,
                LogicState.ZERO, LogicState.ZERO, LogicState.ZERO, LogicState.ZERO);
        assertHalfAdder(simulation, switchA, switchB, sumNet, carryNet,
                LogicState.ONE, LogicState.ZERO, LogicState.ONE, LogicState.ZERO);
        assertHalfAdder(simulation, switchA, switchB, sumNet, carryNet,
                LogicState.ZERO, LogicState.ONE, LogicState.ONE, LogicState.ZERO);
        assertHalfAdder(simulation, switchA, switchB, sumNet, carryNet,
                LogicState.ONE, LogicState.ONE, LogicState.ZERO, LogicState.ONE);
    }

    @Test
    void reconfiguringAGateChangesItsCompiledPorts() {
        CircuitBuilder circuit = new CircuitBuilder();
        ComponentInstance gate = circuit.add("logic.and", "AND1");
        assertEquals(2, circuit.compile().circuit().component(0).inputCount());

        circuit.replace(gate.withParameters(
                ParameterValues.empty().with(LibraryParameters.INPUT_COUNT, 5)));

        assertEquals(5, circuit.compile().circuit().component(0).inputCount());
    }

    @Test
    void theSourceMapTranslatesBothWays() {
        CircuitBuilder circuit = new CircuitBuilder();
        ComponentInstance toggle = circuit.add("source.toggle", "A");
        circuit.add("output.led", "LED1");
        circuit.wire("A", "OUT", "LED1", "IN");

        CompilationResult result = circuit.compile();
        CircuitSourceMap map = result.sourceMap();
        int componentId = map.componentId(toggle.id()).orElseThrow();

        assertEquals(toggle.id(), map.componentUuid(componentId).orElseThrow());
        UUID wire = circuit.document().connections().iterator().next().id();
        assertEquals(map.netOf(circuit.port("A", "OUT")).orElseThrow(),
                map.netOfConnection(wire).orElseThrow());
        assertTrue(map.componentId(UUID.randomUUID()).isEmpty());
        assertFalse(map.portsOf(0).isEmpty());
    }

    @Test
    void chainsOfWiresFormASingleNet() {
        CircuitBuilder circuit = new CircuitBuilder();
        circuit.add("source.one", "VCC");
        circuit.add("output.probe", "P1");
        circuit.add("output.probe", "P2");
        circuit.add("output.probe", "P3");
        // A branching wire: three probes hang off the same signal via a chain of segments.
        circuit.wire("VCC", "OUT", "P1", "IN").wire("P1", "IN", "P2", "IN").wire("P2", "IN", "P3", "IN");

        CompilationResult result = circuit.compile();

        assertEquals(1, result.circuit().netCount());
        assertEquals(4, result.sourceMap().portsOf(0).size());
        assertEquals(LogicVector.ONE, new Simulation(result.circuit()).readNet(0));
    }

    @Test
    void validationDoesNotBuildAnything() {
        CircuitBuilder circuit = new CircuitBuilder();
        circuit.add("logic.and", "AND1");
        List<ValidationIssue> issues = new CircuitCompiler(circuit.registry()).validate(circuit.document());

        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().noneMatch(ValidationIssue::isError));
    }

    private static void assertHalfAdder(Simulation simulation, int switchA, int switchB,
                                        int sumNet, int carryNet, LogicState a, LogicState b,
                                        LogicState sum, LogicState carry) {
        simulation.setInput(switchA, a);
        simulation.setInput(switchB, b);
        assertEquals(LogicVector.single(sum), simulation.readNet(sumNet), a + " + " + b + " sum");
        assertEquals(LogicVector.single(carry), simulation.readNet(carryNet), a + " + " + b + " carry");
    }

    /** Two switches into an XOR (sum) and an AND (carry), each feeding an LED. */
    private static CircuitBuilder halfAdder() {
        CircuitBuilder circuit = new CircuitBuilder();
        circuit.add("source.toggle", "A");
        circuit.add("source.toggle", "B");
        circuit.add("logic.xor", "XOR1");
        circuit.add("logic.and", "AND1");
        circuit.add("output.led", "SUM");
        circuit.add("output.led", "CARRY");
        circuit.wire("A", "OUT", "XOR1", "IN0")
                .wire("B", "OUT", "XOR1", "IN1")
                .wire("A", "OUT", "AND1", "IN0")
                .wire("B", "OUT", "AND1", "IN1")
                .wire("XOR1", "OUT", "SUM", "IN")
                .wire("AND1", "OUT", "CARRY", "IN");
        return circuit;
    }

    // ========== Width Mismatch Tests ==========

    @Test
    void widthMismatch_8bitTo1bit_throwsCompileError() {
        // Create a registry with synthetic 8-bit and 1-bit components
        ComponentRegistry registry = createWidthTestRegistry();
        CircuitCompiler compiler = new CircuitCompiler(registry);

        CircuitDocument document = new CircuitDocument();
        // Add 8-bit output component
        ComponentInstance output8 = ComponentInstance.create("test.output8",
                new CircuitPoint(0, 0), ParameterValues.empty());
        document.addComponent(output8);
        // Add 1-bit input component
        ComponentInstance input1 = ComponentInstance.create("test.input1",
                new CircuitPoint(100, 0), ParameterValues.empty());
        document.addComponent(input1);
        // Connect them - this should cause a width mismatch
        document.addConnection(Connection.create(
                new PortReference(output8.id(), "OUT"),
                new PortReference(input1.id(), "IN")));

        CircuitCompileException exception = assertThrows(CircuitCompileException.class,
                () -> compiler.compile(document));

        assertFalse(exception.errors().isEmpty(), "Must have at least one error");
        assertTrue(exception.errors().stream()
                        .anyMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR
                                && issue.message().contains("wide")),
                "Must have a width mismatch error");
    }

    @Test
    void widthMismatch_8bitTo8bit_compilesSuccessfully() {
        ComponentRegistry registry = createWidthTestRegistry();
        CircuitCompiler compiler = new CircuitCompiler(registry);

        CircuitDocument document = new CircuitDocument();
        // Add two 8-bit components
        ComponentInstance output8 = ComponentInstance.create("test.output8",
                new CircuitPoint(0, 0), ParameterValues.empty());
        document.addComponent(output8);
        ComponentInstance input8 = ComponentInstance.create("test.input8",
                new CircuitPoint(100, 0), ParameterValues.empty());
        document.addComponent(input8);
        // Connect them - same width, should work
        document.addConnection(Connection.create(
                new PortReference(output8.id(), "OUT"),
                new PortReference(input8.id(), "IN")));

        CompilationResult result = compiler.compile(document);
        assertEquals(1, result.circuit().netCount());
        assertTrue(result.issues().stream().noneMatch(ValidationIssue::isError), "Must have no errors");
    }

    @Test
    void widthMismatch_validateReturnsIssuesWithoutCompiling() {
        ComponentRegistry registry = createWidthTestRegistry();
        CircuitCompiler compiler = new CircuitCompiler(registry);

        CircuitDocument document = new CircuitDocument();
        ComponentInstance output8 = ComponentInstance.create("test.output8",
                new CircuitPoint(0, 0), ParameterValues.empty());
        document.addComponent(output8);
        ComponentInstance input1 = ComponentInstance.create("test.input1",
                new CircuitPoint(100, 0), ParameterValues.empty());
        document.addComponent(input1);
        document.addConnection(Connection.create(
                new PortReference(output8.id(), "OUT"),
                new PortReference(input1.id(), "IN")));

        List<ValidationIssue> issues = compiler.validate(document);
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(ValidationIssue::isError));
        assertTrue(issues.stream()
                        .anyMatch(issue -> issue.message().contains("wide")));
    }

    @Test
    void compileNeverTruncatesSilently() {
        ComponentRegistry registry = createWidthTestRegistry();
        CircuitCompiler compiler = new CircuitCompiler(registry);

        CircuitDocument document = new CircuitDocument();
        ComponentInstance output8 = ComponentInstance.create("test.output8",
                new CircuitPoint(0, 0), ParameterValues.empty());
        document.addComponent(output8);
        ComponentInstance input1 = ComponentInstance.create("test.input1",
                new CircuitPoint(100, 0), ParameterValues.empty());
        document.addComponent(input1);
        document.addConnection(Connection.create(
                new PortReference(output8.id(), "OUT"),
                new PortReference(input1.id(), "IN")));

        try {
            compiler.compile(document);
            // If we reach here, compilation succeeded, which means it silently truncated
            // This is what we want to prevent
            assertFalse(true, "Compilation must fail for width mismatch, not truncate");
        } catch (CircuitCompileException e) {
            // Expected - compilation must fail
            assertTrue(e.errors().stream().anyMatch(ValidationIssue::isError));
        }
    }

    @Test
    void deterministischeNetIds() {
        CircuitBuilder circuit = new CircuitBuilder();
        circuit.add("source.one", "VCC");
        circuit.add("source.zero", "GND");
        circuit.add("logic.and", "AND1");
        circuit.wire("VCC", "OUT", "AND1", "IN0");
        circuit.wire("GND", "OUT", "AND1", "IN1");

        CompilationResult first = circuit.compile();
        CompilationResult second = circuit.compile();

        // Net IDs should be identical
        assertEquals(first.sourceMap().netByPort(), second.sourceMap().netByPort());
    }

    @Test
    void deterministischeComponentIds() {
        CircuitBuilder circuit = new CircuitBuilder();
        circuit.add("source.one", "VCC");
        circuit.add("logic.and", "AND1");
        circuit.add("source.zero", "GND");

        CompilationResult first = circuit.compile();
        CompilationResult second = circuit.compile();

        // Component IDs should be identical
        assertEquals(first.sourceMap().componentIdByUuid(), second.sourceMap().componentIdByUuid());
    }

    @Test
    void netWithoutDriver_reportsInfo() {
        CircuitBuilder circuit = new CircuitBuilder();
        circuit.add("output.led", "LED1");

        CompilationResult result = circuit.compile();

        assertTrue(result.issues().stream()
                        .anyMatch(issue -> issue.severity() == ValidationIssue.Severity.INFO
                                && issue.message().contains("no driver")));
    }

    @Test
    void netWithOneDriver() {
        CircuitBuilder circuit = new CircuitBuilder();
        circuit.add("source.one", "VCC");
        circuit.add("output.led", "LED1");
        circuit.wire("VCC", "OUT", "LED1", "IN");

        CompilationResult result = circuit.compile();
        int net = result.sourceMap().netOf(circuit.port("VCC", "OUT")).orElseThrow();

        assertEquals(1, result.circuit().net(net).driverCount());
    }

    @Test
    void netWithMultipleDrivers_warns() {
        CircuitBuilder circuit = new CircuitBuilder();
        circuit.add("source.one", "VCC");
        circuit.add("source.zero", "GND");
        circuit.add("output.probe", "P");
        circuit.wire("VCC", "OUT", "GND", "OUT").wire("VCC", "OUT", "P", "IN");

        CompilationResult result = circuit.compile();

        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).message().contains("Two outputs"));
    }

    @Test
    void twoContradictoryDrivers_producesConflict() {
        CircuitBuilder circuit = new CircuitBuilder();
        circuit.add("source.one", "VCC");
        circuit.add("source.zero", "GND");
        circuit.add("output.probe", "P");
        circuit.wire("VCC", "OUT", "GND", "OUT").wire("VCC", "OUT", "P", "IN");

        CompilationResult result = circuit.compile();
        int net = result.sourceMap().netOf(circuit.port("VCC", "OUT")).orElseThrow();

        Simulation simulation = new Simulation(result.circuit());
        assertEquals(LogicVector.UNKNOWN, simulation.readNet(net));
        assertTrue(simulation.hasDriverConflict(net));
    }

    @Test
    void transitiveConnectionsFormSingleNet() {
        CircuitBuilder circuit = new CircuitBuilder();
        circuit.add("source.one", "VCC");
        circuit.add("output.probe", "P1");
        circuit.add("output.probe", "P2");
        circuit.add("output.probe", "P3");
        // Chain: VCC -> P1 -> P2 -> P3
        circuit.wire("VCC", "OUT", "P1", "IN")
                .wire("P1", "IN", "P2", "IN")
                .wire("P2", "IN", "P3", "IN");

        CompilationResult result = circuit.compile();

        // All should be in one net
        assertEquals(1, result.circuit().netCount());
        assertEquals(4, result.sourceMap().portsOf(0).size());
    }

    @Test
    void connectionOrderDoesNotChangeSemantics() {
        CircuitBuilder circuit1 = new CircuitBuilder();
        circuit1.add("source.one", "VCC");
        circuit1.add("output.probe", "P1");
        circuit1.add("output.probe", "P2");
        circuit1.wire("VCC", "OUT", "P1", "IN").wire("P1", "IN", "P2", "IN");

        CircuitBuilder circuit2 = new CircuitBuilder();
        circuit2.add("source.one", "VCC");
        circuit2.add("output.probe", "P1");
        circuit2.add("output.probe", "P2");
        circuit2.wire("P1", "IN", "P2", "IN").wire("VCC", "OUT", "P1", "IN");

        CompilationResult result1 = circuit1.compile();
        CompilationResult result2 = circuit2.compile();

        // Same number of nets
        assertEquals(result1.circuit().netCount(), result2.circuit().netCount());
        // Same net connectivity
        assertEquals(result1.sourceMap().portsOf(0).size(), result2.sourceMap().portsOf(0).size());
    }

    @Test
    void unknownComponentDefinition_throwsError() {
        CircuitBuilder circuit = new CircuitBuilder();
        circuit.document().addComponent(ComponentInstance.create("logic.does.not.exist",
                new CircuitPoint(0, 0), ParameterValues.empty()));

        CircuitCompileException failure = assertThrows(CircuitCompileException.class, circuit::compile);
        assertEquals(1, failure.errors().size());
        assertTrue(failure.errors().get(0).message().contains("Unknown component"));
    }

    @Test
    void unknownPortInConnection_isReportedAndIgnored() {
        CircuitBuilder circuit = new CircuitBuilder();
        ComponentInstance gate = circuit.add("logic.and", "AND1",
                ParameterValues.empty().with(LibraryParameters.INPUT_COUNT, 4));
        // Wire to port IN3
        circuit.wire("AND1", "IN3", "AND1", "IN0");
        // Now reconfigure to only 2 inputs
        circuit.replace(gate.withParameters(ParameterValues.empty().with(LibraryParameters.INPUT_COUNT, 2)));

        // This should produce a warning about the port no longer existing
        CompilationResult result = circuit.compile();
        assertTrue(result.warnings().stream()
                        .anyMatch(issue -> issue.message().contains("no longer exists")));
    }

    private static ComponentRegistry createWidthTestRegistry() {
        ComponentRegistry registry = new ComponentRegistry();

        // 8-bit output component
        ComponentDefinition output8Def = new ComponentDefinition() {
            @Override
            public String id() {
                return "test.output8";
            }

            @Override
            public String displayName() {
                return "8-bit Output";
            }

            @Override
            public ComponentCategory category() {
                return ComponentCategory.SOURCES;
            }

            @Override
            public String description() {
                return "Test 8-bit output";
            }

            @Override
            public List<ParameterSpec<?>> parameters() {
                return List.of();
            }

            @Override
            public List<PortSpec> ports(ParameterValues values) {
                return List.of(new PortSpec("OUT", PortDirection.OUTPUT, BitWidth.of(8),
                        new CircuitPoint(0, 0), PortSide.RIGHT, "8-bit output"));
            }

            @Override
            public CircuitSize bodySize(ParameterValues values) {
                return new CircuitSize(40, 20);
            }
        };

        ComponentBehavior output8Behavior = context -> {
            context.driveOutput(0, LogicVector.repeat(LogicState.ONE, 8));
        };
        registry.register(ComponentType.of(output8Def, output8Behavior));

        // 8-bit input component
        ComponentDefinition input8Def = new ComponentDefinition() {
            @Override
            public String id() {
                return "test.input8";
            }

            @Override
            public String displayName() {
                return "8-bit Input";
            }

            @Override
            public ComponentCategory category() {
                return ComponentCategory.OUTPUTS;
            }

            @Override
            public String description() {
                return "Test 8-bit input";
            }

            @Override
            public List<ParameterSpec<?>> parameters() {
                return List.of();
            }

            @Override
            public List<PortSpec> ports(ParameterValues values) {
                return List.of(new PortSpec("IN", PortDirection.INPUT, BitWidth.of(8),
                        new CircuitPoint(0, 0), PortSide.LEFT, "8-bit input"));
            }

            @Override
            public CircuitSize bodySize(ParameterValues values) {
                return new CircuitSize(40, 20);
            }
        };

        ComponentBehavior input8Behavior = context -> {
            // Sink, no outputs
        };
        registry.register(ComponentType.of(input8Def, input8Behavior));

        // 1-bit input component
        ComponentDefinition input1Def = new ComponentDefinition() {
            @Override
            public String id() {
                return "test.input1";
            }

            @Override
            public String displayName() {
                return "1-bit Input";
            }

            @Override
            public ComponentCategory category() {
                return ComponentCategory.OUTPUTS;
            }

            @Override
            public String description() {
                return "Test 1-bit input";
            }

            @Override
            public List<ParameterSpec<?>> parameters() {
                return List.of();
            }

            @Override
            public List<PortSpec> ports(ParameterValues values) {
                return List.of(new PortSpec("IN", PortDirection.INPUT, BitWidth.ONE,
                        new CircuitPoint(0, 0), PortSide.LEFT, "1-bit input"));
            }

            @Override
            public CircuitSize bodySize(ParameterValues values) {
                return new CircuitSize(40, 20);
            }
        };

        ComponentBehavior input1Behavior = context -> {
            // Sink, no outputs
        };
        registry.register(ComponentType.of(input1Def, input1Behavior));

        return registry;
    }
}
