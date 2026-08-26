package dev.logicforge.ui.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitMetadata;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.SubcircuitSupport;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.LibraryParameters;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.MemorySnapshot;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A {@link MemoryView}-style caller must stay editable even after the editor navigates away
 * from the component's owning circuit entirely (not just to a sibling instance) — the
 * previous fix only captured a hierarchy instance path, which is not enough to find a
 * project-backed ROM's own definition document once that circuit is no longer the one the
 * editor's ambient {@code document} field points at.
 */
class ComponentViewTargetTest {

    private final CircuitEditor editor = new CircuitEditor(ComponentRegistry.standard());

    @Test
    void writingRomContentsThroughTheCapturedTargetWorksAfterNavigatingCompletelyAway() {
        CircuitDocument child = new CircuitDocument(new CircuitMetadata("Cpu", ""));
        ComponentInstance rom = ComponentInstance.create("memory.rom", new CircuitPoint(0, 0),
                ComponentRegistry.standard().require("memory.rom").definition().defaultParameters()
                        .with(LibraryParameters.ADDRESS_WIDTH, 2)
                        .with(LibraryParameters.WIDTH, 8));
        child.addComponent(rom);

        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance cpuA = SubcircuitSupport.instantiate("Cpu", new CircuitPoint(0, 0)).withLabel("cpuA");
        main.addComponent(cpuA);

        CircuitProject project = CircuitProject.of("rom-target", main);
        project.putCircuit(child);
        editor.setProject(project, false);

        editor.openSubcircuit(cpuA);
        ComponentViewTarget target = editor.viewTarget(rom.id());

        // Navigate all the way back to main: editor.document() is now "main", which does not
        // contain the ROM's local UUID at all (it lives only in the "Cpu" document).
        editor.navigateBack();
        assertEquals("main", editor.activeCircuitName());

        editor.writeMemoryWord(target, 1, LogicVector.fromUnsignedLong(0x77, 8));

        // The write lands in the "Cpu" document's ROM_CONTENTS parameter...
        ComponentInstance updated = project.circuit("Cpu").orElseThrow().requireComponent(rom.id());
        assertNotEquals(rom.parameters().asMap().get(LibraryParameters.ROM_CONTENTS.key()),
                updated.parameters().asMap().get(LibraryParameters.ROM_CONTENTS.key()),
                "ROM_CONTENTS must have actually changed on Cpu's own document");

        // ...and, since a parameter change recompiles, is visible through the runtime too.
        editor.openSubcircuit(cpuA);
        Optional<MemorySnapshot> snapshot = editor.memorySnapshot(rom.id());
        assertTrue(snapshot.isPresent());
        assertEquals(0x77, snapshot.get().wordAt(1).toUnsignedLong().orElseThrow());
    }

    @Test
    void loadingRomContentsThroughTheCapturedTargetWorksAfterNavigatingCompletelyAway() {
        CircuitDocument child = new CircuitDocument(new CircuitMetadata("Cpu", ""));
        ComponentInstance rom = ComponentInstance.create("memory.rom", new CircuitPoint(0, 0),
                ComponentRegistry.standard().require("memory.rom").definition().defaultParameters()
                        .with(LibraryParameters.ADDRESS_WIDTH, 2)
                        .with(LibraryParameters.WIDTH, 8));
        child.addComponent(rom);

        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance cpuA = SubcircuitSupport.instantiate("Cpu", new CircuitPoint(0, 0)).withLabel("cpuA");
        main.addComponent(cpuA);

        CircuitProject project = CircuitProject.of("rom-load-target", main);
        project.putCircuit(child);
        editor.setProject(project, false);

        editor.openSubcircuit(cpuA);
        ComponentViewTarget target = editor.viewTarget(rom.id());
        editor.navigateBack();

        editor.loadMemory(target, List.of(
                LogicVector.fromUnsignedLong(0x11, 8), LogicVector.fromUnsignedLong(0x22, 8),
                LogicVector.fromUnsignedLong(0x33, 8), LogicVector.fromUnsignedLong(0x44, 8)));

        editor.openSubcircuit(cpuA);
        MemorySnapshot snapshot = editor.memorySnapshot(rom.id()).orElseThrow();
        assertEquals(0x33, snapshot.wordAt(2).toUnsignedLong().orElseThrow());
    }

    @Test
    void ramWriteThroughTheCapturedTargetWorksAfterNavigatingCompletelyAway() {
        CircuitDocument child = new CircuitDocument(new CircuitMetadata("Cpu", ""));
        ComponentInstance ram = ComponentInstance.create("memory.ram", new CircuitPoint(0, 0),
                ComponentRegistry.standard().require("memory.ram").definition().defaultParameters());
        child.addComponent(ram);

        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance cpuA = SubcircuitSupport.instantiate("Cpu", new CircuitPoint(0, 0)).withLabel("cpuA");
        main.addComponent(cpuA);

        CircuitProject project = CircuitProject.of("ram-target", main);
        project.putCircuit(child);
        editor.setProject(project, false);

        editor.openSubcircuit(cpuA);
        ComponentViewTarget target = editor.viewTarget(ram.id());
        editor.navigateBack();

        editor.writeMemoryWord(target, 5, LogicVector.fromUnsignedLong(0x99, 8));

        editor.openSubcircuit(cpuA);
        assertEquals(0x99, editor.memorySnapshot(ram.id()).orElseThrow()
                .wordAt(5).toUnsignedLong().orElseThrow());
    }
}
