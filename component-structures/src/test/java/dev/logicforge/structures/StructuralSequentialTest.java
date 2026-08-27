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

class StructuralSequentialTest {

    @Test
    void norSrLatchSetsResetsHoldsAndExposesTheInvalidState() {
        CircuitProject source = StructuralCircuitFactory.dFlipFlopProject();
        CircuitProject project = copyChildren(source);
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", "SR harness"));
        ComponentInstance s = add(main, "source.toggle", "S", -200, -50, ParameterValues.empty());
        ComponentInstance r = add(main, "source.toggle", "R", -200, 50, ParameterValues.empty());
        ComponentInstance latch = sub(main, StructuralCircuitFactory.SR_LATCH_NOR, "LATCH", 0, 0);
        ComponentInstance q = add(main, "output.probe", "Q", 200, -40, ParameterValues.empty());
        ComponentInstance qBar = add(main, "output.probe", "Q_BAR", 200, 40, ParameterValues.empty());
        wire(main, s, "OUT", latch, "S");
        wire(main, r, "OUT", latch, "R");
        wire(main, latch, "Q", q, "IN");
        wire(main, latch, "Q_BAR", qBar, "IN");
        project.putCircuit(main);
        var compiled = new CircuitCompiler(ComponentRegistry.standard()).compile(project, "main");
        Simulation simulation = new Simulation(compiled.circuit());
        int sId = compiled.sourceMap().componentId(s.id()).orElseThrow();
        int rId = compiled.sourceMap().componentId(r.id()).orElseThrow();
        int qNet = compiled.sourceMap().netOf(new PortReference(q.id(), "IN")).orElseThrow();
        int qBarNet = compiled.sourceMap().netOf(new PortReference(qBar.id(), "IN")).orElseThrow();

        simulation.setInput(sId, LogicState.ONE);
        assertEquals(LogicVector.ONE, simulation.readNet(qNet));
        assertEquals(LogicVector.ZERO, simulation.readNet(qBarNet));
        simulation.setInput(sId, LogicState.ZERO);
        assertEquals(LogicVector.ONE, simulation.readNet(qNet), "S=R=0 must hold the set state");

        simulation.setInput(rId, LogicState.ONE);
        assertEquals(LogicVector.ZERO, simulation.readNet(qNet));
        assertEquals(LogicVector.ONE, simulation.readNet(qBarNet));
        simulation.setInput(sId, LogicState.ONE);
        assertEquals(LogicVector.ZERO, simulation.readNet(qNet));
        assertEquals(LogicVector.ZERO, simulation.readNet(qBarNet),
                "S=R=1 is the documented invalid NOR-latch state");
    }

    @Test
    void masterSlaveDffCapturesOnlyOnTheRisingEdge() {
        CircuitProject project = copyChildren(StructuralCircuitFactory.dFlipFlopProject());
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", "DFF harness"));
        ComponentInstance d = add(main, "source.toggle", "D", -200, -40, ParameterValues.empty());
        ComponentInstance clk = add(main, "source.toggle", "CLK", -200, 40, ParameterValues.empty());
        ComponentInstance dff = sub(main, StructuralCircuitFactory.DFF, "DFF", 0, 0);
        ComponentInstance q = add(main, "output.probe", "Q", 200, 0, ParameterValues.empty());
        wire(main, d, "OUT", dff, "D");
        wire(main, clk, "OUT", dff, "CLK");
        wire(main, dff, "Q", q, "IN");
        project.putCircuit(main);
        var compiled = new CircuitCompiler(ComponentRegistry.standard()).compile(project, "main");
        Simulation simulation = new Simulation(compiled.circuit());
        int dId = compiled.sourceMap().componentId(d.id()).orElseThrow();
        int clkId = compiled.sourceMap().componentId(clk.id()).orElseThrow();
        int qNet = compiled.sourceMap().netOf(new PortReference(q.id(), "IN")).orElseThrow();

        simulation.setInput(clkId, LogicState.ONE);
        assertEquals(LogicVector.ZERO, simulation.readNet(qNet));
        simulation.setInput(dId, LogicState.ONE);
        assertEquals(LogicVector.ZERO, simulation.readNet(qNet), "D changes while CLK high must not leak");
        simulation.setInput(clkId, LogicState.ZERO);
        assertEquals(LogicVector.ZERO, simulation.readNet(qNet));
        simulation.setInput(clkId, LogicState.ONE);
        assertEquals(LogicVector.ONE, simulation.readNet(qNet));
    }

    @Test
    void register8LoadsAndHoldsThroughDffAndLatchHierarchy() {
        CircuitProject source = StructuralCircuitFactory.register8Project();
        CircuitDocument register = source.circuit(StructuralCircuitFactory.REGISTER8).orElseThrow();
        assertEquals(8, register.components().stream().filter(component -> component.definitionId()
                .equals(SubcircuitSupport.definitionId(StructuralCircuitFactory.DFF))).count());
        assertEquals(8, register.components().stream().filter(component -> component.definitionId()
                .equals(SubcircuitSupport.definitionId(StructuralCircuitFactory.MUX2))).count());
        assertFalse(source.circuits().stream().flatMap(circuit -> circuit.components().stream())
                .anyMatch(component -> component.definitionId().equals("sequential.register")));

        CircuitProject project = copyChildren(source);
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", "Register harness"));
        ComponentRegistry registry = ComponentRegistry.standard();
        ComponentInstance data = add(main, "system.input_port", "DATA", -250, -80,
                registry.require("system.input_port").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8));
        ComponentInstance one = add(main, "source.one", "ONE", -250, -160, ParameterValues.empty());
        ComponentInstance load = add(main, "source.toggle", "LOAD", -250, 0, ParameterValues.empty());
        ComponentInstance clk = add(main, "source.toggle", "CLK", -250, 80, ParameterValues.empty());
        ComponentInstance dut = sub(main, StructuralCircuitFactory.REGISTER8, "REGISTER8", 0, 0);
        ComponentInstance q = add(main, "routing.bus_probe", "Q", 250, 0,
                registry.require("routing.bus_probe").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8));
        wire(main, one, "OUT", data, "SELECT");
        wire(main, one, "OUT", data, "READ");
        wire(main, data, "DATA", dut, "DATA");
        wire(main, load, "OUT", dut, "LOAD");
        wire(main, clk, "OUT", dut, "CLK");
        wire(main, dut, "Q", q, "IN");
        project.putCircuit(main);
        var compiled = new CircuitCompiler(registry).compile(project, "main");
        Simulation simulation = new Simulation(compiled.circuit());
        int dataId = compiled.sourceMap().componentId(data.id()).orElseThrow();
        int loadId = compiled.sourceMap().componentId(load.id()).orElseThrow();
        int clkId = compiled.sourceMap().componentId(clk.id()).orElseThrow();
        int qNet = compiled.sourceMap().netOf(new PortReference(q.id(), "IN")).orElseThrow();

        simulation.setInput(dataId, LogicVector.fromUnsignedLong(0xa5, 8));
        simulation.setInput(loadId, LogicState.ONE);
        simulation.setInput(clkId, LogicState.ONE);
        assertEquals(LogicVector.fromUnsignedLong(0xa5, 8), simulation.readNet(qNet));

        simulation.setInput(clkId, LogicState.ZERO);
        simulation.setInput(loadId, LogicState.ZERO);
        simulation.setInput(dataId, LogicVector.fromUnsignedLong(0x5a, 8));
        simulation.setInput(clkId, LogicState.ONE);
        assertEquals(LogicVector.fromUnsignedLong(0xa5, 8), simulation.readNet(qNet));
    }

    @Test
    void resettableDffCapturesHoldsAndResetsConservatively() {
        CircuitProject project = copyChildren(StructuralCircuitFactory.resettableDFlipFlopProject());
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", "reset DFF harness"));
        ComponentInstance d = add(main, "source.toggle", "D", -200, -60, ParameterValues.empty());
        ComponentInstance clk = add(main, "source.toggle", "CLK", -200, 0, ParameterValues.empty());
        ComponentInstance reset = add(main, "source.toggle", "RESET", -200, 60, ParameterValues.empty());
        ComponentInstance dff = sub(main, StructuralCircuitFactory.DFF_RESET, "DFF", 0, 0);
        ComponentInstance q = add(main, "output.probe", "Q", 200, 0, ParameterValues.empty());
        wire(main, d, "OUT", dff, "D");
        wire(main, clk, "OUT", dff, "CLK");
        wire(main, reset, "OUT", dff, "RESET");
        wire(main, dff, "Q", q, "IN");
        project.putCircuit(main);
        var compiled = new CircuitCompiler(ComponentRegistry.standard()).compile(project, "main");
        Simulation simulation = new Simulation(compiled.circuit());
        int dId = compiled.sourceMap().componentId(d.id()).orElseThrow();
        int clkId = compiled.sourceMap().componentId(clk.id()).orElseThrow();
        int resetId = compiled.sourceMap().componentId(reset.id()).orElseThrow();
        int qNet = compiled.sourceMap().netOf(new PortReference(q.id(), "IN")).orElseThrow();

        simulation.setInput(dId, LogicState.ONE);
        simulation.setInput(clkId, LogicState.ONE);
        assertEquals(LogicState.ONE, simulation.readNet(qNet).singleBit(), "capture");
        simulation.setInput(dId, LogicState.ZERO);
        assertEquals(LogicState.ONE, simulation.readNet(qNet).singleBit(), "hold while clock high");
        simulation.setInput(resetId, LogicState.UNKNOWN);
        assertEquals(LogicState.UNKNOWN, simulation.readNet(qNet).singleBit(), "unknown reset");
        simulation.setInput(resetId, LogicState.ONE);
        assertEquals(LogicState.ZERO, simulation.readNet(qNet).singleBit(), "asynchronous reset");
        simulation.setInput(resetId, LogicState.ZERO);
        simulation.setInput(clkId, LogicState.UNKNOWN);
        assertTrue(simulation.readNet(qNet).singleBit() == LogicState.ZERO
                || simulation.readNet(qNet).singleBit() == LogicState.UNKNOWN,
                "unknown clock must never invent a captured one");
    }

    @Test
    void parametricRegisterFamilyUsesOnlyStructuralDffs() {
        for (int width : new int[]{1, 3, 4, 8, 16}) {
            CircuitProject project = StructuralCircuitFactory.structuralRegister(width, true, 0);
            String name = StructuralCircuitFactory.structuralRegisterName(width, true, 0);
            CircuitDocument register = project.circuit(name).orElseThrow();
            assertEquals(width, register.components().stream()
                    .filter(component -> component.definitionId().equals(
                            SubcircuitSupport.definitionId(StructuralCircuitFactory.DFF_RESET)))
                    .count());
            assertFalse(project.circuits().stream().flatMap(circuit -> circuit.components().stream())
                    .anyMatch(component -> component.definitionId().startsWith("sequential.register")));
        }
    }

    private static CircuitProject copyChildren(CircuitProject source) {
        CircuitProject copy = new CircuitProject(source.name());
        source.circuits().stream()
                .filter(circuit -> !circuit.metadata().name().equals("main"))
                .forEach(copy::putCircuit);
        return copy;
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
