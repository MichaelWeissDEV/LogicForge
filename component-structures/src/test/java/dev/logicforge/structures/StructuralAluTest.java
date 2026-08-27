package dev.logicforge.structures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitMetadata;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.SubcircuitSupport;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.compiler.CircuitCompiler;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.LibraryParameters;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.Simulation;
import org.junit.jupiter.api.Test;

class StructuralAluTest {

    @Test
    void alu8ImplementsEveryOperationAndFlagsThroughStructuralHierarchy() {
        CircuitProject source = StructuralCircuitFactory.alu8Project();
        assertFalse(source.circuits().stream().flatMap(circuit -> circuit.components().stream())
                .anyMatch(component -> component.definitionId().equals("arithmetic.alu")));
        CircuitDocument alu = source.circuit(StructuralCircuitFactory.ALU8).orElseThrow();
        assertEquals(1, alu.components().stream().filter(component -> component.definitionId().equals(
                SubcircuitSupport.definitionId(StructuralCircuitFactory.rippleAdderName(8)))).count());

        Harness harness = compileHarness(source);
        assertCase(harness, 0, 0x7f, 0x01, 0x80, 0, 1, 0, 1);
        assertCase(harness, 0, 0xff, 0x01, 0x00, 1, 0, 1, 0);
        assertCase(harness, 1, 0x00, 0x01, 0xff, 0, 1, 0, 0);
        assertCase(harness, 1, 0x80, 0x01, 0x7f, 0, 0, 1, 1);
        assertCase(harness, 2, 0xa5, 0x0f, 0x05, 0, 0, 0, 0);
        assertCase(harness, 3, 0xa0, 0x0f, 0xaf, 0, 1, 0, 0);
        assertCase(harness, 4, 0xff, 0x0f, 0xf0, 0, 1, 0, 0);
        assertCase(harness, 5, 0x0f, 0x00, 0xf0, 0, 1, 0, 0);
        assertCase(harness, 6, 0x81, 0x00, 0x02, 0, 0, 1, 0);
        assertCase(harness, 7, 0x01, 0x00, 0x00, 1, 0, 1, 0);
    }

    @Test
    void structuralAluMatchesFastAluAcrossDeterministicExhaustiveSlicesAndUnknowns() {
        Harness harness = compileHarness(StructuralCircuitFactory.alu8Project());
        int[] bValues = {0x00, 0x01, 0x02, 0x7f, 0x80, 0xfe, 0xff};
        for (int op = 0; op <= 4; op++) {
            for (int a = 0; a <= 0xff; a++) {
                for (int b : bValues) {
                    assertFastEquivalent(harness, op, a, b);
                }
            }
        }
        for (int op = 5; op <= 7; op++) {
            for (int a = 0; a <= 0xff; a++) {
                assertFastEquivalent(harness, op, a, 0);
            }
        }
        assertStructuralFourState(harness, 0, LogicVector.of("10XZ0001"),
                LogicVector.fromUnsignedLong(1, 8), LogicVector.of("10XX0010"),
                LogicState.ZERO, LogicState.ONE, LogicState.ZERO, LogicState.ZERO);
        assertStructuralFourState(harness, 4, LogicVector.of("ZZZZZZZZ"),
                LogicVector.fromUnsignedLong(0xaa, 8), LogicVector.of("XXXXXXXX"),
                LogicState.UNKNOWN, LogicState.UNKNOWN, LogicState.ZERO, LogicState.ZERO);
    }

    private static Harness compileHarness(CircuitProject source) {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitProject project = new CircuitProject("alu-test");
        source.circuits().stream().filter(circuit -> !circuit.metadata().name().equals("main"))
                .forEach(project::putCircuit);
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", "ALU harness"));
        ComponentInstance one = add(main, "source.one", "ONE", -300, -220, ParameterValues.empty());
        ComponentInstance a = busInput(main, registry, "A", 8, -300, -140, one);
        ComponentInstance b = busInput(main, registry, "B", 8, -300, -60, one);
        ComponentInstance op = busInput(main, registry, "OP", 3, -300, 20, one);
        ComponentInstance fastOp = busInput(main, registry, "FAST_OP", 4, -300, 100, one);
        ComponentInstance cin = add(main, "source.toggle", "CIN", -300, 180,
                ParameterValues.empty());
        ComponentInstance dut = sub(main, StructuralCircuitFactory.ALU8, "ALU8", 0, 0);
        ComponentInstance fast = add(main, "arithmetic.alu", "FAST_ALU", 0, 200,
                registry.require("arithmetic.alu").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8));
        ComponentInstance result = add(main, "routing.bus_probe", "RESULT", 300, -100,
                registry.require("routing.bus_probe").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8));
        ComponentInstance z = add(main, "output.probe", "Z", 300, -40, ParameterValues.empty());
        ComponentInstance n = add(main, "output.probe", "N", 300, 0, ParameterValues.empty());
        ComponentInstance c = add(main, "output.probe", "C", 300, 40, ParameterValues.empty());
        ComponentInstance v = add(main, "output.probe", "V", 300, 80, ParameterValues.empty());
        ComponentInstance fastResult = add(main, "routing.bus_probe", "FAST_RESULT", 300, 180,
                registry.require("routing.bus_probe").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8));
        ComponentInstance fastZ = add(main, "output.probe", "FAST_Z", 300, 240,
                ParameterValues.empty());
        ComponentInstance fastN = add(main, "output.probe", "FAST_N", 300, 280,
                ParameterValues.empty());
        ComponentInstance fastC = add(main, "output.probe", "FAST_C", 300, 320,
                ParameterValues.empty());
        ComponentInstance fastV = add(main, "output.probe", "FAST_V", 300, 360,
                ParameterValues.empty());
        wire(main, a, "DATA", dut, "A");
        wire(main, b, "DATA", dut, "B");
        wire(main, op, "DATA", dut, "OP");
        wire(main, dut, "RESULT", result, "IN");
        wire(main, dut, "Z", z, "IN");
        wire(main, dut, "N", n, "IN");
        wire(main, dut, "C", c, "IN");
        wire(main, dut, "V", v, "IN");
        wire(main, a, "DATA", fast, "A");
        wire(main, b, "DATA", fast, "B");
        wire(main, fastOp, "DATA", fast, "OP");
        wire(main, cin, "OUT", fast, "CIN");
        wire(main, fast, "RESULT", fastResult, "IN");
        wire(main, fast, "ZERO", fastZ, "IN");
        wire(main, fast, "NEGATIVE", fastN, "IN");
        wire(main, fast, "CARRY", fastC, "IN");
        wire(main, fast, "OVERFLOW", fastV, "IN");
        project.putCircuit(main);
        var compiled = new CircuitCompiler(registry).compile(project, "main");
        Simulation simulation = new Simulation(compiled.circuit());
        return new Harness(simulation,
                compiled.sourceMap().componentId(a.id()).orElseThrow(),
                compiled.sourceMap().componentId(b.id()).orElseThrow(),
                compiled.sourceMap().componentId(op.id()).orElseThrow(),
                compiled.sourceMap().componentId(fastOp.id()).orElseThrow(),
                compiled.sourceMap().componentId(cin.id()).orElseThrow(),
                net(compiled, result), net(compiled, z), net(compiled, n),
                net(compiled, c), net(compiled, v), net(compiled, fastResult),
                net(compiled, fastZ), net(compiled, fastN), net(compiled, fastC),
                net(compiled, fastV));
    }

    private static ComponentInstance busInput(CircuitDocument main, ComponentRegistry registry,
                                              String label, int width, double x, double y,
                                              ComponentInstance one) {
        ComponentInstance input = add(main, "system.input_port", label, x, y,
                registry.require("system.input_port").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, width));
        wire(main, one, "OUT", input, "SELECT");
        wire(main, one, "OUT", input, "READ");
        return input;
    }

    private static int net(dev.logicforge.compiler.CompilationResult compiled,
                           ComponentInstance probe) {
        return compiled.sourceMap().netOf(new PortReference(probe.id(), "IN")).orElseThrow();
    }

    private static void assertCase(Harness harness, int op, int a, int b, int result,
                                   int z, int n, int c, int v) {
        harness.simulation().setInput(harness.aId(), LogicVector.fromUnsignedLong(a, 8));
        harness.simulation().setInput(harness.bId(), LogicVector.fromUnsignedLong(b, 8));
        harness.simulation().setInput(harness.opId(), LogicVector.fromUnsignedLong(op, 3));
        assertEquals(LogicVector.fromUnsignedLong(result, 8),
                harness.simulation().readNet(harness.resultNet()), "operation " + op);
        assertEquals(LogicState.of(z != 0), harness.simulation().readNet(harness.zNet()).singleBit());
        assertEquals(LogicState.of(n != 0), harness.simulation().readNet(harness.nNet()).singleBit());
        assertEquals(LogicState.of(c != 0), harness.simulation().readNet(harness.cNet()).singleBit());
        assertEquals(LogicState.of(v != 0), harness.simulation().readNet(harness.vNet()).singleBit());
    }

    private static void assertFastEquivalent(Harness harness, int op, int a, int b) {
        assertFastEquivalent(harness, op, LogicVector.fromUnsignedLong(a, 8),
                LogicVector.fromUnsignedLong(b, 8));
    }

    private static void assertFastEquivalent(Harness harness, int op, LogicVector a,
                                             LogicVector b) {
        harness.simulation().setInput(harness.aId(), a);
        harness.simulation().setInput(harness.bId(), b);
        harness.simulation().setInput(harness.opId(), LogicVector.fromUnsignedLong(op, 3));
        harness.simulation().setInput(harness.fastOpId(), LogicVector.fromUnsignedLong(op, 4));
        harness.simulation().setInput(harness.cinId(), LogicState.of(op == 1));
        String label = "op=" + op + " A=" + a + " B=" + b;
        assertEquals(harness.simulation().readNet(harness.fastResultNet()),
                harness.simulation().readNet(harness.resultNet()), label + " result");
        assertEquals(harness.simulation().readNet(harness.fastZNet()),
                harness.simulation().readNet(harness.zNet()), label + " Z");
        assertEquals(harness.simulation().readNet(harness.fastNNet()),
                harness.simulation().readNet(harness.nNet()), label + " N");
        assertEquals(harness.simulation().readNet(harness.fastCNet()),
                harness.simulation().readNet(harness.cNet()), label + " C");
        assertEquals(harness.simulation().readNet(harness.fastVNet()),
                harness.simulation().readNet(harness.vNet()), label + " V");
    }

    private static void assertStructuralFourState(Harness harness, int op, LogicVector a,
                                                  LogicVector b, LogicVector result,
                                                  LogicState z, LogicState n,
                                                  LogicState c, LogicState v) {
        harness.simulation().setInput(harness.aId(), a);
        harness.simulation().setInput(harness.bId(), b);
        harness.simulation().setInput(harness.opId(), LogicVector.fromUnsignedLong(op, 3));
        assertEquals(result, harness.simulation().readNet(harness.resultNet()));
        assertEquals(z, harness.simulation().readNet(harness.zNet()).singleBit());
        assertEquals(n, harness.simulation().readNet(harness.nNet()).singleBit());
        assertEquals(c, harness.simulation().readNet(harness.cNet()).singleBit());
        assertEquals(v, harness.simulation().readNet(harness.vNet()).singleBit());
    }

    private static ComponentInstance add(CircuitDocument document, String type, String label,
                                         double x, double y, ParameterValues parameters) {
        ComponentInstance component = ComponentInstance.create(type, new CircuitPoint(x, y), parameters)
                .withLabel(label);
        document.addComponent(component);
        return component;
    }

    private static ComponentInstance sub(CircuitDocument document, String child, String label,
                                         double x, double y) {
        ComponentInstance component = SubcircuitSupport.instantiate(child, new CircuitPoint(x, y))
                .withLabel(label);
        document.addComponent(component);
        return component;
    }

    private static void wire(CircuitDocument document, ComponentInstance from, String fromPort,
                             ComponentInstance to, String toPort) {
        document.addConnection(Connection.create(new PortReference(from.id(), fromPort),
                new PortReference(to.id(), toPort)));
    }

    private record Harness(Simulation simulation, int aId, int bId, int opId, int fastOpId,
                           int cinId, int resultNet, int zNet, int nNet, int cNet, int vNet,
                           int fastResultNet, int fastZNet, int fastNNet, int fastCNet,
                           int fastVNet) {
    }
}
