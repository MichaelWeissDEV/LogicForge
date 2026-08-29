package dev.logicforge.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.compiler.CircuitCompiler;
import dev.logicforge.compiler.CompilationResult;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.Simulation;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The physical-IC examples don't just compile — wiring a switch to a real 74HC00/74HC86/
 * 74HC08/74HC283 physical pin has to actually behave like the part it names. These tests
 * simulate the shipped examples end to end through their real chip pins, the same way
 * {@code ExampleProjectsTest} exercises the purely behavioral examples.
 */
class PhysicalIcExamplesTest {

    private static final Path EXAMPLES =
            Path.of(System.getProperty("logicforge.examples", "../examples")).resolve("physical-ic");

    @Test
    void nand74hc00BehavesLikeAQuadNandGate() {
        CircuitDocument circuit = load("74hc-nand");
        CompilationResult compiled = compile(circuit);
        Simulation simulation = new Simulation(compiled.circuit());

        int a = compiled.componentByLabel("A").orElseThrow();
        int b = compiled.componentByLabel("B").orElseThrow();
        int y = compiled.sourceMap().netOf(port(circuit, "Y", "IN")).orElseThrow();

        assertEquals(LogicVector.ONE, valueAfter(simulation, a, LogicState.ZERO, b, LogicState.ZERO, y));
        assertEquals(LogicVector.ONE, valueAfter(simulation, a, LogicState.ONE, b, LogicState.ZERO, y));
        assertEquals(LogicVector.ONE, valueAfter(simulation, a, LogicState.ZERO, b, LogicState.ONE, y));
        assertEquals(LogicVector.ZERO, valueAfter(simulation, a, LogicState.ONE, b, LogicState.ONE, y));
    }

    @Test
    void halfAdder74hcMatchesTheBehavioralHalfAdder() {
        CircuitDocument circuit = load("74hc-half-adder");
        CompilationResult compiled = compile(circuit);
        Simulation simulation = new Simulation(compiled.circuit());

        int a = compiled.componentByLabel("A").orElseThrow();
        int b = compiled.componentByLabel("B").orElseThrow();
        int sum = compiled.sourceMap().netOf(port(circuit, "SUM", "IN")).orElseThrow();
        int carry = compiled.sourceMap().netOf(port(circuit, "CARRY", "IN")).orElseThrow();

        record Case(LogicState a, LogicState b, LogicState sum, LogicState carry) {
        }
        for (Case testCase : new Case[] {
                new Case(LogicState.ZERO, LogicState.ZERO, LogicState.ZERO, LogicState.ZERO),
                new Case(LogicState.ONE, LogicState.ZERO, LogicState.ONE, LogicState.ZERO),
                new Case(LogicState.ZERO, LogicState.ONE, LogicState.ONE, LogicState.ZERO),
                new Case(LogicState.ONE, LogicState.ONE, LogicState.ZERO, LogicState.ONE)}) {
            simulation.setInput(a, testCase.a());
            simulation.setInput(b, testCase.b());
            assertEquals(LogicVector.single(testCase.sum()), simulation.readNet(sum),
                    testCase.a() + " xor " + testCase.b());
            assertEquals(LogicVector.single(testCase.carry()), simulation.readNet(carry),
                    testCase.a() + " and " + testCase.b());
        }
    }

    @Test
    void adder74hc283AddsFourBitValuesThroughItsPhysicalPins() {
        CircuitDocument circuit = load("74hc283-adder");
        CompilationResult compiled = compile(circuit);
        Simulation simulation = new Simulation(compiled.circuit());

        int[] aInputs = new int[4];
        int[] bInputs = new int[4];
        int[] sumNets = new int[4];
        for (int bit = 0; bit < 4; bit++) {
            aInputs[bit] = compiled.componentByLabel("A" + bit).orElseThrow();
            bInputs[bit] = compiled.componentByLabel("B" + bit).orElseThrow();
            sumNets[bit] = compiled.sourceMap().netOf(port(circuit, "SUM" + bit, "IN")).orElseThrow();
        }
        int cin = compiled.componentByLabel("CIN").orElseThrow();
        int cout = compiled.sourceMap().netOf(port(circuit, "COUT", "IN")).orElseThrow();

        assertAddition(simulation, aInputs, bInputs, sumNets, cin, cout, 0, 0, false, 0, false);
        assertAddition(simulation, aInputs, bInputs, sumNets, cin, cout, 5, 3, false, 8, false);
        assertAddition(simulation, aInputs, bInputs, sumNets, cin, cout, 15, 1, false, 0, true);
        assertAddition(simulation, aInputs, bInputs, sumNets, cin, cout, 7, 8, true, 0, true);
    }

    private static void assertAddition(Simulation simulation, int[] aInputs, int[] bInputs, int[] sumNets,
                                       int cin, int cout, int aValue, int bValue, boolean carryIn,
                                       int expectedSum, boolean expectedCarryOut) {
        for (int bit = 0; bit < 4; bit++) {
            simulation.setInput(aInputs[bit], LogicState.of(((aValue >> bit) & 1) != 0));
            simulation.setInput(bInputs[bit], LogicState.of(((bValue >> bit) & 1) != 0));
        }
        simulation.setInput(cin, LogicState.of(carryIn));

        int actualSum = 0;
        for (int bit = 0; bit < 4; bit++) {
            if (simulation.readNet(sumNets[bit]).getBit(0) == LogicState.ONE) {
                actualSum |= 1 << bit;
            }
        }
        assertEquals(expectedSum, actualSum, aValue + " + " + bValue + " + " + (carryIn ? 1 : 0));
        assertEquals(expectedCarryOut, simulation.readNet(cout).getBit(0) == LogicState.ONE,
                aValue + " + " + bValue + " + " + (carryIn ? 1 : 0) + " carry out");
    }

    private static LogicVector valueAfter(Simulation simulation, int a, LogicState aValue, int b,
                                          LogicState bValue, int net) {
        simulation.setInput(a, aValue);
        simulation.setInput(b, bValue);
        return simulation.readNet(net);
    }

    private static CircuitDocument load(String name) {
        return ProjectFormat.load(EXAMPLES.resolve(name + "." + ProjectFormat.EXTENSION)).mainCircuit();
    }

    private static CompilationResult compile(CircuitDocument circuit) {
        CompilationResult compiled = new CircuitCompiler(ComponentRegistry.standard()).compile(circuit);
        assertTrue(compiled.issues().stream().noneMatch(issue -> issue.isError()));
        return compiled;
    }

    private static PortReference port(CircuitDocument circuit, String label, String portName) {
        ComponentInstance instance = circuit.components().stream()
                .filter(component -> component.label().equals(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No component labelled " + label));
        return new PortReference(instance.id(), portName);
    }
}
