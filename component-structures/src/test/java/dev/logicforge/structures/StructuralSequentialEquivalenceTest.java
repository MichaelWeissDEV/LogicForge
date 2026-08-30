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
 * Structural sequential circuits (a resettable register, a loadable counter, a modulo-8
 * counter) driven side by side with their behavioral counterparts from the exact same
 * clock/data sources in one compiled circuit — the same "prove it, don't just registry-check
 * it" discipline {@link StructuralMuxTest} and {@link StructuralDecrementerTest} already
 * apply, extended to clocked components (drive an edge, compare both outputs, repeat).
 */
class StructuralSequentialEquivalenceTest {

    // ------------------------------------------------------------ register_reset

    @Test
    void resettableRegisterMatchesTheBehavioralRegisterResetAcrossWidths() {
        for (int width : List.of(1, 3, 4, 8)) {
            RegisterHarness harness = registerHarness(width);
            int mask = width == 32 ? -1 : (1 << width) - 1;

            harness.setReset(LogicState.ONE);
            harness.risingEdge();
            assertQMatch(harness, 0, "reset forces Q to zero");
            harness.fallingEdge();
            harness.setReset(LogicState.ZERO);

            harness.setData((0x5A & mask));
            harness.setLoad(LogicState.ZERO);
            harness.risingEdge();
            assertQMatch(harness, 0, "LOAD=0 must not capture DATA");
            harness.fallingEdge();

            harness.setLoad(LogicState.ONE);
            harness.risingEdge();
            assertQMatch(harness, 0x5A & mask, "LOAD=1 captures DATA on the active edge");
            harness.fallingEdge();

            harness.setData((0xA5 & mask));
            harness.risingEdge();
            assertQMatch(harness, 0xA5 & mask, "a second load captures the new value");
        }
    }

    @Test
    void isAvailableInTheStandardRegistryForEveryRegisteredWidth() {
        StructuralImplementationRegistry registry = StructuralImplementationRegistry.standard();
        for (int width : List.of(1, 3, 4, 8)) {
            assertTrue(registry.find("sequential.register_reset",
                    ParameterValues.empty().with(LibraryParameters.WIDTH, width)
                            .with(LibraryParameters.CLOCK_EDGE, "rising"),
                    ImplementationLevel.GATE).isPresent(), "width " + width);
        }
        assertTrue(registry.find("sequential.register_reset",
                ParameterValues.empty().with(LibraryParameters.WIDTH, 16)
                        .with(LibraryParameters.CLOCK_EDGE, "rising"),
                ImplementationLevel.GATE).isEmpty(), "width 16 has no proven structural build");
    }

    private static void assertQMatch(RegisterHarness harness, long expected, String message) {
        assertEquals(harness.behavioralQ(), harness.structuralQ(), message);
        assertEquals(LogicVector.fromUnsignedLong(expected, harness.width), harness.structuralQ(), message);
    }

    private static RegisterHarness registerHarness(int width) {
        CircuitProject source = StructuralCircuitFactory.structuralRegister(width, true, 0);
        CircuitProject project = new CircuitProject("register-reset-equivalence");
        source.circuits().stream().filter(circuit -> !circuit.metadata().name().equals("main"))
                .forEach(project::putCircuit);
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", "register-reset harness"));

        ComponentInstance clk = add(main, "source.toggle", "CLK", -320, -100, ParameterValues.empty());
        ComponentInstance reset = add(main, "source.toggle", "RESET", -320, -40, ParameterValues.empty());
        ComponentInstance load = add(main, "source.toggle", "LOAD", -320, 20, ParameterValues.empty());
        ComponentInstance dataTie = add(main, "source.one", "DATA_TIE", -400, 80, ParameterValues.empty());
        ComponentInstance data = add(main, "system.input_port", "DATA", -320, 80,
                registry.require("system.input_port").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, width));
        wire(main, dataTie, "OUT", data, "SELECT");
        wire(main, dataTie, "OUT", data, "READ");

        ComponentInstance behavioral = add(main, "sequential.register_reset", "BEHAVIORAL", 0, -60,
                registry.require("sequential.register_reset").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, width));
        ComponentInstance behavioralOut = add(main, "routing.bus_probe", "BEHAVIORAL_Q", 260, -60,
                busProbeParams(registry, width));

        String registerName = StructuralCircuitFactory.structuralRegisterName(width, true, 0);
        ComponentInstance structural = sub(main, registerName, "STRUCTURAL", 0, 100);
        ComponentInstance structuralOut = add(main, "routing.bus_probe", "STRUCTURAL_Q", 260, 100,
                busProbeParams(registry, width));

        wire(main, clk, "OUT", behavioral, "CLK");
        wire(main, reset, "OUT", behavioral, "RESET");
        wire(main, load, "OUT", behavioral, "LOAD");
        wire(main, data, "DATA", behavioral, "DATA");
        wire(main, behavioral, "Q", behavioralOut, "IN");

        wire(main, clk, "OUT", structural, "CLK");
        wire(main, reset, "OUT", structural, "RESET");
        wire(main, load, "OUT", structural, "LOAD");
        wire(main, data, "DATA", structural, "DATA");
        wire(main, structural, "Q", structuralOut, "IN");

        project.putCircuit(main);
        var compiled = new CircuitCompiler(registry).compile(project, "main");
        Simulation simulation = new Simulation(compiled.circuit());
        return new RegisterHarness(simulation, width,
                compiled.sourceMap().componentId(clk.id()).orElseThrow(),
                compiled.sourceMap().componentId(reset.id()).orElseThrow(),
                compiled.sourceMap().componentId(load.id()).orElseThrow(),
                compiled.sourceMap().componentId(data.id()).orElseThrow(),
                compiled.sourceMap().netOf(PortEndpoint.whole(new PortReference(behavioralOut.id(), "IN")))
                        .orElseThrow(),
                compiled.sourceMap().netOf(PortEndpoint.whole(new PortReference(structuralOut.id(), "IN")))
                        .orElseThrow());
    }

    private record RegisterHarness(Simulation simulation, int width, int clk, int reset, int load,
                                   int data, int behavioralQNet, int structuralQNet) {
        void setReset(LogicState value) {
            simulation.setInput(reset, value);
        }

        void setLoad(LogicState value) {
            simulation.setInput(load, value);
        }

        void setData(long value) {
            simulation.setInput(data, LogicVector.fromUnsignedLong(value, width));
        }

        void risingEdge() {
            simulation.setInput(clk, LogicState.ONE);
        }

        void fallingEdge() {
            simulation.setInput(clk, LogicState.ZERO);
        }

        LogicVector behavioralQ() {
            return simulation.readNet(behavioralQNet);
        }

        LogicVector structuralQ() {
            return simulation.readNet(structuralQNet);
        }
    }

    // ------------------------------------------------------------ loadable_counter16

    @Test
    void loadableCounter16MatchesTheBehavioralLoadableCounterIncludingTerminalCount() {
        for (int resetValue : List.of(0, 0xBFFF)) {
            CounterHarness harness = loadableCounterHarness(resetValue);

            harness.setReset(LogicState.ONE);
            harness.risingEdge();
            assertCountMatch(harness, resetValue & 0xffff, "reset forces COUNT to its reset value");
            harness.fallingEdge();
            harness.setReset(LogicState.ZERO);

            harness.setData(0xFFFE);
            harness.setLoad(LogicState.ONE);
            harness.setEnable(LogicState.ZERO);
            harness.risingEdge();
            assertCountMatch(harness, 0xFFFE, "LOAD wins over ENABLE");
            harness.fallingEdge();

            harness.setLoad(LogicState.ZERO);
            harness.setEnable(LogicState.ONE);
            harness.risingEdge();
            assertCountMatch(harness, 0xFFFF, "ENABLE advances by one");
            assertEquals(LogicVector.ONE, harness.behavioralTc(), "0xFFFF is the terminal count");
            assertEquals(harness.behavioralTc(), harness.structuralTc());
            harness.fallingEdge();

            harness.risingEdge();
            assertCountMatch(harness, 0, "wraps from 0xFFFF back to 0");
            assertEquals(LogicVector.ZERO, harness.behavioralTc());
            assertEquals(harness.behavioralTc(), harness.structuralTc());
        }
    }

    @Test
    void loadableCounter16IsAvailableInTheStandardRegistryForItsProvenResetValues() {
        StructuralImplementationRegistry registry = StructuralImplementationRegistry.standard();
        for (int resetValue : List.of(0, 0xBFFF)) {
            assertTrue(registry.find("sequential.loadable_counter",
                    ParameterValues.empty().with(LibraryParameters.WIDTH, 16)
                            .with(LibraryParameters.CLOCK_EDGE, "rising")
                            .with(LibraryParameters.RESET_VALUE, Integer.toHexString(resetValue)),
                    ImplementationLevel.GATE).isPresent(), "reset value 0x" + Integer.toHexString(resetValue));
        }
        assertTrue(registry.find("sequential.loadable_counter",
                ParameterValues.empty().with(LibraryParameters.WIDTH, 16)
                        .with(LibraryParameters.CLOCK_EDGE, "rising")
                        .with(LibraryParameters.RESET_VALUE, "1234"),
                ImplementationLevel.GATE).isEmpty(), "an unproven reset value must not silently match");
    }

    private static void assertCountMatch(CounterHarness harness, long expected, String message) {
        assertEquals(harness.behavioralCount(), harness.structuralCount(), message);
        assertEquals(LogicVector.fromUnsignedLong(expected, harness.width()), harness.structuralCount(), message);
    }

    private static CounterHarness loadableCounterHarness(int resetValue) {
        CircuitProject source = StructuralCircuitFactory.loadableCounter16Project(resetValue);
        CircuitProject project = new CircuitProject("loadable-counter-equivalence");
        source.circuits().stream().filter(circuit -> !circuit.metadata().name().equals("main"))
                .forEach(project::putCircuit);
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", "loadable-counter harness"));

        ComponentInstance clk = add(main, "source.toggle", "CLK", -320, -160, ParameterValues.empty());
        ComponentInstance reset = add(main, "source.toggle", "RESET", -320, -100, ParameterValues.empty());
        ComponentInstance load = add(main, "source.toggle", "LOAD", -320, -40, ParameterValues.empty());
        ComponentInstance enable = add(main, "source.toggle", "ENABLE", -320, 20, ParameterValues.empty());
        ComponentInstance dataTie = add(main, "source.one", "DATA_TIE", -400, 100, ParameterValues.empty());
        ComponentInstance data = add(main, "system.input_port", "DATA", -320, 100,
                registry.require("system.input_port").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 16));
        wire(main, dataTie, "OUT", data, "SELECT");
        wire(main, dataTie, "OUT", data, "READ");

        ComponentInstance behavioral = add(main, "sequential.loadable_counter", "BEHAVIORAL", 0, -100,
                registry.require("sequential.loadable_counter").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 16)
                        .with(LibraryParameters.RESET_VALUE, Integer.toHexString(resetValue)));
        ComponentInstance behavioralCount = add(main, "routing.bus_probe", "BEHAVIORAL_COUNT", 280, -140,
                busProbeParams(registry, 16));
        ComponentInstance behavioralTc = add(main, "output.probe", "BEHAVIORAL_TC", 280, -60,
                ParameterValues.empty());

        String counterName = StructuralCircuitFactory.loadableCounter16Name(resetValue);
        ComponentInstance structural = sub(main, counterName, "STRUCTURAL", 0, 100);
        ComponentInstance structuralCount = add(main, "routing.bus_probe", "STRUCTURAL_COUNT", 280, 60,
                busProbeParams(registry, 16));
        ComponentInstance structuralTc = add(main, "output.probe", "STRUCTURAL_TC", 280, 140,
                ParameterValues.empty());

        wire(main, clk, "OUT", behavioral, "CLK");
        wire(main, reset, "OUT", behavioral, "RESET");
        wire(main, load, "OUT", behavioral, "LOAD");
        wire(main, enable, "OUT", behavioral, "ENABLE");
        wire(main, data, "DATA", behavioral, "DATA");
        wire(main, behavioral, "COUNT", behavioralCount, "IN");
        wire(main, behavioral, "TC", behavioralTc, "IN");

        wire(main, clk, "OUT", structural, "CLK");
        wire(main, reset, "OUT", structural, "RESET");
        wire(main, load, "OUT", structural, "LOAD");
        wire(main, enable, "OUT", structural, "ENABLE");
        wire(main, data, "DATA", structural, "DATA");
        wire(main, structural, "COUNT", structuralCount, "IN");
        wire(main, structural, "TC", structuralTc, "IN");

        project.putCircuit(main);
        var compiled = new CircuitCompiler(registry).compile(project, "main");
        Simulation simulation = new Simulation(compiled.circuit());
        return new CounterHarness(simulation,
                compiled.sourceMap().componentId(clk.id()).orElseThrow(),
                compiled.sourceMap().componentId(reset.id()).orElseThrow(),
                compiled.sourceMap().componentId(enable.id()).orElseThrow(),
                compiled.sourceMap().componentId(load.id()).orElseThrow(),
                compiled.sourceMap().componentId(data.id()).orElseThrow(), 16,
                compiled.sourceMap().netOf(PortEndpoint.whole(new PortReference(behavioralCount.id(), "IN")))
                        .orElseThrow(),
                compiled.sourceMap().netOf(PortEndpoint.whole(new PortReference(structuralCount.id(), "IN")))
                        .orElseThrow(),
                compiled.sourceMap().netOf(new PortReference(behavioralTc.id(), "IN")).orElseThrow(),
                compiled.sourceMap().netOf(new PortReference(structuralTc.id(), "IN")).orElseThrow());
    }

    // ------------------------------------------------------------ modulo_counter3

    @Test
    void moduloCounter3MatchesTheBehavioralModuloCounterAcrossAFullWrap() {
        CounterHarness harness = moduloCounterHarness();
        harness.setReset(LogicState.ONE);
        harness.risingEdge();
        assertCountMatch(harness, 0, "reset forces COUNT to zero");
        harness.fallingEdge();
        harness.setReset(LogicState.ZERO);
        harness.setEnable(LogicState.ONE);

        for (int value = 1; value <= 8; value++) {
            harness.risingEdge();
            assertCountMatch(harness, value & 7, "value " + value);
            assertEquals(LogicState.of((value & 7) == 7), harness.behavioralTc().singleBit(),
                    "TC at value " + value);
            assertEquals(harness.behavioralTc(), harness.structuralTc(), "value " + value);
            harness.fallingEdge();
        }
    }

    @Test
    void moduloCounter3IsAvailableInTheStandardRegistry() {
        StructuralImplementationRegistry registry = StructuralImplementationRegistry.standard();
        assertTrue(registry.find("sequential.modulo_counter",
                ParameterValues.empty().with(LibraryParameters.WIDTH, 3)
                        .with(LibraryParameters.MODULUS, 8)
                        .with(LibraryParameters.CLOCK_EDGE, "rising"),
                ImplementationLevel.GATE).isPresent());
        assertTrue(registry.find("sequential.modulo_counter",
                ParameterValues.empty().with(LibraryParameters.WIDTH, 3)
                        .with(LibraryParameters.MODULUS, 5)
                        .with(LibraryParameters.CLOCK_EDGE, "rising"),
                ImplementationLevel.GATE).isEmpty(), "only the full-range modulus 8 is a proven structural build");
    }

    private static CounterHarness moduloCounterHarness() {
        CircuitProject source = StructuralCircuitFactory.moduloCounter3Project();
        CircuitProject project = new CircuitProject("modulo-counter-equivalence");
        source.circuits().stream().filter(circuit -> !circuit.metadata().name().equals("main"))
                .forEach(project::putCircuit);
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", "modulo-counter harness"));

        ComponentInstance clk = add(main, "source.toggle", "CLK", -280, -100, ParameterValues.empty());
        ComponentInstance reset = add(main, "source.toggle", "RESET", -280, -40, ParameterValues.empty());
        ComponentInstance enable = add(main, "source.toggle", "ENABLE", -280, 20, ParameterValues.empty());

        ComponentInstance behavioral = add(main, "sequential.modulo_counter", "BEHAVIORAL", 0, -80,
                registry.require("sequential.modulo_counter").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 3)
                        .with(LibraryParameters.MODULUS, 8));
        ComponentInstance behavioralCount = add(main, "routing.bus_probe", "BEHAVIORAL_COUNT", 280, -120,
                busProbeParams(registry, 3));
        ComponentInstance behavioralTc = add(main, "output.probe", "BEHAVIORAL_TC", 280, -40,
                ParameterValues.empty());

        ComponentInstance structural = sub(main, StructuralCircuitFactory.MODULO_COUNTER3, "STRUCTURAL", 0, 100);
        ComponentInstance structuralCount = add(main, "routing.bus_probe", "STRUCTURAL_COUNT", 280, 60,
                busProbeParams(registry, 3));
        ComponentInstance structuralTc = add(main, "output.probe", "STRUCTURAL_TC", 280, 140,
                ParameterValues.empty());

        wire(main, clk, "OUT", behavioral, "CLK");
        wire(main, reset, "OUT", behavioral, "RESET");
        wire(main, enable, "OUT", behavioral, "ENABLE");
        wire(main, behavioral, "COUNT", behavioralCount, "IN");
        wire(main, behavioral, "TC", behavioralTc, "IN");

        wire(main, clk, "OUT", structural, "CLK");
        wire(main, reset, "OUT", structural, "RESET");
        wire(main, enable, "OUT", structural, "ENABLE");
        wire(main, structural, "COUNT", structuralCount, "IN");
        wire(main, structural, "TC", structuralTc, "IN");

        project.putCircuit(main);
        var compiled = new CircuitCompiler(registry).compile(project, "main");
        Simulation simulation = new Simulation(compiled.circuit());
        return new CounterHarness(simulation,
                compiled.sourceMap().componentId(clk.id()).orElseThrow(),
                compiled.sourceMap().componentId(reset.id()).orElseThrow(),
                compiled.sourceMap().componentId(enable.id()).orElseThrow(), -1, -1, 3,
                compiled.sourceMap().netOf(PortEndpoint.whole(new PortReference(behavioralCount.id(), "IN")))
                        .orElseThrow(),
                compiled.sourceMap().netOf(PortEndpoint.whole(new PortReference(structuralCount.id(), "IN")))
                        .orElseThrow(),
                compiled.sourceMap().netOf(new PortReference(behavioralTc.id(), "IN")).orElseThrow(),
                compiled.sourceMap().netOf(new PortReference(structuralTc.id(), "IN")).orElseThrow());
    }

    private record CounterHarness(Simulation simulation, int clk, int reset, int enable, int load,
                                  int data, int width, int behavioralCountNet, int structuralCountNet,
                                  int behavioralTcNet, int structuralTcNet) {
        void setReset(LogicState value) {
            simulation.setInput(reset, value);
        }

        void setEnable(LogicState value) {
            simulation.setInput(enable, value);
        }

        void setLoad(LogicState value) {
            simulation.setInput(load, value);
        }

        void setData(long value) {
            simulation.setInput(data, LogicVector.fromUnsignedLong(value, width));
        }

        void risingEdge() {
            simulation.setInput(clk, LogicState.ONE);
        }

        void fallingEdge() {
            simulation.setInput(clk, LogicState.ZERO);
        }

        LogicVector behavioralCount() {
            return simulation.readNet(behavioralCountNet);
        }

        LogicVector structuralCount() {
            return simulation.readNet(structuralCountNet);
        }

        LogicVector behavioralTc() {
            return simulation.readNet(behavioralTcNet);
        }

        LogicVector structuralTc() {
            return simulation.readNet(structuralTcNet);
        }
    }

    // ------------------------------------------------------------ shared helpers

    private static ParameterValues busProbeParams(ComponentRegistry registry, int width) {
        return registry.require("routing.bus_probe").definition().defaultParameters()
                .with(LibraryParameters.WIDTH, width);
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
