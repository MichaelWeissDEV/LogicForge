package dev.logicforge.structures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class StructuralAdderTest {

    @Test
    void registryFindsCanonicalGateImplementations() {
        StructuralImplementationRegistry registry = StructuralImplementationRegistry.standard();
        assertTrue(registry.find("arithmetic.full_adder", ParameterValues.empty(),
                ImplementationLevel.GATE).isPresent());
        assertTrue(registry.find("arithmetic.adder",
                ParameterValues.empty().with(LibraryParameters.WIDTH, 8),
                ImplementationLevel.GATE).isPresent());
        assertTrue(registry.allFor("arithmetic.full_adder").getFirst().tags().contains("hierarchy"));
    }

    @Test
    void fullAdderUsesTwoRealHalfAdderChildrenAndNoBehavioralAdderPrimitive() {
        CircuitProject project = StructuralCircuitFactory.fullAdderProject();
        CircuitDocument full = project.circuit(StructuralCircuitFactory.FULL_ADDER).orElseThrow();
        long halfAdders = full.components().stream()
                .filter(component -> component.definitionId().equals(
                        SubcircuitSupport.definitionId(StructuralCircuitFactory.HALF_ADDER)))
                .count();
        assertEquals(2, halfAdders);
        assertFalse(project.circuits().stream().flatMap(circuit -> circuit.components().stream())
                .anyMatch(component -> component.definitionId().equals("arithmetic.full_adder")));

        for (int a = 0; a <= 1; a++) {
            for (int b = 0; b <= 1; b++) {
                for (int cin = 0; cin <= 1; cin++) {
                    Outputs outputs = evaluateScalar(project, StructuralCircuitFactory.FULL_ADDER,
                            a == 0 ? "source.zero" : "source.one",
                            b == 0 ? "source.zero" : "source.one",
                            cin == 0 ? "source.zero" : "source.one");
                    int expected = a + b + cin;
                    assertEquals(LogicState.of((expected & 1) != 0), outputs.sum().singleBit());
                    assertEquals(LogicState.of(expected > 1), outputs.carry().singleBit());
                }
            }
        }
    }

    @Test
    void fullAdderPropagatesUnknownsThroughItsActualGateHierarchy() {
        CircuitProject project = StructuralCircuitFactory.fullAdderProject();
        Outputs outputs = evaluateScalar(project, StructuralCircuitFactory.FULL_ADDER,
                "source.unknown", "source.zero", "source.zero");
        assertEquals(LogicState.UNKNOWN, outputs.sum().singleBit());
        assertEquals(LogicState.ZERO, outputs.carry().singleBit(),
                "controlling zeroes in the two AND gates keep carry defined");
    }

    @Test
    void rippleAdder8MatchesUnsignedAdditionAcrossRepresentativeValues() {
        CircuitProject project = StructuralCircuitFactory.rippleAdderProject(8);
        int[][] cases = {
                {0x00, 0x00, 0}, {0x00, 0x00, 1}, {0x12, 0x34, 0},
                {0xff, 0x01, 0}, {0x80, 0x80, 0}, {0xa5, 0x5a, 1},
        };
        for (int[] testCase : cases) {
            Outputs outputs = evaluateBus(project, StructuralCircuitFactory.rippleAdderName(8),
                    testCase[0], testCase[1], testCase[2]);
            int expected = testCase[0] + testCase[1] + testCase[2];
            assertEquals(LogicVector.fromUnsignedLong(expected & 0xff, 8), outputs.sum());
            assertEquals(LogicState.of(expected > 0xff), outputs.carry().singleBit());
        }
    }

    private static Outputs evaluateScalar(CircuitProject sourceProject, String circuitName,
                                          String aType, String bType, String cinType) {
        CircuitProject project = copyChildren(sourceProject);
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", "test harness"));
        ComponentInstance a = add(main, aType, "A", -200, -80, ParameterValues.empty());
        ComponentInstance b = add(main, bType, "B", -200, 0, ParameterValues.empty());
        ComponentInstance cin = add(main, cinType, "CIN", -200, 80, ParameterValues.empty());
        ComponentInstance instance = sub(main, circuitName, "DUT", 0, 0);
        ComponentInstance sum = add(main, "output.probe", "SUM", 200, -40, ParameterValues.empty());
        ComponentInstance carry = add(main, "output.probe", "CARRY", 200, 60, ParameterValues.empty());
        wire(main, a, "OUT", instance, "A");
        wire(main, b, "OUT", instance, "B");
        wire(main, cin, "OUT", instance, "CIN");
        wire(main, instance, "SUM", sum, "IN");
        wire(main, instance, "COUT", carry, "IN");
        project.putCircuit(main);
        return compileOutputs(project, main, sum, carry);
    }

    private static Outputs evaluateBus(CircuitProject sourceProject, String circuitName,
                                       int aValue, int bValue, int cinValue) {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitProject project = copyChildren(sourceProject);
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", "test harness"));
        ComponentInstance a = add(main, "routing.bus_constant", "A", -200, -80,
                busConstant(registry, 8, aValue));
        ComponentInstance b = add(main, "routing.bus_constant", "B", -200, 0,
                busConstant(registry, 8, bValue));
        ComponentInstance cin = add(main, cinValue == 0 ? "source.zero" : "source.one",
                "CIN", -200, 80, ParameterValues.empty());
        ComponentInstance instance = sub(main, circuitName, "DUT", 0, 0);
        ComponentInstance sum = add(main, "routing.bus_probe", "SUM", 200, -40,
                registry.require("routing.bus_probe").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8));
        ComponentInstance carry = add(main, "output.probe", "CARRY", 200, 60,
                ParameterValues.empty());
        wire(main, a, "OUT", instance, "A");
        wire(main, b, "OUT", instance, "B");
        wire(main, cin, "OUT", instance, "CIN");
        wire(main, instance, "SUM", sum, "IN");
        wire(main, instance, "COUT", carry, "IN");
        project.putCircuit(main);
        return compileOutputs(project, main, sum, carry);
    }

    private static Outputs compileOutputs(CircuitProject project, CircuitDocument main,
                                          ComponentInstance sum, ComponentInstance carry) {
        var compiled = new CircuitCompiler(ComponentRegistry.standard()).compile(project, "main");
        Simulation simulation = new Simulation(compiled.circuit());
        int sumNet = compiled.sourceMap().netOf(new PortReference(sum.id(), "IN")).orElseThrow();
        int carryNet = compiled.sourceMap().netOf(new PortReference(carry.id(), "IN")).orElseThrow();
        return new Outputs(simulation.readNet(sumNet), simulation.readNet(carryNet));
    }

    private static CircuitProject copyChildren(CircuitProject source) {
        CircuitProject copy = new CircuitProject(source.name());
        source.circuits().stream()
                .filter(circuit -> !circuit.metadata().name().equals("main"))
                .forEach(copy::putCircuit);
        return copy;
    }

    private static ParameterValues busConstant(ComponentRegistry registry, int width, int value) {
        return registry.require("routing.bus_constant").definition().defaultParameters()
                .with(LibraryParameters.WIDTH, width)
                .with(LibraryParameters.BUS_CONSTANT_VALUE, Integer.toHexString(value));
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

    private record Outputs(LogicVector sum, LogicVector carry) {
    }
}
