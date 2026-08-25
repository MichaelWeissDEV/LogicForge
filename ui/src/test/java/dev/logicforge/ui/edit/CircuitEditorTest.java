package dev.logicforge.ui.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitMetadata;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.PortDisplayMode;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.SubcircuitSupport;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.Rotation;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.LibraryParameters;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.SimulationStatus;
import dev.logicforge.ui.command.AddComponentCommand;
import dev.logicforge.ui.command.ChangeParameterCommand;
import dev.logicforge.ui.command.ConnectCommand;
import dev.logicforge.ui.command.MoveComponentsCommand;
import dev.logicforge.ui.command.PasteCommand;
import dev.logicforge.ui.command.RemoveElementsCommand;
import dev.logicforge.ui.command.RotateComponentsCommand;
import dev.logicforge.ui.command.SetPortDisplayModeCommand;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The editor's behaviour, exercised without any JavaFX: commands, undo, recompilation and
 * the live simulation values the canvas draws.
 */
class CircuitEditorTest {

    private final CircuitEditor editor = new CircuitEditor(ComponentRegistry.standard());

    @Test
    void switchingActiveCircuitClearsSelectionWithoutMutatingDocuments() {
        CircuitProject project = CircuitProject.empty("navigation");
        CircuitDocument child = project.addCircuit("ALU8");
        ComponentInstance gate = ComponentInstance.create("logic.not",
                new CircuitPoint(0, 0), ParameterValues.empty());
        project.mainCircuit().addComponent(gate);
        CircuitDocument mainBefore = project.mainCircuit().copy();
        CircuitDocument childBefore = child.copy();
        editor.setProject(project, false);
        editor.selection().selectComponent(gate.id());

        editor.openCircuit("ALU8");

        assertEquals("ALU8", editor.activeCircuitName());
        assertTrue(editor.selection().isEmpty());
        assertTrue(project.mainCircuit().structurallyEquals(mainBefore));
        assertTrue(child.structurallyEquals(childBefore));
    }

    @Test
    void childTopologyEditRecompilesMainAndPreservesRootInputState() {
        CircuitDocument child = inverterChild("Inverter");
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance source = ComponentInstance.create("source.toggle",
                new CircuitPoint(0, 0), ParameterValues.empty());
        ComponentInstance instance = SubcircuitSupport.instantiate(
                "Inverter", new CircuitPoint(120, 0));
        ComponentInstance led = ComponentInstance.create("output.led",
                new CircuitPoint(240, 0), ParameterValues.empty());
        main.addComponent(source);
        main.addComponent(instance);
        main.addComponent(led);
        main.addConnection(Connection.create(new PortReference(source.id(), "OUT"),
                new PortReference(instance.id(), "A")));
        main.addConnection(Connection.create(new PortReference(instance.id(), "Y"),
                new PortReference(led.id(), "IN")));
        CircuitProject project = CircuitProject.of("child edit", main);
        project.putCircuit(child);
        editor.setProject(project, false);
        editor.toggleInput(source.id());
        assertEquals(LogicVector.ZERO, editor.valueAt(new PortReference(led.id(), "IN")).orElseThrow());
        var before = editor.simulation().orElseThrow();

        editor.openSubcircuit(instance);
        child.addComponent(ComponentInstance.create("source.zero",
                new CircuitPoint(400, 200), ParameterValues.empty()));

        assertFalse(before == editor.simulation().orElseThrow());
        assertEquals(LogicVector.ONE, editor.inputValueOf(source.id()).map(LogicVector::single)
                .orElseThrow());
        assertEquals(LogicVector.ZERO, editor.valueAt(new PortReference(led.id(), "IN")).orElseThrow());
        assertTrue(editor.isDirty());
        assertEquals("Inverter", editor.activeCircuitName());
        editor.navigateBack();
        assertEquals("main", editor.activeCircuitName());
    }

    @Test
    void buildingAndRunningTheClassicSwitchAndGateCircuit() {
        ComponentInstance switchA = add("source.toggle", 0, 0);
        ComponentInstance switchB = add("source.toggle", 0, 100);
        ComponentInstance gate = add("logic.and", 200, 50);
        ComponentInstance led = add("output.led", 400, 50);
        connect(switchA, "OUT", gate, "IN0");
        connect(switchB, "OUT", gate, "IN1");
        connect(gate, "OUT", led, "IN");

        assertEquals(LogicVector.ZERO, valueAt(led, "IN"));

        editor.toggleInput(switchA.id());
        assertEquals(LogicVector.ZERO, valueAt(led, "IN"));

        editor.toggleInput(switchB.id());
        assertEquals(LogicVector.ONE, valueAt(led, "IN"), "the LED lights up when both switches are on");

        editor.toggleInput(switchA.id());
        assertEquals(LogicVector.ZERO, valueAt(led, "IN"));
        assertEquals(SimulationStatus.STABLE, editor.status());
    }

    @Test
    void anUnconnectedInputShowsAsUnknown() {
        ComponentInstance gate = add("logic.and", 0, 0);
        ComponentInstance led = add("output.led", 200, 0);
        connect(gate, "OUT", led, "IN");

        assertEquals(LogicVector.UNKNOWN, valueAt(led, "IN"));
        assertTrue(editor.issues().stream()
                .anyMatch(issue -> issue.message().contains("no driver")));
    }

    @Test
    void switchStatesSurviveEditingTheCircuit() {
        ComponentInstance switchA = add("source.toggle", 0, 0);
        ComponentInstance led = add("output.led", 200, 0);
        connect(switchA, "OUT", led, "IN");
        editor.toggleInput(switchA.id());
        assertEquals(LogicVector.ONE, valueAt(led, "IN"));

        add("logic.not", 400, 400); // an unrelated edit forces a recompile

        assertEquals(LogicVector.ONE, valueAt(led, "IN"), "adding a gate must not reset the switches");
    }

    @Test
    void movingAComponentDoesNotDisturbTheSimulation() {
        ComponentInstance switchA = add("source.toggle", 0, 0);
        ComponentInstance led = add("output.led", 200, 0);
        connect(switchA, "OUT", led, "IN");
        editor.toggleInput(switchA.id());

        var simulationBefore = editor.simulation().orElseThrow();
        editor.execute(new MoveComponentsCommand(editor.document(), List.of(led),
                List.of(led.movedBy(80, 40))));

        assertTrue(editor.simulation().orElseThrow() == simulationBefore,
                "moving is not a topology change, so nothing is recompiled");
        assertEquals(LogicVector.ONE, valueAt(led, "IN"));
    }

    @Test
    void compactExpandedIsUndoableAndNeverRecompilesOrDropsWiring() {
        ComponentInstance source = add("routing.bus_constant", 0, 0);
        ComponentInstance register = add("sequential.register", 200, 0);
        connect(source, "OUT", register, "DATA");
        var simulationBefore = editor.simulation().orElseThrow();

        editor.execute(new SetPortDisplayModeCommand(editor.document(), register,
                PortDisplayMode.EXPANDED));

        assertEquals(PortDisplayMode.EXPANDED,
                editor.document().requireComponent(register.id()).portDisplayMode());
        assertTrue(editor.simulation().orElseThrow() == simulationBefore);
        assertEquals(1, editor.document().connectionCount());

        editor.undo();
        assertEquals(PortDisplayMode.COMPACT,
                editor.document().requireComponent(register.id()).portDisplayMode());
        assertTrue(editor.simulation().orElseThrow() == simulationBefore);
        assertEquals(1, editor.document().connectionCount());
    }

    @Test
    void restoredRegisterStateImmediatelyRedrivesItsOutputAfterRecompile() {
        ParameterValues busValue = editor.definition("routing.bus_constant").orElseThrow()
                .defaultParameters().with(LibraryParameters.WIDTH, 8)
                .with(LibraryParameters.BUS_CONSTANT_VALUE, "A5");
        ComponentInstance data = add("routing.bus_constant", 0, 0, busValue);
        ComponentInstance load = add("source.one", 0, 80);
        ComponentInstance clock = add("source.toggle", 0, 160);
        ComponentInstance register = add("sequential.register", 240, 80);
        connect(data, "OUT", register, "DATA");
        connect(load, "OUT", register, "LOAD");
        connect(clock, "OUT", register, "CLK");

        editor.toggleInput(clock.id());
        assertEquals(LogicVector.of("10100101"), valueAt(register, "Q"));

        add("logic.not", 500, 300);

        assertEquals(LogicVector.of("10100101"), valueAt(register, "Q"),
                "restored state must immediately drive the fresh simulation");
    }

    @Test
    void romCanBeInspectedAndEditedThroughTheSharedMemoryApi() {
        ParameterValues parameters = editor.definition("memory.rom").orElseThrow()
                .defaultParameters().with(LibraryParameters.ADDRESS_WIDTH, 2)
                .with(LibraryParameters.WIDTH, 8)
                .with(LibraryParameters.ROM_CONTENTS, "12,34,56,78");
        ComponentInstance rom = add("memory.rom", 0, 0, parameters);

        assertEquals(LogicVector.fromUnsignedLong(0x34, 8),
                editor.memorySnapshot(rom.id()).orElseThrow().wordAt(1));

        editor.writeMemoryWord(rom.id(), 1, LogicVector.fromUnsignedLong(0xA5, 8));

        assertEquals(LogicVector.fromUnsignedLong(0xA5, 8),
                editor.memorySnapshot(rom.id()).orElseThrow().wordAt(1));
        assertTrue(editor.document().requireComponent(rom.id()).parameters()
                .get(LibraryParameters.ROM_CONTENTS).contains("A5"));
        editor.undo();
        assertEquals(LogicVector.fromUnsignedLong(0x34, 8),
                editor.memorySnapshot(rom.id()).orElseThrow().wordAt(1));
    }

    @Test
    void restoredCounterImmediatelyRedrivesItsCountAfterRecompile() {
        ComponentInstance clock = add("source.toggle", 0, 0);
        ComponentInstance enable = add("source.one", 0, 80);
        ComponentInstance reset = add("source.zero", 0, 160);
        ComponentInstance counter = add("sequential.counter_up", 220, 80);
        connect(clock, "OUT", counter, "CLK");
        connect(enable, "OUT", counter, "ENABLE");
        connect(reset, "OUT", counter, "RESET");
        editor.toggleInput(clock.id());
        assertEquals(LogicVector.fromUnsignedLong(1, 8), valueAt(counter, "COUNT"));

        add("logic.not", 500, 300);

        assertEquals(LogicVector.fromUnsignedLong(1, 8), valueAt(counter, "COUNT"));
    }

    @Test
    void restoredRamImmediatelyRedrivesCurrentlySelectedWord() {
        ParameterValues addressParameters = editor.definition("routing.bus_constant").orElseThrow()
                .defaultParameters().with(LibraryParameters.WIDTH, 2)
                .with(LibraryParameters.BUS_CONSTANT_VALUE, "0");
        ParameterValues ramParameters = editor.definition("memory.ram").orElseThrow()
                .defaultParameters().with(LibraryParameters.ADDRESS_WIDTH, 2)
                .with(LibraryParameters.WIDTH, 8);
        ComponentInstance address = add("routing.bus_constant", 0, 0, addressParameters);
        ComponentInstance we = add("source.zero", 0, 80);
        ComponentInstance oe = add("source.one", 0, 160);
        ComponentInstance cs = add("source.one", 0, 240);
        ComponentInstance ram = add("memory.ram", 240, 100, ramParameters);
        connect(address, "OUT", ram, "ADDRESS");
        connect(we, "OUT", ram, "WE");
        connect(oe, "OUT", ram, "OE");
        connect(cs, "OUT", ram, "CS");
        editor.writeMemoryWord(ram.id(), 0, LogicVector.fromUnsignedLong(0xA5, 8));
        assertEquals(LogicVector.fromUnsignedLong(0xA5, 8), valueAt(ram, "DATA"));

        add("logic.not", 500, 300);

        assertEquals(LogicVector.fromUnsignedLong(0xA5, 8), valueAt(ram, "DATA"));
    }

    @Test
    void analyzerCanExtractBusBitWithoutCreatingASimulationNet() {
        ParameterValues value = editor.definition("routing.bus_constant").orElseThrow()
                .defaultParameters().with(LibraryParameters.WIDTH, 8)
                .with(LibraryParameters.BUS_CONSTANT_VALUE, "A5");
        ComponentInstance source = add("routing.bus_constant", 0, 0, value);
        ComponentInstance probe = add("routing.bus_probe", 200, 0);
        connect(source, "OUT", probe, "IN");
        int netCount = editor.compilation().orElseThrow().circuit().netCount();
        PortEndpoint bit = PortEndpoint.bit(new PortReference(source.id(), "OUT"), 2);
        LogicAnalyzerController analyzer = new LogicAnalyzerController(editor);

        analyzer.addSignal(bit, "DATA[2]");

        assertEquals(LogicVector.ONE, analyzer.traceFor(bit).orElseThrow()
                .transitions().get(0).value());
        assertEquals(netCount, editor.compilation().orElseThrow().circuit().netCount());
    }

    @Test
    void oneDragIsOneUndoStep() {
        ComponentInstance gate = add("logic.and", 100, 100);
        ComponentInstance dragged = gate.movedBy(64, 32);

        editor.execute(new MoveComponentsCommand(editor.document(), List.of(gate), List.of(dragged)));

        assertEquals(new CircuitPoint(164, 132), editor.document().requireComponent(gate.id()).position());
        editor.undo();
        assertEquals(new CircuitPoint(100, 100), editor.document().requireComponent(gate.id()).position());
        editor.redo();
        assertEquals(new CircuitPoint(164, 132), editor.document().requireComponent(gate.id()).position());
    }

    @Test
    void rotatingKeepsTheWiresAttached() {
        ComponentInstance switchA = add("source.toggle", 0, 0);
        ComponentInstance gate = add("logic.and", 200, 0);
        connect(switchA, "OUT", gate, "IN0");
        editor.toggleInput(switchA.id());

        editor.execute(new RotateComponentsCommand(editor.document(), List.of(gate),
                List.of(gate.withRotation(Rotation.DEG_90))));

        assertEquals(Rotation.DEG_90, editor.document().requireComponent(gate.id()).rotation());
        assertEquals(1, editor.document().connectionCount(), "rotating never drops a wire");
        assertEquals(LogicVector.ONE, valueAt(gate, "IN0"));
    }

    @Test
    void deletingAComponentRemovesItsWiresAndUndoBringsThemBack() {
        ComponentInstance switchA = add("source.toggle", 0, 0);
        ComponentInstance gate = add("logic.and", 200, 0);
        connect(switchA, "OUT", gate, "IN0");

        editor.execute(new RemoveElementsCommand(editor.document(), Set.of(gate.id()), Set.of()));

        assertEquals(1, editor.document().componentCount());
        assertEquals(0, editor.document().connectionCount());

        editor.undo();

        assertEquals(2, editor.document().componentCount());
        assertEquals(1, editor.document().connectionCount());
        assertTrue(editor.document().isConnected(new PortReference(switchA.id(), "OUT"),
                new PortReference(gate.id(), "IN0")));
    }

    @Test
    void narrowingAGateRemovesTheWiresToVanishedPortsAndUndoRestoresThem() {
        ComponentInstance switchA = add("source.toggle", 0, 0);
        ComponentInstance gate = add("logic.and", 200, 0,
                ParameterValues.empty().with(LibraryParameters.INPUT_COUNT, 4));
        connect(switchA, "OUT", gate, "IN0");
        connect(switchA, "OUT", gate, "IN3");

        editor.execute(new ChangeParameterCommand(editor.document(),
                editor.definition("logic.and").orElseThrow(),
                editor.document().requireComponent(gate.id()), LibraryParameters.INPUT_COUNT.key(), 2));

        assertEquals(1, editor.document().connectionCount(), "the wire to IN3 went with the port");
        assertTrue(editor.document().isConnected(new PortReference(switchA.id(), "OUT"),
                new PortReference(gate.id(), "IN0")), "the wire to IN0 stayed");

        editor.undo();

        assertEquals(2, editor.document().connectionCount(), "undo restores port and wire together");
        assertEquals(4, editor.document().requireComponent(gate.id()).parameters()
                .getInt(LibraryParameters.INPUT_COUNT));
    }

    @Test
    void wideningAGateKeepsEveryExistingWire() {
        ComponentInstance switchA = add("source.toggle", 0, 0);
        ComponentInstance gate = add("logic.and", 200, 0);
        connect(switchA, "OUT", gate, "IN0");
        connect(switchA, "OUT", gate, "IN1");
        editor.toggleInput(switchA.id());

        editor.execute(new ChangeParameterCommand(editor.document(),
                editor.definition("logic.and").orElseThrow(),
                editor.document().requireComponent(gate.id()), LibraryParameters.INPUT_COUNT.key(), 6));

        assertEquals(2, editor.document().connectionCount());
        assertEquals(LogicVector.UNKNOWN, valueAt(gate, "OUT"),
                "the four new inputs are floating, so the result is unknown");
    }

    @Test
    void copyingAFragmentTakesItsInternalWiresAlong() {
        ComponentInstance switchA = add("source.toggle", 0, 0);
        ComponentInstance gate = add("logic.and", 200, 0);
        ComponentInstance led = add("output.led", 400, 0);
        connect(switchA, "OUT", gate, "IN0");
        connect(gate, "OUT", led, "IN");

        CircuitClipboard.Fragment copied = editor.clipboard()
                .copy(editor.document(), List.of(switchA.id(), gate.id()));

        assertEquals(2, copied.components().size());
        assertEquals(1, copied.connections().size(), "only the wire between the copied parts");

        CircuitClipboard.Fragment pasted = editor.clipboard().prepareForPaste(64, 64);
        editor.execute(new PasteCommand(editor.document(), pasted.components(), pasted.connections()));

        assertEquals(5, editor.document().componentCount());
        assertEquals(3, editor.document().connectionCount());
        assertFalse(pasted.components().get(0).id().equals(switchA.id()), "the copy gets its own id");
        assertEquals(new CircuitPoint(64, 64), pasted.components().get(0).position());

        editor.undo();
        assertEquals(3, editor.document().componentCount());
    }

    @Test
    void aPastedFragmentSimulatesIndependently() {
        ComponentInstance switchA = add("source.toggle", 0, 0);
        ComponentInstance led = add("output.led", 200, 0);
        connect(switchA, "OUT", led, "IN");
        editor.toggleInput(switchA.id());

        editor.clipboard().copy(editor.document(), List.of(switchA.id(), led.id()));
        CircuitClipboard.Fragment pasted = editor.clipboard().prepareForPaste(0, 200);
        editor.execute(new PasteCommand(editor.document(), pasted.components(), pasted.connections()));

        ComponentInstance copiedLed = pasted.components().get(1);
        assertEquals(LogicVector.ONE, valueAt(led, "IN"), "the original keeps its state");
        assertEquals(LogicVector.ZERO, valueAt(copiedLed, "IN"), "the copy starts from its own default");
    }

    @Test
    void driverConflictsAreVisibleToTheEditor() {
        ComponentInstance high = add("source.one", 0, 0);
        ComponentInstance low = add("source.zero", 0, 100);
        ComponentInstance probe = add("output.probe", 200, 50);
        connect(high, "OUT", probe, "IN");
        connect(low, "OUT", probe, "IN");

        assertEquals(LogicVector.UNKNOWN, valueAt(probe, "IN"));
        int net = editor.netOf(new PortReference(probe.id(), "IN")).orElseThrow();
        assertTrue(editor.hasDriverConflict(net),
                "a conflict is a runtime state of the net, reported by the simulation");
        assertEquals(2, editor.compilation().orElseThrow().circuit().net(net).driverCount());
    }

    @Test
    void aRunawayCircuitReportsAnOscillationInsteadOfHanging() {
        // Two inverters and an XOR fed from a switch settle; a direct inverter loop
        // settles on X. Both must leave the editor responsive.
        ComponentInstance inverter = add("logic.not", 0, 0);
        editor.execute(new ConnectCommand(editor.document(), Connection.create(
                new PortReference(inverter.id(), "Y"), new PortReference(inverter.id(), "A"))));

        assertEquals(LogicVector.UNKNOWN, valueAt(inverter, "Y"));
        assertEquals(SimulationStatus.STABLE, editor.status());
    }

    @Test
    void pausingStopsPropagationUntilStepped() {
        ComponentInstance switchA = add("source.toggle", 0, 0);
        ComponentInstance inverter = add("logic.not", 200, 0);
        connect(switchA, "OUT", inverter, "A");

        editor.setRunning(false);
        editor.toggleInput(switchA.id());
        assertEquals(LogicVector.ZERO, valueAt(switchA, "OUT"), "nothing propagated while paused");

        editor.step();
        assertEquals(LogicVector.ONE, valueAt(switchA, "OUT"));

        editor.setRunning(true);
        assertEquals(LogicVector.ZERO, valueAt(inverter, "Y"));
    }

    @Test
    void resetReturnsSwitchesToTheirConfiguredValue() {
        ComponentInstance switchA = add("source.toggle", 0, 0);
        editor.toggleInput(switchA.id());
        assertEquals(LogicVector.ONE, valueAt(switchA, "OUT"));

        editor.resetSimulation();

        assertEquals(LogicVector.ZERO, valueAt(switchA, "OUT"));
    }

    @Test
    void editingMarksTheProjectDirtyButFlippingASwitchDoesNot() {
        assertFalse(editor.isDirty());
        ComponentInstance switchA = add("source.toggle", 0, 0);
        assertTrue(editor.isDirty(), "placing a component changes the project");

        editor.markSaved();
        editor.toggleInput(switchA.id());

        assertFalse(editor.isDirty(), "a simulation input is not a document change");
    }

    @Test
    void unknownComponentsLeaveTheEditorWithAnErrorInsteadOfASimulation() {
        editor.execute(new AddComponentCommand(editor.document(), ComponentInstance.create(
                "logic.imaginary", new CircuitPoint(0, 0), ParameterValues.empty())));

        assertTrue(editor.simulation().isEmpty());
        assertTrue(editor.compileError().orElse("").contains("logic.imaginary"));

        editor.undo();

        assertTrue(editor.simulation().isPresent(), "removing the offender makes it runnable again");
    }

    @Test
    void pausedStateIsPreservedAcrossRecompile() {
        ComponentInstance sw = add("source.toggle", 0, 0);
        ComponentInstance led = add("output.led", 200, 0);
        connect(sw, "OUT", led, "IN");
        // Pause the editor
        editor.setRunning(false);
        assertFalse(editor.isRunning(), "editor should be paused");
        // Trigger recompile by adding a component
        add("logic.not", 100, 50);
        assertFalse(editor.isRunning(), "editor must still be paused after recompile");
    }

    @Test
    void runningStateIsPreservedAcrossRecompile() {
        ComponentInstance sw = add("source.toggle", 0, 0);
        editor.setRunning(true);
        assertTrue(editor.isRunning());
        // Trigger recompile
        add("logic.not", 100, 50);
        assertTrue(editor.isRunning(), "editor must still be running after recompile");
    }

    // ------------------------------------------------------------------

    private static CircuitDocument inverterChild(String name) {
        CircuitDocument child = new CircuitDocument(new CircuitMetadata(name, ""));
        ParameterValues inputParameters = ParameterValues.defaultsOf(List.of(
                        SubcircuitSupport.INTERFACE_NAME, SubcircuitSupport.INTERFACE_WIDTH))
                .with(SubcircuitSupport.INTERFACE_NAME, "A");
        ParameterValues outputParameters = ParameterValues.defaultsOf(List.of(
                        SubcircuitSupport.INTERFACE_NAME, SubcircuitSupport.INTERFACE_WIDTH))
                .with(SubcircuitSupport.INTERFACE_NAME, "Y");
        ComponentInstance input = ComponentInstance.create(SubcircuitSupport.INPUT_DEFINITION_ID,
                new CircuitPoint(0, 0), inputParameters);
        ComponentInstance not = ComponentInstance.create("logic.not",
                new CircuitPoint(100, 0), ParameterValues.empty());
        ComponentInstance output = ComponentInstance.create(SubcircuitSupport.OUTPUT_DEFINITION_ID,
                new CircuitPoint(200, 0), outputParameters);
        child.addComponent(input);
        child.addComponent(not);
        child.addComponent(output);
        child.addConnection(Connection.create(new PortReference(input.id(), "OUT"),
                new PortReference(not.id(), "A")));
        child.addConnection(Connection.create(new PortReference(not.id(), "Y"),
                new PortReference(output.id(), "IN")));
        return child;
    }

    private ComponentInstance add(String definitionId, double x, double y) {
        return add(definitionId, x, y,
                editor.definition(definitionId).orElseThrow().defaultParameters());
    }

    private ComponentInstance add(String definitionId, double x, double y, ParameterValues parameters) {
        ComponentInstance instance = ComponentInstance.create(definitionId, new CircuitPoint(x, y),
                parameters);
        editor.execute(new AddComponentCommand(editor.document(), instance));
        return instance;
    }

    private void connect(ComponentInstance from, String fromPort, ComponentInstance to, String toPort) {
        editor.execute(new ConnectCommand(editor.document(), Connection.create(
                new PortReference(from.id(), fromPort), new PortReference(to.id(), toPort))));
    }

    private LogicVector valueAt(ComponentInstance instance, String portName) {
        return editor.valueAt(new PortReference(instance.id(), portName)).orElseThrow();
    }

    @Test
    void onlyUserDrivenComponentsRespondToClicks() {
        ComponentInstance gate = add("logic.and", 0, 0);
        ComponentInstance switchA = add("source.toggle", 200, 0);

        assertFalse(editor.isUserInput(gate.id()));
        assertTrue(editor.isUserInput(switchA.id()));

        editor.toggleInput(gate.id()); // must be ignored rather than fail
        assertEquals(LogicState.ZERO, editor.inputValueOf(switchA.id()).orElseThrow());

        editor.toggleInput(switchA.id());
        assertEquals(LogicState.ONE, editor.inputValueOf(switchA.id()).orElseThrow());
    }

    @Test
    void oldDocumentListenersAreRemovedWhenSwitchingProjects() {
        CircuitEditor editor = new CircuitEditor(ComponentRegistry.standard());
        CircuitEditor editorForProjectB = new CircuitEditor(ComponentRegistry.standard());
        
        boolean[] projectACalled = {false};
        editor.setProject(CircuitProject.empty("projectA"), false);
        editor.document().addListener((doc, change) -> projectACalled[0] = true);
        
        // Switch to project B
        editor.setProject(CircuitProject.empty("projectB"), false);
        
        // Modify project B's document - this should NOT trigger project A's listener
        editor.execute(new AddComponentCommand(editor.document(), ComponentInstance.create(
                "logic.and", new CircuitPoint(0, 0), ParameterValues.empty())));
        
        assertFalse(projectACalled[0], "Old document listener from project A should not be called when editing project B");
    }
}
