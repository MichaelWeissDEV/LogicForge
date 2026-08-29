package dev.logicforge.ui.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.ElectricalEndpoint;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.ui.command.AddChipCommand;
import dev.logicforge.ui.command.AddComponentCommand;
import org.junit.jupiter.api.Test;

/**
 * The Logic Analyzer must be able to watch a physical chip pin exactly like a component
 * port: a pin is resolved through the compiler's {@code ChipSourceMap} onto the logical
 * port it was expanded onto, and from there through the same {@code ResolvedSignal}
 * machinery any ordinary watch uses. These tests drive {@link LogicAnalyzerController}
 * with a real 74HC00 chip pin end to end, the same way {@link LogicAnalyzerRangeTest}
 * covers bit-mode component ports.
 */
class LogicAnalyzerChipPinTest {

    private final CircuitEditor editor = new CircuitEditor(ComponentRegistry.standard());

    @Test
    void watchingAChipInputPinTracksTheSwitchDrivingIt() {
        ChipInstance chip = ChipInstance.create("74HC00", CircuitPoint.ORIGIN, "U1");
        editor.execute(new AddChipCommand(editor.document(), chip));
        ComponentInstance toggle = ComponentInstance.create("source.toggle", new CircuitPoint(-200, 0),
                ParameterValues.empty());
        editor.execute(new AddComponentCommand(editor.document(), toggle));
        editor.document().addConnection(Connection.create(
                new ElectricalEndpoint.ComponentEndpoint(PortEndpoint.whole(new PortReference(toggle.id(), "OUT"))),
                new ElectricalEndpoint.ChipPinEndpoint(chip.id(), 1)));

        LogicAnalyzerController analyzer = new LogicAnalyzerController(editor);
        ElectricalEndpoint.ChipPinEndpoint pin1 = new ElectricalEndpoint.ChipPinEndpoint(chip.id(), 1);
        analyzer.addSignal(pin1, "U1.pin1");
        assertTrue(analyzer.isWatching(pin1));

        var initial = analyzer.traceFor(pin1).orElseThrow();
        assertEquals(LogicVector.ZERO, lastValue(initial));

        editor.toggleInput(toggle.id());
        var afterToggle = analyzer.traceFor(pin1).orElseThrow();
        assertEquals(LogicVector.ONE, lastValue(afterToggle));

        analyzer.removeSignal(pin1);
        assertFalse(analyzer.isWatching(pin1));
    }

    @Test
    void watchingAChipOutputPinObservesTheGateResult() {
        ChipInstance chip = ChipInstance.create("74HC00", CircuitPoint.ORIGIN, "U1");
        editor.execute(new AddChipCommand(editor.document(), chip));
        ComponentInstance a = ComponentInstance.create("source.toggle", new CircuitPoint(-200, 0),
                ParameterValues.empty());
        ComponentInstance b = ComponentInstance.create("source.toggle", new CircuitPoint(-200, 100),
                ParameterValues.empty());
        editor.execute(new AddComponentCommand(editor.document(), a));
        editor.execute(new AddComponentCommand(editor.document(), b));
        // Gate 1 of a 74HC00: pins 1 (1A) and 2 (1B) in, pin 3 (1Y) out.
        editor.document().addConnection(Connection.create(
                new ElectricalEndpoint.ComponentEndpoint(PortEndpoint.whole(new PortReference(a.id(), "OUT"))),
                new ElectricalEndpoint.ChipPinEndpoint(chip.id(), 1)));
        editor.document().addConnection(Connection.create(
                new ElectricalEndpoint.ComponentEndpoint(PortEndpoint.whole(new PortReference(b.id(), "OUT"))),
                new ElectricalEndpoint.ChipPinEndpoint(chip.id(), 2)));

        LogicAnalyzerController analyzer = new LogicAnalyzerController(editor);
        ElectricalEndpoint.ChipPinEndpoint pinY = new ElectricalEndpoint.ChipPinEndpoint(chip.id(), 3);
        analyzer.addSignal(pinY, "U1.pin3");

        assertEquals(LogicVector.ONE, lastValue(analyzer.traceFor(pinY).orElseThrow()), "NAND(0,0) = 1");

        editor.toggleInput(a.id());
        editor.toggleInput(b.id());
        assertEquals(LogicVector.ZERO, lastValue(analyzer.traceFor(pinY).orElseThrow()), "NAND(1,1) = 0");
    }

    @Test
    void locationAndNavigateToRuntimeEndpointSelectTheChipForAChipPinWatch() {
        ChipInstance chip = ChipInstance.create("74HC00", CircuitPoint.ORIGIN, "U1");
        editor.execute(new AddChipCommand(editor.document(), chip));
        ComponentInstance toggle = ComponentInstance.create("source.toggle", new CircuitPoint(-200, 0),
                ParameterValues.empty());
        editor.execute(new AddComponentCommand(editor.document(), toggle));
        editor.document().addConnection(Connection.create(
                new ElectricalEndpoint.ComponentEndpoint(PortEndpoint.whole(new PortReference(toggle.id(), "OUT"))),
                new ElectricalEndpoint.ChipPinEndpoint(chip.id(), 1)));

        LogicAnalyzerController analyzer = new LogicAnalyzerController(editor);
        ElectricalEndpoint.ChipPinEndpoint pin1 = new ElectricalEndpoint.ChipPinEndpoint(chip.id(), 1);
        analyzer.addSignal(pin1, "U1.pin1");
        LogicAnalyzerController.WatchedSignal signal = analyzer.watchedSignals().get(0);

        editor.selection().clear();
        LogicAnalyzerController.SignalLocation location = analyzer.location(signal).orElseThrow();
        assertTrue(editor.navigateToRuntimeEndpoint(location.parentPath(), location.endpoint()));
        // Pin 1 is wired to the toggle, so navigation highlights that wire — the same
        // "prefer the connection over the bare endpoint" rule the component-port overload
        // already applies when the watched port has a connection.
        assertTrue(editor.selection().connections().stream()
                        .map(id -> editor.document().connection(id).orElseThrow())
                        .anyMatch(connection -> connection.touchesChip(chip.id())),
                "navigating to a wired chip pin watch selects its wire");
    }

    @Test
    void navigateToRuntimeEndpointSelectsTheChipItselfWhenThePinIsUnconnected() {
        ChipInstance chip = ChipInstance.create("74HC00", CircuitPoint.ORIGIN, "U1");
        editor.execute(new AddChipCommand(editor.document(), chip));

        // Pin 4 (2A) is a real, but unwired, input pin of the same package.
        ElectricalEndpoint.ChipPinEndpoint pin4 = new ElectricalEndpoint.ChipPinEndpoint(chip.id(), 4);
        LogicAnalyzerController.SignalLocation location =
                new LogicAnalyzerController.SignalLocation(
                        dev.logicforge.compiler.RuntimeInstancePath.root("main"), pin4);

        editor.selection().clear();
        assertTrue(editor.navigateToRuntimeEndpoint(location.parentPath(), location.endpoint()));
        assertTrue(editor.selection().containsChip(chip.id()),
                "an unwired chip pin watch falls back to selecting its chip");
    }

    private static LogicVector lastValue(dev.logicforge.analyzer.SignalTrace trace) {
        return trace.transitions().get(trace.transitions().size() - 1).value();
    }
}
