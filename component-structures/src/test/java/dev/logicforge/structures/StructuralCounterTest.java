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

class StructuralCounterTest {

    @Test
    void loadableCounterResetsLoadsHoldsAndIncrements() {
        CircuitProject source = StructuralCircuitFactory.loadableCounter16Project(0xBFFF);
        assertFalse(source.circuits().stream().flatMap(circuit -> circuit.components().stream())
                .anyMatch(component -> component.definitionId().equals("sequential.loadable_counter")));
        Harness harness = harness(source,
                StructuralCircuitFactory.loadableCounter16Name(0xBFFF), 16, true);

        harness.setReset(LogicState.ONE);
        assertEquals(0xBFFF, harness.count());
        harness.setReset(LogicState.ZERO);
        harness.setData(0x1234);
        harness.setLoad(LogicState.ONE);
        harness.risingEdge();
        assertEquals(0x1234, harness.count());
        harness.fallingEdge();
        harness.setLoad(LogicState.ZERO);
        harness.setEnable(LogicState.ZERO);
        harness.risingEdge();
        assertEquals(0x1234, harness.count());
        harness.fallingEdge();
        harness.setEnable(LogicState.ONE);
        harness.risingEdge();
        assertEquals(0x1235, harness.count());
    }

    @Test
    void moduloCounterDescendsThroughRegister3AndWrapsAtEight() {
        CircuitProject source = StructuralCircuitFactory.moduloCounter3Project();
        CircuitDocument counter = source.circuit(StructuralCircuitFactory.MODULO_COUNTER3)
                .orElseThrow();
        assertEquals(1, counter.components().stream().filter(component -> component.definitionId()
                .equals(SubcircuitSupport.definitionId(
                        StructuralCircuitFactory.structuralRegisterName(3, true, 0)))).count());
        Harness harness = harness(source, StructuralCircuitFactory.MODULO_COUNTER3, 3, false);
        harness.setReset(LogicState.ONE);
        harness.setReset(LogicState.ZERO);
        harness.setEnable(LogicState.ONE);
        for (int value = 1; value <= 8; value++) {
            harness.risingEdge();
            assertEquals(value & 7, harness.count());
            harness.fallingEdge();
        }
    }

    private static Harness harness(CircuitProject source, String circuitName, int width,
                                   boolean hasLoad) {
        CircuitProject project = new CircuitProject("counter-test");
        source.circuits().stream().filter(circuit -> !circuit.metadata().name().equals("main"))
                .forEach(project::putCircuit);
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", "counter harness"));
        ComponentInstance clk = add(main, "source.toggle", "CLK", -300, -100, ParameterValues.empty());
        ComponentInstance reset = add(main, "source.toggle", "RESET", -300, -40, ParameterValues.empty());
        ComponentInstance enable = add(main, "source.toggle", "ENABLE", -300, 20, ParameterValues.empty());
        ComponentInstance load = hasLoad
                ? add(main, "source.toggle", "LOAD", -300, 80, ParameterValues.empty()) : null;
        ComponentInstance data = hasLoad
                ? add(main, "system.input_port", "DATA", -300, 140,
                registry.require("system.input_port").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, width)) : null;
        ComponentInstance one = hasLoad
                ? add(main, "source.one", "ONE", -380, 140, ParameterValues.empty()) : null;
        ComponentInstance dut = sub(main, circuitName, "DUT", 0, 0);
        ComponentInstance count = add(main, "routing.bus_probe", "COUNT", 300, 0,
                registry.require("routing.bus_probe").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, width));
        wire(main, clk, "OUT", dut, "CLK");
        wire(main, reset, "OUT", dut, "RESET");
        wire(main, enable, "OUT", dut, "ENABLE");
        if (hasLoad) {
            wire(main, load, "OUT", dut, "LOAD");
            wire(main, one, "OUT", data, "SELECT");
            wire(main, one, "OUT", data, "READ");
            wire(main, data, "DATA", dut, "DATA");
        }
        wire(main, dut, "COUNT", count, "IN");
        project.putCircuit(main);
        var compiled = new CircuitCompiler(registry).compile(project, "main");
        return new Harness(new Simulation(compiled.circuit()),
                compiled.sourceMap().componentId(clk.id()).orElseThrow(),
                compiled.sourceMap().componentId(reset.id()).orElseThrow(),
                compiled.sourceMap().componentId(enable.id()).orElseThrow(),
                load == null ? -1 : compiled.sourceMap().componentId(load.id()).orElseThrow(),
                data == null ? -1 : compiled.sourceMap().componentId(data.id()).orElseThrow(),
                compiled.sourceMap().netOf(new PortReference(count.id(), "IN")).orElseThrow(), width);
    }

    private record Harness(Simulation simulation, int clk, int reset, int enable, int load,
                           int data, int countNet, int width) {
        void setReset(LogicState value) { simulation.setInput(reset, value); }
        void setEnable(LogicState value) { simulation.setInput(enable, value); }
        void setLoad(LogicState value) { simulation.setInput(load, value); }
        void setData(long value) { simulation.setInput(data, LogicVector.fromUnsignedLong(value, width)); }
        void risingEdge() { simulation.setInput(clk, LogicState.ONE); }
        void fallingEdge() { simulation.setInput(clk, LogicState.ZERO); }
        long count() { return simulation.readNet(countNet).toUnsignedLong().orElseThrow(); }
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
