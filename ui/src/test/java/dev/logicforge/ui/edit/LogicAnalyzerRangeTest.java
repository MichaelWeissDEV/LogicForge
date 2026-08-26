package dev.logicforge.ui.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.LibraryParameters;
import dev.logicforge.logic.LogicVector;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A range watch (e.g. {@code DATA[7:4]}) on a bit-mode port genuinely spans several
 * independent runtime nets — there is no single net that "is" the range. These tests drive
 * {@link LogicAnalyzerController} through that exact shape: an analyzer trace must observe
 * every underlying net, reconstruct the full logical value on any of their changes, and stay
 * pinned to the hierarchy instance it was added on even when the same child circuit backs
 * more than one live instance.
 */
class LogicAnalyzerRangeTest {

    private final CircuitEditor editor = new CircuitEditor(ComponentRegistry.standard());

    @Test
    void rangeWatchOnABitModePortTracksAllUnderlyingNetsAndIgnoresBitsOutsideTheRange() {
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance[] toggles = eightToggles(main);
        ComponentInstance probe = busProbe(main, toggles);
        CircuitProject project = CircuitProject.of("range-watch", main);
        editor.setProject(project, false);

        LogicAnalyzerController analyzer = new LogicAnalyzerController(editor);
        PortEndpoint range74 = PortEndpoint.range(new PortReference(probe.id(), "IN"), 7, 4);
        analyzer.addSignal(range74, "DATA[7:4]");

        var trace = analyzer.traceFor(range74).orElseThrow();
        assertEquals(1, trace.transitions().size());
        assertEquals(LogicVector.fromUnsignedLong(0, 4), trace.transitions().get(0).value());

        // Bit 5 is inside the watched range [7:4]: must produce a new transition.
        editor.toggleInput(toggles[5].id());
        var afterBit5 = analyzer.traceFor(range74).orElseThrow();
        assertEquals(2, afterBit5.transitions().size());
        assertEquals(LogicVector.fromUnsignedLong(0b0010, 4), afterBit5.transitions().get(1).value(),
                "bit 5 is offset 1 within [7:4]");

        // Bit 2 is outside the watched range: must not produce a new transition.
        editor.toggleInput(toggles[2].id());
        var afterBit2 = analyzer.traceFor(range74).orElseThrow();
        assertEquals(2, afterBit2.transitions().size(), "bit 2 is outside [7:4] and must be ignored");
    }

    /**
     * The same child circuit (with a bit-mode bus inside it) instantiated twice: a range
     * watch added on one instance must track only that instance's nets, independent of the
     * other — proving composite/range analyzer bindings compose correctly with hierarchy
     * resolution, not just with a flat root circuit.
     */
    @Test
    void rangeWatchAcrossTwoInstancesOfTheSameChildStaysIndependent() {
        CircuitDocument child = new CircuitDocument(new CircuitMetadata("Bus", ""));
        ComponentInstance[] toggles = eightToggles(child);
        ComponentInstance probe = busProbe(child, toggles);

        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance busA = SubcircuitSupport.instantiate("Bus", new CircuitPoint(0, 0)).withLabel("busA");
        ComponentInstance busB = SubcircuitSupport.instantiate("Bus", new CircuitPoint(300, 0)).withLabel("busB");
        main.addComponent(busA);
        main.addComponent(busB);

        CircuitProject project = CircuitProject.of("range-hierarchy", main);
        project.putCircuit(child);
        editor.setProject(project, false);

        LogicAnalyzerController analyzer = new LogicAnalyzerController(editor);
        PortEndpoint range74 = PortEndpoint.range(new PortReference(probe.id(), "IN"), 7, 4);

        editor.openSubcircuit(busA);
        analyzer.addSignal(range74, "busA.DATA[7:4]");
        editor.toggleInput(toggles[4].id()); // bit 4 -> offset 0 within the range
        editor.navigateBack();

        editor.openSubcircuit(busB);
        analyzer.addSignal(range74, "busB.DATA[7:4]");
        editor.navigateBack();

        assertEquals(2, analyzer.watchedSignals().size());
        var busASignal = analyzer.watchedSignals().get(0);
        var busBSignal = analyzer.watchedSignals().get(1);
        assertNotEquals(busASignal.instancePath(), busBSignal.instancePath());

        assertEquals(LogicVector.fromUnsignedLong(0b0001, 4),
                analyzer.traceFor(busASignal).orElseThrow().transitions().get(1).value(),
                "busA's own toggle must be reflected");
        assertEquals(LogicVector.fromUnsignedLong(0, 4),
                analyzer.traceFor(busBSignal).orElseThrow().transitions().get(0).value(),
                "busB must be untouched by busA's toggle");
    }

    /** Removing the watched instance must leave the range watch unresolved, never re-bind. */
    @Test
    void rangeWatchStaysUnresolvedOnceItsInstanceIsRemoved() {
        CircuitDocument child = new CircuitDocument(new CircuitMetadata("Bus", ""));
        ComponentInstance[] toggles = eightToggles(child);
        ComponentInstance probe = busProbe(child, toggles);

        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance busA = SubcircuitSupport.instantiate("Bus", new CircuitPoint(0, 0)).withLabel("busA");
        ComponentInstance busB = SubcircuitSupport.instantiate("Bus", new CircuitPoint(300, 0)).withLabel("busB");
        main.addComponent(busA);
        main.addComponent(busB);

        CircuitProject project = CircuitProject.of("range-removed", main);
        project.putCircuit(child);
        editor.setProject(project, false);

        LogicAnalyzerController analyzer = new LogicAnalyzerController(editor);
        PortEndpoint range74 = PortEndpoint.range(new PortReference(probe.id(), "IN"), 7, 4);

        editor.openSubcircuit(busA);
        analyzer.addSignal(range74, "busA.DATA[7:4]");
        var watchedBusA = analyzer.watchedSignals().get(0);
        assertTrue(analyzer.traceFor(watchedBusA).isPresent());

        editor.navigateBack();
        editor.execute(new dev.logicforge.ui.command.RemoveElementsCommand(main,
                List.of(busA.id()), List.of()));

        assertFalse(analyzer.traceFor(watchedBusA).isPresent(),
                "busA is gone; the watch must not silently reattach to busB");
    }

    // ------------------------------------------------------------------

    /** Eight independent toggles, each individually settable, for driving a bit-mode bus. */
    private ComponentInstance[] eightToggles(CircuitDocument document) {
        ComponentInstance[] toggles = new ComponentInstance[8];
        for (int i = 0; i < 8; i++) {
            ComponentInstance toggle = ComponentInstance.create("source.toggle",
                    new CircuitPoint(0, i * 40), ParameterValues.empty());
            document.addComponent(toggle);
            toggles[i] = toggle;
        }
        return toggles;
    }

    /** An 8-bit probe wired bit-by-bit to the given toggles, forcing IN into bit mode. */
    private ComponentInstance busProbe(CircuitDocument document, ComponentInstance[] toggles) {
        ParameterValues parameters = ComponentRegistry.standard().require("routing.bus_probe")
                .definition().defaultParameters().with(LibraryParameters.WIDTH, 8);
        ComponentInstance probe = ComponentInstance.create("routing.bus_probe",
                new CircuitPoint(300, 0), parameters);
        document.addComponent(probe);
        for (int i = 0; i < 8; i++) {
            document.addConnection(Connection.create(
                    PortEndpoint.whole(new PortReference(toggles[i].id(), "OUT")),
                    PortEndpoint.bit(new PortReference(probe.id(), "IN"), i)));
        }
        return probe;
    }
}
