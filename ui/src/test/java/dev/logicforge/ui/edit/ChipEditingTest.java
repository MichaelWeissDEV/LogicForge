package dev.logicforge.ui.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.chip.ChipDisplayMode;
import dev.logicforge.circuit.chip.ChipGeometry;
import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.ElectricalEndpoint;
import dev.logicforge.circuit.document.PlacedElectricalEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.geometry.CircuitBounds;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.Rotation;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.ui.command.AddChipCommand;
import dev.logicforge.ui.command.MoveChipsCommand;
import dev.logicforge.ui.command.RemoveElementsCommand;
import dev.logicforge.ui.command.RotateChipsCommand;
import dev.logicforge.ui.command.SetChipDisplayModeCommand;
import dev.logicforge.ui.command.SetChipReferenceDesignatorCommand;
import dev.logicforge.ui.wiring.OrthogonalWireRouter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Editor-level chip support, headless: selection, hit testing, commands/undo-redo and
 * clipboard — exercised without any JavaFX, the same way {@code CircuitEditorTest} covers
 * ordinary components.
 */
class ChipEditingTest {

    private final CircuitEditor editor = new CircuitEditor(ComponentRegistry.standard());
    private final HitTester hitTester = new HitTester(editor::document, editor::definition,
            id -> editor.chipRegistry().find(id), new OrthogonalWireRouter());

    private ChipInstance addNandChip(CircuitPoint position, String designator) {
        ChipInstance instance = ChipInstance.create("74HC00", position, designator);
        editor.execute(new AddChipCommand(editor.document(), instance));
        return instance;
    }

    // ------------------------------------------------------------ SelectionModel

    @Test
    void selectionModelTracksChipsAsAThirdCollection() {
        SelectionModel selection = new SelectionModel();
        UUID chipId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();

        selection.selectChip(chipId);
        assertTrue(selection.containsChip(chipId));
        assertFalse(selection.containsComponent(chipId));
        assertEquals(1, selection.size());
        assertFalse(selection.isEmpty());

        selection.selectComponent(componentId);
        assertFalse(selection.containsChip(chipId), "selecting a component replaces the whole selection");

        selection.addAll(List.of(componentId), List.of(chipId), List.of());
        assertEquals(2, selection.size());
        assertTrue(selection.containsComponent(componentId));
        assertTrue(selection.containsChip(chipId));

        selection.toggleChip(chipId);
        assertFalse(selection.containsChip(chipId));
        selection.toggleChip(chipId);
        assertTrue(selection.containsChip(chipId));

        selection.retainExisting(List.of(), List.of(), List.of());
        assertTrue(selection.isEmpty());
    }

    // ------------------------------------------------------------------ HitTester

    @Test
    void chipAtFindsThePackageBody() {
        ChipInstance chip = addNandChip(new CircuitPoint(200, 200), "U1");
        assertTrue(hitTester.chipAt(new CircuitPoint(200, 200)).isPresent());
        assertEquals(chip.id(), hitTester.chipAt(new CircuitPoint(200, 200)).orElseThrow().id());
        assertTrue(hitTester.chipAt(new CircuitPoint(-500, -500)).isEmpty());
    }

    @Test
    void chipPinAtFindsAPhysicalPinWithinTolerance() {
        ChipInstance chip = addNandChip(new CircuitPoint(0, 0), "U1");
        CircuitPoint pin1Tip = ChipGeometry.pinTip(chip,
                editor.chipRegistry().require("74HC00").packageDefinition().type(), 1);

        PlacedElectricalEndpoint hit = hitTester.chipPinAt(pin1Tip, 5).orElseThrow();
        assertTrue(hit.endpoint() instanceof ElectricalEndpoint.ChipPinEndpoint);
        ElectricalEndpoint.ChipPinEndpoint pinEndpoint = (ElectricalEndpoint.ChipPinEndpoint) hit.endpoint();
        assertEquals(chip.id(), pinEndpoint.chipInstanceId());
        assertEquals(1, pinEndpoint.physicalPinNumber());
        assertTrue(hit.connectable(), "pin 1 (1A) is a signal pin");

        assertTrue(hitTester.chipPinAt(pin1Tip.plus(1000, 1000), 5).isEmpty());
    }

    @Test
    void groundAndPowerPinsAreNotConnectable() {
        ChipInstance chip = addNandChip(new CircuitPoint(0, 0), "U1");
        var packageDefinition = editor.chipRegistry().require("74HC00").packageDefinition();
        // Pin 7 is GND, pin 14 is VCC on the 74HC00.
        CircuitPoint gnd = ChipGeometry.pinTip(chip, packageDefinition.type(), 7);
        CircuitPoint vcc = ChipGeometry.pinTip(chip, packageDefinition.type(), 14);
        assertFalse(hitTester.chipPinAt(gnd, 5).orElseThrow().connectable());
        assertFalse(hitTester.chipPinAt(vcc, 5).orElseThrow().connectable());
    }

    @Test
    void endpointAtFindsChipPinsThroughTheUnifiedSearch() {
        ChipInstance chip = addNandChip(new CircuitPoint(0, 0), "U1");
        // Far from anything: neither a port nor a chip pin should be found.
        assertTrue(hitTester.endpointAt(new CircuitPoint(9000, 9000), 5).isEmpty());
        // Exactly on a chip pin: endpointAt finds it exactly like portAt finds a component port.
        CircuitPoint pin1Tip = ChipGeometry.pinTip(chip,
                editor.chipRegistry().require("74HC00").packageDefinition().type(), 1);
        PlacedElectricalEndpoint hit = hitTester.endpointAt(pin1Tip, 5).orElseThrow();
        assertTrue(hit.endpoint() instanceof ElectricalEndpoint.ChipPinEndpoint);
    }

    @Test
    void chipsInCollectsChipsFullyInsideARectangle() {
        ChipInstance inside = addNandChip(new CircuitPoint(0, 0), "U1");
        addNandChip(new CircuitPoint(5000, 5000), "U2");
        CircuitBounds area = ChipGeometry.bodyBounds(inside,
                editor.chipRegistry().require("74HC00").packageDefinition().type()).grownBy(10);
        List<UUID> found = hitTester.chipsIn(area);
        assertEquals(List.of(inside.id()), found);
    }

    @Test
    void connectionAtWorksForAllFourEndpointCombinations() {
        ChipInstance chipA = addNandChip(new CircuitPoint(0, 0), "U1");
        ChipInstance chipB = addNandChip(new CircuitPoint(600, 0), "U2");
        ComponentInstance switchInstance = dev.logicforge.circuit.document.ComponentInstance.create(
                "source.toggle", new CircuitPoint(-300, 0), dev.logicforge.circuit.component.ParameterValues.empty());
        ComponentInstance ledInstance = dev.logicforge.circuit.document.ComponentInstance.create(
                "output.led", new CircuitPoint(900, 0), dev.logicforge.circuit.component.ParameterValues.empty());
        editor.execute(new dev.logicforge.ui.command.AddComponentCommand(editor.document(), switchInstance));
        editor.execute(new dev.logicforge.ui.command.AddComponentCommand(editor.document(), ledInstance));

        // component -> chip (switch OUT -> chipA pin 1 "1A")
        Connection componentToChip = Connection.create(
                new ElectricalEndpoint.ComponentEndpoint(
                        dev.logicforge.circuit.document.PortEndpoint.whole(new PortReference(switchInstance.id(), "OUT"))),
                new ElectricalEndpoint.ChipPinEndpoint(chipA.id(), 1));
        // chip -> component (chipA pin 3 "1Y" -> led IN)
        Connection chipToComponent = Connection.create(
                new ElectricalEndpoint.ChipPinEndpoint(chipA.id(), 3),
                new ElectricalEndpoint.ComponentEndpoint(
                        dev.logicforge.circuit.document.PortEndpoint.whole(new PortReference(ledInstance.id(), "IN"))));
        // chip -> chip (chipA pin 3 unused further; use chipA pin 2 -> chipB pin 1)
        Connection chipToChip = Connection.create(
                new ElectricalEndpoint.ChipPinEndpoint(chipA.id(), 2),
                new ElectricalEndpoint.ChipPinEndpoint(chipB.id(), 1));

        editor.document().addConnection(componentToChip);
        editor.document().addConnection(chipToComponent);
        editor.document().addConnection(chipToChip);

        // component -> component, for completeness (switch -> led directly would self-conflict
        // with the wires above electrically, so verify via a fresh pair instead).
        ComponentInstance switch2 = dev.logicforge.circuit.document.ComponentInstance.create(
                "source.toggle", new CircuitPoint(-300, 300), dev.logicforge.circuit.component.ParameterValues.empty());
        ComponentInstance led2 = dev.logicforge.circuit.document.ComponentInstance.create(
                "output.led", new CircuitPoint(-100, 300), dev.logicforge.circuit.component.ParameterValues.empty());
        editor.execute(new dev.logicforge.ui.command.AddComponentCommand(editor.document(), switch2));
        editor.execute(new dev.logicforge.ui.command.AddComponentCommand(editor.document(), led2));
        Connection componentToComponent = Connection.create(new PortReference(switch2.id(), "OUT"),
                new PortReference(led2.id(), "IN"));
        editor.document().addConnection(componentToComponent);

        for (Connection connection : List.of(componentToChip, chipToComponent, chipToChip, componentToComponent)) {
            CircuitPoint midpoint = midpointOf(connection);
            assertTrue(hitTester.connectionAt(midpoint, 40).isPresent(),
                    "expected to hit " + connection.id() + " near " + midpoint);
        }
    }

    private CircuitPoint midpointOf(Connection connection) {
        var route = new OrthogonalWireRouter().route(
                hitTester.resolve(connection.from()).orElseThrow(),
                hitTester.resolve(connection.to()).orElseThrow(), connection.waypoints());
        var points = route.points();
        return points.get(points.size() / 2);
    }

    // ------------------------------------------------------------------ Commands

    @Test
    void addAndRemoveChipRoundTripThroughUndo() {
        ChipInstance chip = addNandChip(new CircuitPoint(0, 0), "U1");
        assertEquals(1, editor.document().chipCount());

        editor.execute(new RemoveElementsCommand(editor.document(), Set.of(), Set.of(chip.id()), Set.of()));
        assertEquals(0, editor.document().chipCount());

        editor.undo();
        assertEquals(1, editor.document().chipCount());
        assertEquals(chip, editor.document().chip(chip.id()).orElseThrow(), "undo restores the exact same instance");

        editor.redo();
        assertEquals(0, editor.document().chipCount());
    }

    @Test
    void removingAChipTakesItsWiresWithItAndUndoRestoresThem() {
        ChipInstance chip = addNandChip(new CircuitPoint(0, 0), "U1");
        ComponentInstance switchInstance = dev.logicforge.circuit.document.ComponentInstance.create(
                "source.toggle", new CircuitPoint(-200, 0), dev.logicforge.circuit.component.ParameterValues.empty());
        editor.execute(new dev.logicforge.ui.command.AddComponentCommand(editor.document(), switchInstance));
        Connection wire = Connection.create(
                new ElectricalEndpoint.ComponentEndpoint(
                        dev.logicforge.circuit.document.PortEndpoint.whole(new PortReference(switchInstance.id(), "OUT"))),
                new ElectricalEndpoint.ChipPinEndpoint(chip.id(), 1));
        editor.document().addConnection(wire);
        assertEquals(1, editor.document().connectionCount());

        editor.execute(new RemoveElementsCommand(editor.document(), Set.of(), Set.of(chip.id()), Set.of()));
        assertEquals(0, editor.document().chipCount());
        assertEquals(0, editor.document().connectionCount(), "the wire touching the chip pin is removed too");

        editor.undo();
        assertEquals(1, editor.document().chipCount());
        assertEquals(1, editor.document().connectionCount(), "undo restores the chip and its wire");
    }

    @Test
    void moveAndRotateChipUndoRedo() {
        ChipInstance chip = addNandChip(new CircuitPoint(0, 0), "U1");
        ChipInstance moved = chip.withPosition(new CircuitPoint(400, 400));
        editor.execute(new MoveChipsCommand(editor.document(), List.of(chip), List.of(moved)));
        assertEquals(new CircuitPoint(400, 400), editor.document().chip(chip.id()).orElseThrow().position());
        editor.undo();
        assertEquals(new CircuitPoint(0, 0), editor.document().chip(chip.id()).orElseThrow().position());

        ChipInstance rotated = chip.withRotation(Rotation.DEG_90);
        editor.execute(new RotateChipsCommand(editor.document(), List.of(chip), List.of(rotated)));
        assertEquals(Rotation.DEG_90, editor.document().chip(chip.id()).orElseThrow().rotation());
        editor.undo();
        assertEquals(Rotation.DEG_0, editor.document().chip(chip.id()).orElseThrow().rotation());
    }

    @Test
    void renameAndDisplayModeCommandsAreUndoable() {
        ChipInstance chip = addNandChip(new CircuitPoint(0, 0), "U1");

        editor.execute(new SetChipReferenceDesignatorCommand(editor.document(), chip, "U9"));
        assertEquals("U9", editor.document().chip(chip.id()).orElseThrow().referenceDesignator());
        editor.undo();
        assertEquals("U1", editor.document().chip(chip.id()).orElseThrow().referenceDesignator());

        editor.execute(new SetChipDisplayModeCommand(editor.document(), chip, ChipDisplayMode.SYMBOL));
        assertEquals(ChipDisplayMode.SYMBOL, editor.document().chip(chip.id()).orElseThrow().displayMode());
        editor.undo();
        assertEquals(ChipDisplayMode.PACKAGE, editor.document().chip(chip.id()).orElseThrow().displayMode());
    }

    @Test
    void designatorsAreSequentialAndPreservedAcrossUndoRedo() {
        ChipInstance first = ChipInstance.create("74HC00", CircuitPoint.ORIGIN, ChipDesignators.next(editor.document()));
        editor.execute(new AddChipCommand(editor.document(), first));
        assertEquals("U1", first.referenceDesignator());

        ChipInstance second = ChipInstance.create("74HC00", new CircuitPoint(200, 0),
                ChipDesignators.next(editor.document()));
        editor.execute(new AddChipCommand(editor.document(), second));
        assertEquals("U2", second.referenceDesignator());

        editor.undo();
        editor.redo();
        assertEquals("U2", editor.document().chip(second.id()).orElseThrow().referenceDesignator(),
                "the designator baked into the instance survives undo/redo unchanged");
    }

    // ------------------------------------------------------------------ Clipboard

    @Test
    void copyPasteAssignsFreshIdsAndDesignatorsButKeepsPhysicalPinNumbers() {
        ChipInstance chip = addNandChip(new CircuitPoint(0, 0), "U1");
        CircuitClipboard clipboard = new CircuitClipboard();
        clipboard.copy(editor.document(), List.of(), List.of(chip.id()));
        CircuitClipboard.Fragment pasted = clipboard.prepareForPaste(editor.document(), 100, 100);

        assertEquals(1, pasted.chips().size());
        ChipInstance copy = pasted.chips().get(0);
        assertNotEquals(chip.id(), copy.id());
        assertEquals("U2", copy.referenceDesignator(), "a fresh, non-colliding designator");
        assertEquals(chip.chipDefinitionId(), copy.chipDefinitionId());
        assertEquals(new CircuitPoint(100, 100), copy.position());
    }

    @Test
    void copyPasteRemapsChipToChipWiresWithoutChangingPinNumbers() {
        ChipInstance chipA = addNandChip(new CircuitPoint(0, 0), "U1");
        ChipInstance chipB = addNandChip(new CircuitPoint(600, 0), "U2");
        Connection wire = Connection.create(new ElectricalEndpoint.ChipPinEndpoint(chipA.id(), 3),
                new ElectricalEndpoint.ChipPinEndpoint(chipB.id(), 1));
        editor.document().addConnection(wire);

        CircuitClipboard clipboard = new CircuitClipboard();
        clipboard.copy(editor.document(), List.of(), List.of(chipA.id(), chipB.id()));
        CircuitClipboard.Fragment pasted = clipboard.prepareForPaste(editor.document(), 1000, 1000);

        assertEquals(2, pasted.chips().size());
        assertEquals(1, pasted.connections().size());
        Connection copiedWire = pasted.connections().get(0);
        ElectricalEndpoint.ChipPinEndpoint from = (ElectricalEndpoint.ChipPinEndpoint) copiedWire.from();
        ElectricalEndpoint.ChipPinEndpoint to = (ElectricalEndpoint.ChipPinEndpoint) copiedWire.to();
        assertEquals(3, from.physicalPinNumber(), "physical pin numbers never change on copy");
        assertEquals(1, to.physicalPinNumber());
        assertNotEquals(chipA.id(), from.chipInstanceId());
        assertNotEquals(chipB.id(), to.chipInstanceId());
        Set<UUID> pastedIds = Set.of(pasted.chips().get(0).id(), pasted.chips().get(1).id());
        assertTrue(pastedIds.contains(from.chipInstanceId()));
        assertTrue(pastedIds.contains(to.chipInstanceId()));
    }

    @Test
    void copyMixedSelectionOfComponentAndChipTogether() {
        ChipInstance chip = addNandChip(new CircuitPoint(0, 0), "U1");
        ComponentInstance switchInstance = dev.logicforge.circuit.document.ComponentInstance.create(
                "source.toggle", new CircuitPoint(-200, 0), dev.logicforge.circuit.component.ParameterValues.empty());
        editor.execute(new dev.logicforge.ui.command.AddComponentCommand(editor.document(), switchInstance));
        Connection wire = Connection.create(
                new ElectricalEndpoint.ComponentEndpoint(
                        dev.logicforge.circuit.document.PortEndpoint.whole(new PortReference(switchInstance.id(), "OUT"))),
                new ElectricalEndpoint.ChipPinEndpoint(chip.id(), 1));
        editor.document().addConnection(wire);

        CircuitClipboard clipboard = new CircuitClipboard();
        clipboard.copy(editor.document(), List.of(switchInstance.id()), List.of(chip.id()));
        CircuitClipboard.Fragment pasted = clipboard.prepareForPaste(editor.document(), 50, 50);

        assertEquals(1, pasted.components().size());
        assertEquals(1, pasted.chips().size());
        assertEquals(1, pasted.connections().size(), "the component<->chip wire is carried along");
    }
}
