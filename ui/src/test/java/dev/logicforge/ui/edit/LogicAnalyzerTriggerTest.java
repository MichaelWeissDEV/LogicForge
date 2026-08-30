package dev.logicforge.ui.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.analyzer.TriggerCondition;
import dev.logicforge.analyzer.TriggerEngine;
import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.LibraryParameters;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.SimulationStatus;
import dev.logicforge.ui.command.AddComponentCommand;
import org.junit.jupiter.api.Test;

/**
 * The trigger arms on one watched signal and, once its condition matches, pauses the shared
 * simulation run-control the toolbar's Run/Pause button and the Study window also drive —
 * exercised through {@link LogicAnalyzerController} exactly the way the UI wires it, rather
 * than through {@link TriggerEngine} directly (that headless engine has its own dedicated
 * tests in the {@code logic-analyzer} module).
 */
class LogicAnalyzerTriggerTest {

    private final CircuitEditor editor = new CircuitEditor(ComponentRegistry.standard());

    private ComponentInstance addToggle() {
        ComponentInstance toggle = ComponentInstance.create("source.toggle", CircuitPoint.ORIGIN,
                ParameterValues.empty());
        editor.execute(new AddComponentCommand(editor.document(), toggle));
        return toggle;
    }

    @Test
    void firingATriggerPausesTheSharedRunControl() {
        ComponentInstance toggle = addToggle();
        LogicAnalyzerController analyzer = new LogicAnalyzerController(editor);
        PortEndpoint out = PortEndpoint.whole(new PortReference(toggle.id(), "OUT"));
        analyzer.addSignal(out, "SW.OUT");
        LogicAnalyzerController.WatchedSignal signal = analyzer.watchedSignals().get(0);

        analyzer.armTrigger(signal, new TriggerCondition.RisingEdge(0));
        assertEquals(TriggerEngine.Status.ARMED, analyzer.triggerStatus());
        assertTrue(editor.isRunning(), "nothing has fired yet");

        editor.toggleInput(toggle.id());

        assertEquals(TriggerEngine.Status.TRIGGERED, analyzer.triggerStatus());
        assertTrue(analyzer.triggerTime().isPresent());
        assertFalse(editor.isRunning(), "the fire listener must pause the shared run control");
    }

    @Test
    void doesNotFireOnAFallingEdgeWhenArmedForRising() {
        ComponentInstance toggle = addToggle();
        LogicAnalyzerController analyzer = new LogicAnalyzerController(editor);
        PortEndpoint out = PortEndpoint.whole(new PortReference(toggle.id(), "OUT"));
        analyzer.addSignal(out, "SW.OUT");
        editor.toggleInput(toggle.id()); // now high, before arming
        LogicAnalyzerController.WatchedSignal signal = analyzer.watchedSignals().get(0);

        analyzer.armTrigger(signal, new TriggerCondition.RisingEdge(0));
        editor.toggleInput(toggle.id()); // falling, not rising

        assertEquals(TriggerEngine.Status.ARMED, analyzer.triggerStatus());
        assertTrue(editor.isRunning());
    }

    @Test
    void rearmFiresAgainOnTheSameCondition() {
        ComponentInstance toggle = addToggle();
        LogicAnalyzerController analyzer = new LogicAnalyzerController(editor);
        PortEndpoint out = PortEndpoint.whole(new PortReference(toggle.id(), "OUT"));
        analyzer.addSignal(out, "SW.OUT");
        LogicAnalyzerController.WatchedSignal signal = analyzer.watchedSignals().get(0);
        analyzer.armTrigger(signal, new TriggerCondition.RisingEdge(0));

        editor.toggleInput(toggle.id());
        assertEquals(TriggerEngine.Status.TRIGGERED, analyzer.triggerStatus());

        editor.setRunning(true);
        analyzer.rearmTrigger();
        assertEquals(TriggerEngine.Status.ARMED, analyzer.triggerStatus());
        assertTrue(analyzer.triggerTime().isEmpty());

        editor.toggleInput(toggle.id()); // falling
        editor.toggleInput(toggle.id()); // rising again
        assertEquals(TriggerEngine.Status.TRIGGERED, analyzer.triggerStatus());
        assertFalse(editor.isRunning());
    }

    @Test
    void disarmTriggerStopsWatchingAndClearsTheRecord() {
        ComponentInstance toggle = addToggle();
        LogicAnalyzerController analyzer = new LogicAnalyzerController(editor);
        PortEndpoint out = PortEndpoint.whole(new PortReference(toggle.id(), "OUT"));
        analyzer.addSignal(out, "SW.OUT");
        LogicAnalyzerController.WatchedSignal signal = analyzer.watchedSignals().get(0);
        analyzer.armTrigger(signal, new TriggerCondition.RisingEdge(0));

        analyzer.disarmTrigger();

        assertEquals(TriggerEngine.Status.DISARMED, analyzer.triggerStatus());
        assertTrue(analyzer.armedSignal().isEmpty());

        editor.toggleInput(toggle.id());
        assertEquals(TriggerEngine.Status.DISARMED, analyzer.triggerStatus(),
                "a disarmed trigger must not react to circuit activity");
        assertTrue(editor.isRunning());
    }

    @Test
    void removingTheArmedWatchAutomaticallyDisarmsTheTrigger() {
        ComponentInstance toggle = addToggle();
        LogicAnalyzerController analyzer = new LogicAnalyzerController(editor);
        PortEndpoint out = PortEndpoint.whole(new PortReference(toggle.id(), "OUT"));
        analyzer.addSignal(out, "SW.OUT");
        LogicAnalyzerController.WatchedSignal signal = analyzer.watchedSignals().get(0);
        analyzer.armTrigger(signal, new TriggerCondition.RisingEdge(0));
        assertEquals(TriggerEngine.Status.ARMED, analyzer.triggerStatus());

        analyzer.removeSignal(signal);

        assertEquals(TriggerEngine.Status.DISARMED, analyzer.triggerStatus());
        assertTrue(analyzer.armedSignal().isEmpty());
    }

    @Test
    void theArmedTriggerSurvivesARecompileByRebindingItsNet() {
        ComponentInstance toggle = addToggle();
        LogicAnalyzerController analyzer = new LogicAnalyzerController(editor);
        PortEndpoint out = PortEndpoint.whole(new PortReference(toggle.id(), "OUT"));
        analyzer.addSignal(out, "SW.OUT");
        LogicAnalyzerController.WatchedSignal signal = analyzer.watchedSignals().get(0);
        analyzer.armTrigger(signal, new TriggerCondition.RisingEdge(0));

        // Adding another, unrelated component forces a structural recompile — a fresh
        // Simulation with renumbered nets, exactly like a real editing session.
        ComponentInstance other = ComponentInstance.create("output.led", new CircuitPoint(300, 0),
                ParameterValues.empty());
        editor.execute(new AddComponentCommand(editor.document(), other));

        assertEquals(TriggerEngine.Status.ARMED, analyzer.triggerStatus(),
                "the trigger must re-bind to the new simulation's net, not silently drop");

        editor.toggleInput(toggle.id());
        assertEquals(TriggerEngine.Status.TRIGGERED, analyzer.triggerStatus());
        assertFalse(editor.isRunning());
    }

    /**
     * Every other test here settles synchronously inside one {@code setInput} call, so a
     * fire listener re-entering the simulation (pausing it from inside {@code onNetChanged})
     * has nowhere to actually bite. A running clock's own scheduled edges are the real
     * clock-driven propagation path — this proves the fire-time re-entrancy (the trigger's
     * own listener calling back into {@code editor.setRunning}, mid delta-cycle dispatch)
     * leaves the simulation in a normal, stable state with nothing thrown.
     */
    @Test
    void firingOnAClockDrivenEdgeDuringPropagationLeavesTheSimulationStable() {
        ParameterValues timing = editor.definition("source.clock").orElseThrow().defaultParameters()
                .with(LibraryParameters.FREQUENCY_HZ, 1_000_000)
                .with(LibraryParameters.INITIALLY_HIGH, false);
        ComponentInstance clock = ComponentInstance.create("source.clock", CircuitPoint.ORIGIN, timing);
        editor.execute(new AddComponentCommand(editor.document(), clock));

        LogicAnalyzerController analyzer = new LogicAnalyzerController(editor);
        PortEndpoint out = PortEndpoint.whole(new PortReference(clock.id(), "OUT"));
        analyzer.addSignal(out, "CLK.OUT");
        LogicAnalyzerController.WatchedSignal signal = analyzer.watchedSignals().get(0);
        analyzer.armTrigger(signal, new TriggerCondition.RisingEdge(0));
        editor.setRunning(true);

        for (int step = 0; step < 20 && analyzer.triggerStatus() != TriggerEngine.Status.TRIGGERED; step++) {
            editor.stepTime();
        }

        assertEquals(TriggerEngine.Status.TRIGGERED, analyzer.triggerStatus());
        assertEquals(SimulationStatus.STABLE, editor.status(),
                "pausing from inside the observer callback must not leave the simulation mid-update");
    }

    @Test
    void valueEqualsTriggerFiresOnABusReachingItsTarget() {
        ComponentInstance a = addToggle();
        ComponentInstance b = ComponentInstance.create("source.toggle", new CircuitPoint(0, 100),
                ParameterValues.empty());
        editor.execute(new AddComponentCommand(editor.document(), b));
        LogicAnalyzerController analyzer = new LogicAnalyzerController(editor);
        PortEndpoint aOut = PortEndpoint.whole(new PortReference(a.id(), "OUT"));
        analyzer.addSignal(aOut, "A.OUT");
        LogicAnalyzerController.WatchedSignal signal = analyzer.watchedSignals().get(0);

        analyzer.armTrigger(signal, new TriggerCondition.ValueEquals(LogicVector.ONE));
        editor.toggleInput(a.id());

        assertEquals(TriggerEngine.Status.TRIGGERED, analyzer.triggerStatus());
    }
}
