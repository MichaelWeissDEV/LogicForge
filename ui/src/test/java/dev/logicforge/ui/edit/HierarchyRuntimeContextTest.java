package dev.logicforge.ui.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitMetadata;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.SubcircuitSupport;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.MemorySnapshot;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Proves the bug described by the hierarchy runtime context work: a component inside a
 * child circuit used to be resolved by its local document UUID alone, which is ambiguous
 * once the same child circuit is instantiated more than once. These tests drive the editor
 * exactly the way the UI does — open a subcircuit instance, then call the runtime APIs with
 * the local component id — and would fail against the old implementation.
 */
class HierarchyRuntimeContextTest {

    private final CircuitEditor editor = new CircuitEditor(ComponentRegistry.standard());

    @Test
    void twoInstancesOfTheSameChildCircuitHaveIndependentMemory() {
        CircuitDocument child = new CircuitDocument(new CircuitMetadata("Cpu", ""));
        ComponentInstance ram = ComponentInstance.create("memory.ram", new CircuitPoint(0, 0),
                ComponentRegistry.standard().require("memory.ram").definition().defaultParameters());
        child.addComponent(ram);

        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance cpuA = SubcircuitSupport.instantiate("Cpu", new CircuitPoint(0, 0)).withLabel("cpuA");
        ComponentInstance cpuB = SubcircuitSupport.instantiate("Cpu", new CircuitPoint(200, 0)).withLabel("cpuB");
        main.addComponent(cpuA);
        main.addComponent(cpuB);

        CircuitProject project = CircuitProject.of("two-cpus", main);
        project.putCircuit(child);
        editor.setProject(project, false);

        editor.openSubcircuit(cpuA);
        assertTrue(editor.activeInstancePath().isPresent());
        editor.writeMemoryWord(ram.id(), 3, LogicVector.fromUnsignedLong(0xAB, 8));
        Optional<MemorySnapshot> afterWriteToA = editor.memorySnapshot(ram.id());
        assertTrue(afterWriteToA.isPresent());
        assertEquals(0xAB, afterWriteToA.get().wordAt(3).toUnsignedLong().orElseThrow());

        editor.navigateBack();
        editor.openSubcircuit(cpuB);
        Optional<MemorySnapshot> cpuBMemory = editor.memorySnapshot(ram.id());
        assertTrue(cpuBMemory.isPresent(), "cpuB's own RAM instance must resolve independently");
        assertNotEquals(0xAB, cpuBMemory.get().wordAt(3).toUnsignedLong().orElse(-1),
                "writing cpuA's RAM must not be visible through cpuB's local UUID");
    }

    @Test
    void definitionModeHasNoRuntimeInstance() {
        CircuitDocument child = new CircuitDocument(new CircuitMetadata("Cpu", ""));
        ComponentInstance ram = ComponentInstance.create("memory.ram", new CircuitPoint(0, 0),
                ComponentRegistry.standard().require("memory.ram").definition().defaultParameters());
        child.addComponent(ram);

        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance cpuA = SubcircuitSupport.instantiate("Cpu", new CircuitPoint(0, 0)).withLabel("cpuA");
        main.addComponent(cpuA);

        CircuitProject project = CircuitProject.of("definition-mode", main);
        project.putCircuit(child);
        editor.setProject(project, false);

        editor.openCircuit("Cpu");

        assertTrue(editor.isDefinitionMode());
        assertTrue(editor.activeInstancePath().isEmpty());
        assertTrue(editor.memorySnapshot(ram.id()).isEmpty(),
                "a definition with 0, 1 or many instances has no single live memory to show");
        assertTrue(editor.debugSnapshot(ram.id()).isEmpty());
    }

    @Test
    void nestedHierarchyResolvesThroughMultipleLevels() {
        CircuitDocument innermost = new CircuitDocument(new CircuitMetadata("Cpu", ""));
        ComponentInstance ram = ComponentInstance.create("memory.ram", new CircuitPoint(0, 0),
                ComponentRegistry.standard().require("memory.ram").definition().defaultParameters());
        innermost.addComponent(ram);

        CircuitDocument wrapper = new CircuitDocument(new CircuitMetadata("Board", ""));
        ComponentInstance cpuInBoard = SubcircuitSupport.instantiate("Cpu", new CircuitPoint(0, 0)).withLabel("cpu");
        wrapper.addComponent(cpuInBoard);

        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance boardInMain = SubcircuitSupport.instantiate("Board", new CircuitPoint(0, 0)).withLabel("board");
        main.addComponent(boardInMain);

        CircuitProject project = CircuitProject.of("nested", main);
        project.putCircuit(wrapper);
        project.putCircuit(innermost);
        editor.setProject(project, false);

        editor.openSubcircuit(boardInMain);
        editor.openSubcircuit(cpuInBoard);
        assertEquals("main/" + boardInMain.id() + "/" + cpuInBoard.id(), editor.activeInstancePath().orElseThrow());

        editor.writeMemoryWord(ram.id(), 5, LogicVector.fromUnsignedLong(0x42, 8));
        assertEquals(0x42, editor.memorySnapshot(ram.id()).orElseThrow()
                .wordAt(5).toUnsignedLong().orElseThrow());
    }

    @Test
    void renamingTheChildCircuitDoesNotBreakTheLiveInstance() {
        CircuitDocument child = new CircuitDocument(new CircuitMetadata("Cpu", ""));
        ComponentInstance ram = ComponentInstance.create("memory.ram", new CircuitPoint(0, 0),
                ComponentRegistry.standard().require("memory.ram").definition().defaultParameters());
        child.addComponent(ram);

        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance cpuA = SubcircuitSupport.instantiate("Cpu", new CircuitPoint(0, 0)).withLabel("cpuA");
        main.addComponent(cpuA);

        CircuitProject project = CircuitProject.of("rename", main);
        project.putCircuit(child);
        editor.setProject(project, false);

        editor.openSubcircuit(cpuA);
        String pathBefore = editor.activeInstancePath().orElseThrow();
        editor.writeMemoryWord(ram.id(), 1, LogicVector.fromUnsignedLong(0x77, 8));

        editor.renameCircuit("Cpu", "CentralProcessor");

        assertEquals("CentralProcessor", editor.activeCircuitName());
        assertEquals(pathBefore, editor.activeInstancePath().orElseThrow(),
                "hierarchy paths are built from instance UUIDs, never circuit names");
        assertEquals(0x77, editor.memorySnapshot(ram.id()).orElseThrow()
                .wordAt(1).toUnsignedLong().orElseThrow());
    }

    @Test
    void watchOnOneInstanceStaysUnresolvedOnceThatInstanceIsGoneRatherThanRebindingToTheOtherOne() {
        CircuitDocument child = new CircuitDocument(new CircuitMetadata("Cpu", ""));
        ComponentInstance ram = ComponentInstance.create("memory.ram", new CircuitPoint(0, 0),
                ComponentRegistry.standard().require("memory.ram").definition().defaultParameters());
        child.addComponent(ram);

        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance cpuA = SubcircuitSupport.instantiate("Cpu", new CircuitPoint(0, 0)).withLabel("cpuA");
        ComponentInstance cpuB = SubcircuitSupport.instantiate("Cpu", new CircuitPoint(200, 0)).withLabel("cpuB");
        main.addComponent(cpuA);
        main.addComponent(cpuB);

        CircuitProject project = CircuitProject.of("watch-identity", main);
        project.putCircuit(child);
        editor.setProject(project, false);

        LogicAnalyzerController analyzer = new LogicAnalyzerController(editor);
        editor.openSubcircuit(cpuA);
        assertTrue(analyzer.canWatch());
        analyzer.addSignal(new dev.logicforge.circuit.document.PortReference(ram.id(), "WE"), "cpuA.RAM.WE");
        assertEquals(1, analyzer.watchedSignals().size());
        String watchedPath = analyzer.watchedSignals().get(0).hierarchyPath();
        assertTrue(watchedPath.startsWith("main/" + cpuA.id() + "/"));

        editor.navigateBack();
        editor.execute(new dev.logicforge.ui.command.RemoveElementsCommand(main,
                java.util.List.of(cpuA.id()), java.util.List.of()));

        assertFalse(analyzer.traceFor(analyzer.watchedSignals().get(0)).isPresent(),
                "the watched instance is gone; the trace must not silently reattach to cpuB");
    }
}
