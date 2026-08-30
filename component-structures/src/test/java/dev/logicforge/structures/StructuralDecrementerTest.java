package dev.logicforge.structures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitMetadata;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.SubcircuitSupport;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.compiler.CircuitCompiler;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.LibraryParameters;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.Simulation;
import org.junit.jupiter.api.Test;

/**
 * {@link StructuralCircuitFactory#decrementer16Project} implements {@code A - 1} as {@code A
 * + 0xFFFF} through the existing 16-bit ripple-adder hierarchy. Before registering it against
 * the behavioral {@code arithmetic.decrementer} at width 16, this proves the two agree by
 * driving both from the same value in one compiled circuit.
 */
class StructuralDecrementerTest {

    @Test
    void matchesBehavioralDecrementAcrossRepresentativeValuesIncludingWraparound() {
        int[] values = {0x0000, 0x0001, 0x8000, 0xFFFF, 0x1234, 0xBEEF, 0x00FF, 0xFF00};
        for (int value : values) {
            Result result = evaluate(value);
            assertEquals(result.behavioral(), result.structural(), "A=0x" + Integer.toHexString(value));
            int expected = (value - 1) & 0xFFFF;
            assertEquals(LogicVector.fromUnsignedLong(expected, 16), result.structural());
        }
    }

    @Test
    void isAvailableThroughTheStandardRegistryAtGateLevel() {
        StructuralImplementationRegistry registry = StructuralImplementationRegistry.standard();
        assertTrue(registry.find("arithmetic.decrementer",
                ParameterValues.empty().with(LibraryParameters.WIDTH, 16), ImplementationLevel.GATE)
                .isPresent());
        assertTrue(registry.find("arithmetic.decrementer",
                ParameterValues.empty().with(LibraryParameters.WIDTH, 8), ImplementationLevel.GATE)
                .isEmpty(), "only width 16 has a proven structural implementation");
    }

    private record Result(LogicVector behavioral, LogicVector structural) {
    }

    private static Result evaluate(int value) {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitProject project = new CircuitProject("struct-decrementer-equivalence");
        for (CircuitDocument child : StructuralCircuitFactory.decrementer16Project().circuits()) {
            if (!dev.logicforge.circuit.document.CircuitProject.MAIN_CIRCUIT.equals(child.metadata().name())) {
                project.putCircuit(child);
            }
        }
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", "decrementer equivalence harness"));

        ComponentInstance aA = add(main, "routing.bus_constant", "A_A", -260, -60, busConstant(value));
        ComponentInstance behavioral = add(main, "arithmetic.decrementer", "BEHAVIORAL", 0, -60,
                registry.require("arithmetic.decrementer").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 16));
        ComponentInstance behavioralOut = add(main, "routing.bus_probe", "BEHAVIORAL_OUT", 260, -60,
                busProbeParams(registry));

        ComponentInstance aB = add(main, "routing.bus_constant", "A_B", -260, 60, busConstant(value));
        ComponentInstance structural = sub(main, StructuralCircuitFactory.DECREMENTER16, "STRUCTURAL", 0, 60);
        ComponentInstance structuralOut = add(main, "routing.bus_probe", "STRUCTURAL_OUT", 260, 60,
                busProbeParams(registry));

        wire(main, aA, "OUT", behavioral, "A");
        wire(main, behavioral, "OUT", behavioralOut, "IN");
        wire(main, aB, "OUT", structural, "A");
        wire(main, structural, "OUT", structuralOut, "IN");

        project.putCircuit(main);
        var compiled = new CircuitCompiler(registry).compile(project, "main");
        Simulation simulation = new Simulation(compiled.circuit());
        int behavioralNet = compiled.sourceMap().netOf(PortEndpoint.whole(
                new PortReference(behavioralOut.id(), "IN"))).orElseThrow();
        int structuralNet = compiled.sourceMap().netOf(PortEndpoint.whole(
                new PortReference(structuralOut.id(), "IN"))).orElseThrow();
        return new Result(simulation.readNet(behavioralNet), simulation.readNet(structuralNet));
    }

    private static ParameterValues busProbeParams(ComponentRegistry registry) {
        return registry.require("routing.bus_probe").definition().defaultParameters()
                .with(LibraryParameters.WIDTH, 16);
    }

    private static ParameterValues busConstant(int value) {
        return ComponentRegistry.standard().require("routing.bus_constant").definition().defaultParameters()
                .with(LibraryParameters.WIDTH, 16)
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
}
