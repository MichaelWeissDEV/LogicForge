package dev.logicforge.structures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import org.junit.jupiter.api.Test;

/**
 * {@link StructuralCircuitFactory#decoder2To4Project} is a plain gate-level 2-to-4 decoder
 * — {@code A} in, {@code Y} out — with no {@code ENABLE} input at all, unlike the behavioral
 * {@code routing.decoder} it would need to stand in for ({@code SEL, ENABLE} in, {@code
 * OUT0..OUT3} out). It is deliberately <b>not</b> registered in {@link
 * StructuralImplementationRegistry}: these tests exist to pin down exactly why, not just as
 * a port-shape mismatch but a real four-valued-logic divergence, so a future attempt to wire
 * an ENABLE gate onto it knows what it still has to get right.
 *
 * <p>For a fully defined {@code SEL}, the two agree — see {@link
 * #matchesTheEnabledBehavioralDecoderForEveryFullyDefinedSelectValue()}. The divergence only
 * shows up once {@code SEL} carries an {@code X}: the behavioral decoder treats <em>any</em>
 * undefined {@code SEL} bit as "cannot know which output would be selected," so it drives
 * every output to X. The gate-level product terms, though, resolve some outputs to a
 * definite 0 whenever a term happens to depend on a select bit that is 0 rather than the X
 * one — see {@link #divergesFromTheBehavioralDecoderOnAPartiallyUndefinedSelect()}.
 */
class StructuralDecoderTest {

    @Test
    void matchesTheEnabledBehavioralDecoderForEveryFullyDefinedSelectValue() {
        for (int sel = 0; sel < 4; sel++) {
            Result result = evaluate(LogicVector.fromUnsignedLong(sel, 2));
            assertEquals(result.behavioral(), result.structural(), "SEL=" + sel);
            for (int output = 0; output < 4; output++) {
                assertEquals(output == sel, result.structural().getBit(output) == LogicState.ONE);
            }
        }
    }

    @Test
    void divergesFromTheBehavioralDecoderOnAPartiallyUndefinedSelect() {
        // SEL = "X0": bit 0 (LSB) is defined as 0, bit 1 is undefined.
        LogicVector partiallyUndefined = LogicVector.of("X0");

        Result result = evaluate(partiallyUndefined);

        assertEquals(LogicVector.repeat(LogicState.UNKNOWN, 4), result.behavioral(),
                "the behavioral decoder cannot know which output applies, so every output is X");
        assertNotEquals(result.behavioral(), result.structural(),
                "the gate-level product terms are NOT uniformly X — see class javadoc");
        // OUT1 and OUT3 (SEL bit0=1) depend on a dominant, structurally-defined 0 from
        // that bit's true/complement term, so the AND resolves to 0 despite bit1 being X.
        assertEquals(LogicState.ZERO, result.structural().getBit(1));
        assertEquals(LogicState.ZERO, result.structural().getBit(3));
        // OUT0 and OUT2 (SEL bit0=0) genuinely depend on the undefined bit1, so those stay X.
        assertEquals(LogicState.UNKNOWN, result.structural().getBit(0));
        assertEquals(LogicState.UNKNOWN, result.structural().getBit(2));
    }

    @Test
    void isDeliberatelyNotRegisteredInTheStandardRegistry() {
        StructuralImplementationRegistry registry = StructuralImplementationRegistry.standard();
        assertTrue(registry.find("routing.decoder",
                ParameterValues.empty().with(LibraryParameters.SELECT_BITS, 2), ImplementationLevel.GATE)
                .isEmpty(), "not a faithful implementation yet — see class javadoc");
    }

    private record Result(LogicVector behavioral, LogicVector structural) {
    }

    private static Result evaluate(LogicVector sel) {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitProject project = new CircuitProject("struct-decoder-equivalence");
        for (CircuitDocument child : StructuralCircuitFactory.decoder2To4Project().circuits()) {
            if (!dev.logicforge.circuit.document.CircuitProject.MAIN_CIRCUIT.equals(child.metadata().name())) {
                project.putCircuit(child);
            }
        }
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", "decoder equivalence harness"));

        BusSource selA = busSource(main, "SEL_A", -300, -100, sel);
        ComponentInstance enable = add(main, "source.one", "ENABLE", -300, 0, ParameterValues.empty());
        ComponentInstance behavioral = add(main, "routing.decoder", "BEHAVIORAL", 0, -60,
                registry.require("routing.decoder").definition().defaultParameters()
                        .with(LibraryParameters.SELECT_BITS, 2));
        ComponentInstance behavioralJoin = add(main, "routing.joiner", "BEHAVIORAL_JOIN", 200, -60,
                registry.require("routing.joiner").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 4));
        ComponentInstance behavioralOut = add(main, "routing.bus_probe", "BEHAVIORAL_OUT", 380, -60,
                busProbeParams(registry));

        BusSource selB = busSource(main, "SEL_B", -300, 100, sel);
        ComponentInstance structural = sub(main, StructuralCircuitFactory.DECODER_2_TO_4, "STRUCTURAL", 0, 100);
        ComponentInstance structuralOut = add(main, "routing.bus_probe", "STRUCTURAL_OUT", 380, 100,
                busProbeParams(registry));

        wire(main, selA.instance(), selA.port(), behavioral, "SEL");
        wire(main, enable, "OUT", behavioral, "ENABLE");
        for (int i = 0; i < 4; i++) {
            wire(main, behavioral, "OUT" + i, behavioralJoin, "BIT" + i);
        }
        wire(main, behavioralJoin, "BUS", behavioralOut, "IN");

        wire(main, selB.instance(), selB.port(), structural, "A");
        wire(main, structural, "Y", structuralOut, "IN");

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
                .with(LibraryParameters.WIDTH, 4);
    }

    private record BusSource(ComponentInstance instance, String port) {
    }

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
