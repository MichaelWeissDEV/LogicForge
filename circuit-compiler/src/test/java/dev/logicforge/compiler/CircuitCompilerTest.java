package dev.logicforge.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.library.LibraryParameters;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.CompiledCircuit;
import dev.logicforge.simulation.Simulation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CircuitCompilerTest {

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
}
