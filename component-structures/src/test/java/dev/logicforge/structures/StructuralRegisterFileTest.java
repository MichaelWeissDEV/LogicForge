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

class StructuralRegisterFileTest {

    @Test
    void registerFileWritesOneOfEightRegistersAndReadsTwoPortsIndependently() {
        CircuitProject source = StructuralCircuitFactory.registerFile8x8Project();
        CircuitDocument registerFile = source.circuit(
                StructuralCircuitFactory.REGISTER_FILE_8X8).orElseThrow();
        assertEquals(8, registerFile.components().stream().filter(component ->
                component.definitionId().equals(SubcircuitSupport.definitionId(
                        StructuralCircuitFactory.REGISTER8))).count());
        assertFalse(source.circuits().stream().flatMap(circuit -> circuit.components().stream())
                .anyMatch(component -> component.definitionId().equals("memory.register_file")));

        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitProject project = new CircuitProject("register-file-test");
        source.circuits().stream().filter(circuit -> !circuit.metadata().name().equals("main"))
                .forEach(project::putCircuit);
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", "RF harness"));
        ComponentInstance one = add(main, "source.one", "ONE", -400, -260, ParameterValues.empty());
        ComponentInstance rdA = busInput(main, registry, one, "RD_A", 3, -400, -180);
        ComponentInstance rdB = busInput(main, registry, one, "RD_B", 3, -400, -120);
        ComponentInstance wrAddr = busInput(main, registry, one, "WR_ADDR", 3, -400, -60);
        ComponentInstance wrData = busInput(main, registry, one, "WR_DATA", 8, -400, 0);
        ComponentInstance wrEn = add(main, "source.toggle", "WR_EN", -400, 60, ParameterValues.empty());
        ComponentInstance clk = add(main, "source.toggle", "CLK", -400, 120, ParameterValues.empty());
        ComponentInstance dut = sub(main, StructuralCircuitFactory.REGISTER_FILE_8X8,
                "REGISTER_FILE", 0, 0);
        ComponentInstance outA = add(main, "routing.bus_probe", "OUT_A", 400, -60,
                registry.require("routing.bus_probe").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8));
        ComponentInstance outB = add(main, "routing.bus_probe", "OUT_B", 400, 60,
                registry.require("routing.bus_probe").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8));
        wire(main, rdA, "DATA", dut, "RD_ADDR_A");
        wire(main, rdB, "DATA", dut, "RD_ADDR_B");
        wire(main, wrAddr, "DATA", dut, "WR_ADDR");
        wire(main, wrData, "DATA", dut, "WR_DATA");
        wire(main, wrEn, "OUT", dut, "WR_EN");
        wire(main, clk, "OUT", dut, "CLK");
        wire(main, dut, "RD_DATA_A", outA, "IN");
        wire(main, dut, "RD_DATA_B", outB, "IN");
        project.putCircuit(main);
        var compiled = new CircuitCompiler(registry).compile(project, "main");
        Simulation simulation = new Simulation(compiled.circuit());
        int rdAId = compiled.sourceMap().componentId(rdA.id()).orElseThrow();
        int rdBId = compiled.sourceMap().componentId(rdB.id()).orElseThrow();
        int wrAddrId = compiled.sourceMap().componentId(wrAddr.id()).orElseThrow();
        int wrDataId = compiled.sourceMap().componentId(wrData.id()).orElseThrow();
        int wrEnId = compiled.sourceMap().componentId(wrEn.id()).orElseThrow();
        int clkId = compiled.sourceMap().componentId(clk.id()).orElseThrow();
        int outANet = compiled.sourceMap().netOf(new PortReference(outA.id(), "IN")).orElseThrow();
        int outBNet = compiled.sourceMap().netOf(new PortReference(outB.id(), "IN")).orElseThrow();

        write(simulation, wrAddrId, wrDataId, wrEnId, clkId, 2, 0xa5);
        write(simulation, wrAddrId, wrDataId, wrEnId, clkId, 5, 0x3c);
        simulation.setInput(wrEnId, LogicState.ZERO);
        simulation.setInput(rdAId, LogicVector.fromUnsignedLong(2, 3));
        simulation.setInput(rdBId, LogicVector.fromUnsignedLong(5, 3));
        assertEquals(LogicVector.fromUnsignedLong(0xa5, 8), simulation.readNet(outANet));
        assertEquals(LogicVector.fromUnsignedLong(0x3c, 8), simulation.readNet(outBNet));
    }

    private static void write(Simulation simulation, int addressId, int dataId, int enableId,
                              int clkId, int address, int data) {
        simulation.setInput(clkId, LogicState.ZERO);
        simulation.setInput(addressId, LogicVector.fromUnsignedLong(address, 3));
        simulation.setInput(dataId, LogicVector.fromUnsignedLong(data, 8));
        simulation.setInput(enableId, LogicState.ONE);
        simulation.setInput(clkId, LogicState.ONE);
    }

    private static ComponentInstance busInput(CircuitDocument main, ComponentRegistry registry,
                                              ComponentInstance one, String label, int width,
                                              double x, double y) {
        ComponentInstance input = add(main, "system.input_port", label, x, y,
                registry.require("system.input_port").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, width));
        wire(main, one, "OUT", input, "SELECT");
        wire(main, one, "OUT", input, "READ");
        return input;
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
