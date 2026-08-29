package dev.logicforge.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.ElectricalEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.chip.StandardChipLibrary;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.Simulation;
import org.junit.jupiter.api.Test;

/** Exercises physical pins through the actual document, compiler and simulation pipeline. */
class ChipCompilationIntegrationTest {

    @Test
    void physical74hc00PinsDriveTheExpandedNandUnit() {
        ComponentRegistry components = ComponentRegistry.standard();
        CircuitDocument document = new CircuitDocument();
        ComponentInstance a = add(document, components, "source.toggle", "A", 0);
        ComponentInstance b = add(document, components, "source.toggle", "B", 60);
        ComponentInstance probe = add(document, components, "output.probe", "Y", 180);
        ChipInstance chip = ChipInstance.create("74HC00", new CircuitPoint(100, 0), "U1");
        document.addChip(chip);

        wire(document, new ElectricalEndpoint.ComponentEndpoint(PortEndpoint.whole(new PortReference(a.id(), "OUT"))),
                new ElectricalEndpoint.ChipPinEndpoint(chip.id(), 1));
        wire(document, new ElectricalEndpoint.ComponentEndpoint(PortEndpoint.whole(new PortReference(b.id(), "OUT"))),
                new ElectricalEndpoint.ChipPinEndpoint(chip.id(), 2));
        wire(document, new ElectricalEndpoint.ChipPinEndpoint(chip.id(), 3),
                new ElectricalEndpoint.ComponentEndpoint(PortEndpoint.whole(new PortReference(probe.id(), "IN"))));

        CompilationResult result = new CircuitCompiler(components, StandardChipLibrary.create())
                .compile(document);
        assertEquals(4, result.chipSourceMap().logicalUnitsOf(chip.id()).size());
        assertTrue(result.chipSourceMap().physicalChipOf(
                result.chipSourceMap().logicalUnitsOf(chip.id()).getFirst()).isPresent());
        assertTrue(result.chipSourceMap().logicalEndpointForPin(chip.id(), 1).isPresent());
        assertTrue(result.chipSourceMap().netForPin(chip.id(), 3).isPresent());

        Simulation simulation = new Simulation(result.circuit());
        int aId = result.sourceMap().componentId(a.id()).orElseThrow();
        int bId = result.sourceMap().componentId(b.id()).orElseThrow();
        int probeId = result.sourceMap().componentId(probe.id()).orElseThrow();
        assertNand(simulation, aId, bId, probeId, LogicState.ZERO, LogicState.ZERO, LogicState.ONE);
        assertNand(simulation, aId, bId, probeId, LogicState.ZERO, LogicState.ONE, LogicState.ONE);
        assertNand(simulation, aId, bId, probeId, LogicState.ONE, LogicState.ZERO, LogicState.ONE);
        assertNand(simulation, aId, bId, probeId, LogicState.ONE, LogicState.ONE, LogicState.ZERO);
    }

    private static ComponentInstance add(CircuitDocument document, ComponentRegistry components,
                                         String definitionId, String label, double x) {
        ComponentInstance instance = ComponentInstance.create(definitionId, new CircuitPoint(x, 0),
                components.require(definitionId).definition().defaultParameters()).withLabel(label);
        document.addComponent(instance);
        return instance;
    }

    private static void wire(CircuitDocument document, ElectricalEndpoint from, ElectricalEndpoint to) {
        document.addConnection(Connection.create(from, to));
    }

    private static void assertNand(Simulation simulation, int a, int b, int probe,
                                   LogicState aValue, LogicState bValue, LogicState expected) {
        simulation.setInput(a, aValue);
        simulation.setInput(b, bValue);
        assertEquals(LogicVector.single(expected), simulation.readInput(probe, 0));
    }
}
