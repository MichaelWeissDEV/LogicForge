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
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.Simulation;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link StructuralCircuitFactory#structuralBusMuxProject} builds a width-N mux entirely out
 * of {@link StructuralCircuitFactory#MUX2} gate cells. Before registering it against {@code
 * routing.mux2} in {@link StructuralImplementationRegistry} for widths 3/4/8/16, this proves
 * it actually reproduces that behavioral component's output — not just for fully defined
 * values, but for X and Z too — by driving the real behavioral {@code routing.mux2} instance
 * and the structural one from the exact same sources in one compiled circuit and comparing
 * their outputs directly, rather than trusting a hand-written truth table.
 */
class StructuralMuxTest {

    @Test
    void matchesTheBehavioralTwoToOneMuxAcrossRepresentativeValuesAndWidths() {
        for (int width : List.of(3, 4, 8, 16)) {
            int mask = width == 16 ? 0xFFFF : (1 << width) - 1;
            int[] values = {0, 1, mask, mask / 2, (mask / 3) & mask};
            for (int in0 : values) {
                for (int in1 : values) {
                    for (int sel = 0; sel <= 1; sel++) {
                        Result result = evaluate(width,
                                LogicVector.fromUnsignedLong(in0, width),
                                LogicVector.fromUnsignedLong(in1, width),
                                sel == 0 ? LogicVector.ZERO : LogicVector.ONE);
                        assertEquals(result.behavioral(), result.structural(),
                                "width " + width + " in0=" + in0 + " in1=" + in1 + " sel=" + sel);
                        int expected = sel == 0 ? in0 : in1;
                        assertEquals(LogicVector.fromUnsignedLong(expected, width), result.structural());
                    }
                }
            }
        }
    }

    @Test
    void propagatesUnknownSelectIdenticallyToTheBehavioralMux() {
        int width = 4;
        Result result = evaluate(width, LogicVector.fromUnsignedLong(0b1010, width),
                LogicVector.fromUnsignedLong(0b0101, width), LogicVector.UNKNOWN);
        assertEquals(result.behavioral(), result.structural(), "an undefined SEL");
    }

    @Test
    void propagatesAPartiallyHighImpedanceSelectedInputIdenticallyToTheBehavioralMux() {
        int width = 4;
        LogicVector in0 = LogicVector.ofMsbFirst(
                LogicState.ONE, LogicState.HIGH_IMPEDANCE, LogicState.ZERO, LogicState.ONE);
        Result result = evaluate(width, in0, LogicVector.fromUnsignedLong(0, width), LogicVector.ZERO);
        assertEquals(result.behavioral(), result.structural(), "SEL=0 selects IN0, which carries a Z bit");
    }

    @Test
    void propagatesAPartiallyUnknownSelectedInputIdenticallyToTheBehavioralMux() {
        int width = 4;
        LogicVector in1 = LogicVector.ofMsbFirst(
                LogicState.UNKNOWN, LogicState.ONE, LogicState.ZERO, LogicState.ZERO);
        Result result = evaluate(width, LogicVector.fromUnsignedLong(0, width), in1, LogicVector.ONE);
        assertEquals(result.behavioral(), result.structural(), "SEL=1 selects IN1, which carries an X bit");
    }

    @Test
    void everyRegisteredMuxWidthIsAvailableThroughTheStandardRegistryAtGateLevel() {
        StructuralImplementationRegistry registry = StructuralImplementationRegistry.standard();
        for (int width : List.of(1, 3, 4, 8, 16)) {
            assertTrue(registry.find("routing.mux2",
                    ParameterValues.empty().with(LibraryParameters.WIDTH, width), ImplementationLevel.GATE)
                    .isPresent(), "width " + width);
        }
        assertTrue(registry.find("routing.mux2",
                ParameterValues.empty().with(LibraryParameters.WIDTH, 5), ImplementationLevel.GATE)
                .isEmpty(), "an unregistered width must not silently match");
    }

    private record Result(LogicVector behavioral, LogicVector structural) {
    }

    /** Wires one behavioral {@code routing.mux2} and one structural bus-mux to the same drivers. */
    private static Result evaluate(int width, LogicVector in0, LogicVector in1, LogicVector sel) {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitProject project = new CircuitProject("struct-mux-equivalence");
        for (CircuitDocument child : StructuralCircuitFactory.structuralBusMuxProject(width).circuits()) {
            if (!dev.logicforge.circuit.document.CircuitProject.MAIN_CIRCUIT.equals(child.metadata().name())) {
                project.putCircuit(child);
            }
        }
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", "mux equivalence harness"));

        BusSource in0A = busSource(main, "IN0_A", -320, -180, in0);
        BusSource in1A = busSource(main, "IN1_A", -320, -120, in1);
        ComponentInstance selA = scalarSource(main, "SEL_A", -320, -60, sel);
        ComponentInstance behavioral = add(main, "routing.mux2", "BEHAVIORAL", 0, -100,
                registry.require("routing.mux2").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, width));
        ComponentInstance behavioralOut = add(main, "routing.bus_probe", "BEHAVIORAL_OUT", 300, -100,
                busProbeParams(registry, width));

        BusSource in0B = busSource(main, "IN0_B", -320, 60, in0);
        BusSource in1B = busSource(main, "IN1_B", -320, 120, in1);
        ComponentInstance selB = scalarSource(main, "SEL_B", -320, 180, sel);
        ComponentInstance structural = sub(main, StructuralCircuitFactory.structuralBusMuxName(width),
                "STRUCTURAL", 0, 100);
        ComponentInstance structuralOut = add(main, "routing.bus_probe", "STRUCTURAL_OUT", 300, 100,
                busProbeParams(registry, width));

        wire(main, in0A.instance(), in0A.port(), behavioral, "IN0");
        wire(main, in1A.instance(), in1A.port(), behavioral, "IN1");
        wire(main, selA, "OUT", behavioral, "SEL");
        wire(main, behavioral, "OUT", behavioralOut, "IN");

        wire(main, in0B.instance(), in0B.port(), structural, "IN0");
        wire(main, in1B.instance(), in1B.port(), structural, "IN1");
        wire(main, selB, "OUT", structural, "SEL");
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

    private static ParameterValues busProbeParams(ComponentRegistry registry, int width) {
        return registry.require("routing.bus_probe").definition().defaultParameters()
                .with(LibraryParameters.WIDTH, width);
    }

    private record BusSource(ComponentInstance instance, String port) {
    }

    /**
     * A fully defined value becomes one {@code routing.bus_constant}; anything carrying X or
     * Z is built bit by bit from individual sources and joined, since {@code
     * routing.bus_constant} only accepts hex (fully defined) values.
     */
    private static BusSource busSource(CircuitDocument document, String label, double x, double y,
                                       LogicVector value) {
        if (value.isFullyDefined()) {
            ComponentInstance constant = add(document, "routing.bus_constant", label, x, y,
                    ComponentRegistry.standard().require("routing.bus_constant").definition()
                            .defaultParameters()
                            .with(LibraryParameters.WIDTH, value.width())
                            .with(LibraryParameters.BUS_CONSTANT_VALUE,
                                    Long.toHexString(value.toUnsignedLong().orElseThrow())));
            return new BusSource(constant, "OUT");
        }
        ComponentRegistry registry = ComponentRegistry.standard();
        ComponentInstance joiner = add(document, "routing.joiner", label + "_JOIN", x + 60, y,
                registry.require("routing.joiner").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, value.width()));
        for (int bit = 0; bit < value.width(); bit++) {
            ComponentInstance bitSource = add(document, sourceTypeFor(value.getBit(bit)),
                    label + "_BIT" + bit, x, y + bit * 30, ParameterValues.empty());
            wire(document, bitSource, "OUT", joiner, "BIT" + bit);
        }
        return new BusSource(joiner, "BUS");
    }

    private static String sourceTypeFor(LogicState state) {
        return switch (state) {
            case ZERO -> "source.zero";
            case ONE -> "source.one";
            case UNKNOWN -> "source.unknown";
            case HIGH_IMPEDANCE -> "source.highz";
        };
    }

    private static ComponentInstance scalarSource(CircuitDocument document, String label,
                                                   double x, double y, LogicVector value) {
        return add(document, sourceTypeFor(value.getBit(0)), label, x, y, ParameterValues.empty());
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
