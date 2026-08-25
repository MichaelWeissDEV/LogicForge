package dev.logicforge.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitMetadata;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.SubcircuitSupport;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.LibraryParameters;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.Simulation;
import org.junit.jupiter.api.Test;

class SubcircuitCompilationTest {

    @Test
    void parentBitMapsToTheSameBitOfAWholeWiredChildInterface() {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument child = byteInputChild("ByteInput");
        ComponentInstance childProbe = child.components().stream()
                .filter(component -> component.definitionId().equals("routing.bus_probe"))
                .findFirst().orElseThrow();
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance source = add(main, "routing.bus_constant", "DATA", 0, 0,
                busParameters(registry, "A5"));
        ComponentInstance instance = SubcircuitSupport.instantiate("ByteInput", new CircuitPoint(100, 0));
        main.addComponent(instance);
        main.addConnection(Connection.create(
                PortEndpoint.bit(new PortReference(source.id(), "OUT"), 5),
                PortEndpoint.bit(new PortReference(instance.id(), "DATA"), 2)));
        CircuitProject project = CircuitProject.of("bit-boundary", main);
        project.putCircuit(child);

        CompilationResult result = new CircuitCompiler(registry).compile(project, "main");
        int probeId = result.hierarchySourceMap().componentId(
                "main/" + instance.id() + "/" + childProbe.id()).orElseThrow();

        assertEquals(LogicVector.of("ZZZZZ1ZZ"),
                new Simulation(result.circuit()).readInput(probeId, 0));
    }

    @Test
    void parentRangeMapsLsbToLsbAcrossAWholeWiredChildInterface() {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument child = byteInputChild("ByteRange");
        ComponentInstance childProbe = child.components().stream()
                .filter(component -> component.definitionId().equals("routing.bus_probe"))
                .findFirst().orElseThrow();
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance source = add(main, "routing.bus_constant", "DATA", 0, 0,
                busParameters(registry, "A5"));
        ComponentInstance instance = SubcircuitSupport.instantiate("ByteRange", new CircuitPoint(100, 0));
        main.addComponent(instance);
        main.addConnection(Connection.create(
                PortEndpoint.range(new PortReference(source.id(), "OUT"), 7, 4),
                PortEndpoint.range(new PortReference(instance.id(), "DATA"), 3, 0)));
        CircuitProject project = CircuitProject.of("range-boundary", main);
        project.putCircuit(child);

        CompilationResult result = new CircuitCompiler(registry).compile(project, "main");
        int probeId = result.hierarchySourceMap().componentId(
                "main/" + instance.id() + "/" + childProbe.id()).orElseThrow();

        assertEquals(LogicVector.of("ZZZZ1010"),
                new Simulation(result.circuit()).readInput(probeId, 0));
    }

    @Test
    void hierarchyInOutPreservesMultipleDriversAndConflictResolution() {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument child = new CircuitDocument(new CircuitMetadata("SharedBus", ""));
        ParameterValues interfaceParameters = ParameterValues.defaultsOf(java.util.List.of(
                        SubcircuitSupport.INTERFACE_NAME, SubcircuitSupport.INTERFACE_WIDTH))
                .with(SubcircuitSupport.INTERFACE_NAME, "DATA")
                .with(SubcircuitSupport.INTERFACE_WIDTH, 8);
        ComponentInstance inout = add(child, SubcircuitSupport.INOUT_DEFINITION_ID,
                "DATA", 0, 0, interfaceParameters);
        ComponentInstance probe = add(child, "routing.bus_probe", "PROBE", 100, 0,
                registry.require("routing.bus_probe").definition().defaultParameters());
        wire(child, inout, "BUS", probe, "IN");

        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance high = add(main, "routing.bus_constant", "A", 0, 0,
                busParameters(registry, "A5"));
        ComponentInstance low = add(main, "routing.bus_constant", "B", 0, 80,
                busParameters(registry, "5A"));
        ComponentInstance instance = SubcircuitSupport.instantiate("SharedBus", new CircuitPoint(120, 0));
        main.addComponent(instance);
        wire(main, high, "OUT", instance, "DATA");
        wire(main, low, "OUT", instance, "DATA");
        CircuitProject project = CircuitProject.of("inout", main);
        project.putCircuit(child);

        CompilationResult result = new CircuitCompiler(registry).compile(project, "main");
        int probeId = result.hierarchySourceMap().componentId(
                "main/" + instance.id() + "/" + probe.id()).orElseThrow();

        assertEquals(LogicVector.repeat(LogicState.UNKNOWN, 8),
                new Simulation(result.circuit()).readInput(probeId, 0));
    }

    @Test
    void declaredBusInterfaceWidthIsValidatedAtParentBoundary() {
        CircuitDocument child = new CircuitDocument(new CircuitMetadata("ByteSink", ""));
        ParameterValues inputParameters = ParameterValues.defaultsOf(java.util.List.of(
                        SubcircuitSupport.INTERFACE_NAME, SubcircuitSupport.INTERFACE_WIDTH))
                .with(SubcircuitSupport.INTERFACE_NAME, "DATA")
                .with(SubcircuitSupport.INTERFACE_WIDTH, 8);
        add(child, SubcircuitSupport.INPUT_DEFINITION_ID, "DATA", 0, 0, inputParameters);
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance scalar = add(main, "source.toggle", "S", 0, 0, ParameterValues.empty());
        ComponentInstance instance = SubcircuitSupport.instantiate("ByteSink", new CircuitPoint(100, 0));
        main.addComponent(instance);
        wire(main, scalar, "OUT", instance, "DATA");
        CircuitProject project = CircuitProject.of("width", main);
        project.putCircuit(child);

        CircuitCompileException failure = assertThrows(CircuitCompileException.class,
                () -> new CircuitCompiler(ComponentRegistry.standard()).compile(project, "main"));
        assertTrue(failure.getMessage().contains("1 and 8 bits wide"));
    }

    @Test
    void reusableChildCircuitIsFlattenedTwiceIntoOneSimulation() {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument child = inverterChild();
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance a = add(main, "source.toggle", "A", 0, 0, ParameterValues.empty());
        ComponentInstance b = add(main, "source.toggle", "B", 0, 100, ParameterValues.empty());
        ComponentInstance first = SubcircuitSupport.instantiate("Inverter", new CircuitPoint(150, 0))
                .withLabel("N1");
        ComponentInstance second = SubcircuitSupport.instantiate("Inverter", new CircuitPoint(150, 100))
                .withLabel("N2");
        main.addComponent(first);
        main.addComponent(second);
        ComponentInstance ledA = add(main, "output.led", "LA", 300, 0, ParameterValues.empty());
        ComponentInstance ledB = add(main, "output.led", "LB", 300, 100, ParameterValues.empty());
        wire(main, a, "OUT", first, "A");
        wire(main, first, "Y", ledA, "IN");
        wire(main, b, "OUT", second, "A");
        wire(main, second, "Y", ledB, "IN");

        CircuitProject project = CircuitProject.of("hierarchy", main);
        project.putCircuit(child);
        CompilationResult result = new CircuitCompiler(registry).compile(project, "main");
        Simulation simulation = new Simulation(result.circuit());
        int aId = result.sourceMap().componentId(a.id()).orElseThrow();
        int ledANet = result.sourceMap().netOf(new PortReference(ledA.id(), "IN")).orElseThrow();
        int ledBNet = result.sourceMap().netOf(new PortReference(ledB.id(), "IN")).orElseThrow();

        assertEquals(6, result.circuit().componentCount(),
                "two interfaces and two parent instances flatten away; child gates are cloned");
        assertEquals(LogicVector.ONE, simulation.readNet(ledANet));
        assertEquals(LogicVector.ONE, simulation.readNet(ledBNet));
        simulation.setInput(aId, LogicState.ONE);
        assertEquals(LogicVector.ZERO, simulation.readNet(ledANet));
        assertEquals(LogicVector.ONE, simulation.readNet(ledBNet),
                "the second child instance has independent runtime wiring");
        assertTrue(result.sourceMap().netOfConnection(
                main.connections().iterator().next().id()).isPresent(),
                "root wire ids remain mapped after flattening");
        assertTrue(result.hierarchySourceMap().netId(
                "main/" + first.id() + ".A").isPresent());
        assertEquals(6, result.hierarchySourceMap().componentIdByPath().size(),
                "every root or nested primitive has a hierarchical runtime path");
    }

    private static CircuitDocument inverterChild() {
        CircuitDocument child = new CircuitDocument(new CircuitMetadata("Inverter", "Reusable NOT"));
        ParameterValues inputParameters = ParameterValues.defaultsOf(
                        java.util.List.of(SubcircuitSupport.INTERFACE_NAME,
                                SubcircuitSupport.INTERFACE_WIDTH))
                .with(SubcircuitSupport.INTERFACE_NAME, "A");
        ParameterValues outputParameters = ParameterValues.defaultsOf(
                        java.util.List.of(SubcircuitSupport.INTERFACE_NAME,
                                SubcircuitSupport.INTERFACE_WIDTH))
                .with(SubcircuitSupport.INTERFACE_NAME, "Y");
        ComponentInstance input = add(child, SubcircuitSupport.INPUT_DEFINITION_ID,
                "A_PORT", 0, 0, inputParameters);
        ComponentInstance not = add(child, "logic.not", "NOT", 100, 0, ParameterValues.empty());
        ComponentInstance output = add(child, SubcircuitSupport.OUTPUT_DEFINITION_ID,
                "Y_PORT", 200, 0, outputParameters);
        wire(child, input, "OUT", not, "A");
        wire(child, not, "Y", output, "IN");
        return child;
    }

    private static CircuitDocument byteInputChild(String name) {
        CircuitDocument child = new CircuitDocument(new CircuitMetadata(name, ""));
        ParameterValues inputParameters = ParameterValues.defaultsOf(java.util.List.of(
                        SubcircuitSupport.INTERFACE_NAME, SubcircuitSupport.INTERFACE_WIDTH))
                .with(SubcircuitSupport.INTERFACE_NAME, "DATA")
                .with(SubcircuitSupport.INTERFACE_WIDTH, 8);
        ComponentInstance input = add(child, SubcircuitSupport.INPUT_DEFINITION_ID,
                "DATA", 0, 0, inputParameters);
        ComponentInstance probe = add(child, "routing.bus_probe", "PROBE", 100, 0,
                ComponentRegistry.standard().require("routing.bus_probe").definition().defaultParameters());
        wire(child, input, "OUT", probe, "IN");
        return child;
    }

    private static ParameterValues busParameters(ComponentRegistry registry, String value) {
        return registry.require("routing.bus_constant").definition().defaultParameters()
                .with(LibraryParameters.WIDTH, 8)
                .with(LibraryParameters.BUS_CONSTANT_VALUE, value);
    }

    private static ComponentInstance add(CircuitDocument document, String type, String label,
                                         double x, double y, ParameterValues parameters) {
        ComponentInstance component = ComponentInstance.create(
                type, new CircuitPoint(x, y), parameters).withLabel(label);
        document.addComponent(component);
        return component;
    }

    private static void wire(CircuitDocument document, ComponentInstance from, String fromPort,
                             ComponentInstance to, String toPort) {
        document.addConnection(Connection.create(new PortReference(from.id(), fromPort),
                new PortReference(to.id(), toPort)));
    }
}
