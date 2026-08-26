package dev.logicforge.ui.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitMetadata;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.SubcircuitSupport;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.LibraryParameters;
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

    /**
     * A widget like MemoryView that stays open across navigation (unlike the Inspector,
     * which always re-derives its component from the currently selected instance) must
     * capture the hierarchy instance path active when it opened and keep using it — not the
     * editor's ambient "currently open circuit", which changes as the user navigates and
     * would otherwise silently start showing a different instance's memory.
     */
    @Test
    void explicitInstancePathKeepsResolvingTheSameInstanceAfterTheEditorNavigatesElsewhere() {
        CircuitDocument child = new CircuitDocument(new CircuitMetadata("Cpu", ""));
        ComponentInstance ram = ComponentInstance.create("memory.ram", new CircuitPoint(0, 0),
                ComponentRegistry.standard().require("memory.ram").definition().defaultParameters());
        child.addComponent(ram);

        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance cpuA = SubcircuitSupport.instantiate("Cpu", new CircuitPoint(0, 0)).withLabel("cpuA");
        ComponentInstance cpuB = SubcircuitSupport.instantiate("Cpu", new CircuitPoint(200, 0)).withLabel("cpuB");
        main.addComponent(cpuA);
        main.addComponent(cpuB);

        CircuitProject project = CircuitProject.of("memory-view-identity", main);
        project.putCircuit(child);
        editor.setProject(project, false);

        // Simulate opening a MemoryView on cpuA's RAM: capture the path the way MemoryView does.
        editor.openSubcircuit(cpuA);
        Optional<String> capturedPath = editor.activeInstancePath();
        editor.writeMemoryWord(capturedPath, ram.id(), 3, LogicVector.fromUnsignedLong(0xAB, 8));

        // The user navigates away to cpuB while the memory window stays open.
        editor.navigateBack();
        editor.openSubcircuit(cpuB);
        assertNotEquals(capturedPath, editor.activeInstancePath());

        // Resolving with the captured path must still show cpuA's RAM, not cpuB's (the
        // ambient overload, driven by the editor's now-current view, would show cpuB's).
        Optional<MemorySnapshot> stillCpuA = editor.memorySnapshot(capturedPath, ram.id());
        assertTrue(stillCpuA.isPresent());
        assertEquals(0xAB, stillCpuA.get().wordAt(3).toUnsignedLong().orElseThrow());

        Optional<MemorySnapshot> ambientCpuB = editor.memorySnapshot(ram.id());
        assertNotEquals(0xAB, ambientCpuB.get().wordAt(3).toUnsignedLong().orElse(-1),
                "the ambient (no-path) overload correctly reflects the now-active cpuB instead");
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

    /** Root main is always a live, directly-resolvable instance — never definition mode. */
    @Test
    void rootMainInstanceIsAlwaysALiveRuntimeContext() {
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        ComponentInstance ram = ComponentInstance.create("memory.ram", new CircuitPoint(0, 0),
                ComponentRegistry.standard().require("memory.ram").definition().defaultParameters());
        main.addComponent(ram);

        CircuitProject project = CircuitProject.of("root-live", main);
        editor.setProject(project, false);

        assertFalse(editor.isDefinitionMode());
        assertEquals(Optional.of(CircuitProject.MAIN_CIRCUIT), editor.activeInstancePath());
        editor.writeMemoryWord(ram.id(), 2, LogicVector.fromUnsignedLong(0x11, 8));
        assertEquals(0x11, editor.memorySnapshot(ram.id()).orElseThrow()
                .wordAt(2).toUnsignedLong().orElseThrow());
    }

    /**
     * A root-level component and a component buried inside a completely different circuit
     * definition can, in principle, carry the same document-local UUID (nothing prevents it —
     * UUIDs are only unique within one document by construction, not across documents). The
     * resolver must never let a nested instance's local UUID accidentally match an entry in
     * the root's flat, UUID-keyed source map; it must resolve nested lookups only through the
     * full hierarchical path.
     */
    @Test
    void duplicateUuidBetweenRootAndNestedInstanceDoesNotMisresolve() {
        ComponentInstance rootRam = ComponentInstance.create("memory.ram", new CircuitPoint(0, 0),
                ComponentRegistry.standard().require("memory.ram").definition().defaultParameters());
        java.util.UUID sharedId = rootRam.id();

        CircuitDocument child = new CircuitDocument(new CircuitMetadata("Cpu", ""));
        ComponentInstance childRam = ComponentInstance.create("memory.ram", new CircuitPoint(0, 0),
                ComponentRegistry.standard().require("memory.ram").definition().defaultParameters())
                .withId(sharedId);
        child.addComponent(childRam);

        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        main.addComponent(rootRam);
        ComponentInstance cpuA = SubcircuitSupport.instantiate("Cpu", new CircuitPoint(200, 0)).withLabel("cpuA");
        main.addComponent(cpuA);

        CircuitProject project = CircuitProject.of("duplicate-uuid", main);
        project.putCircuit(child);
        editor.setProject(project, false);

        // At root, sharedId resolves to the root-level RAM: write a distinguishing value.
        editor.writeMemoryWord(sharedId, 0, LogicVector.fromUnsignedLong(0xAA, 8));

        // Inside cpuA, the very same local UUID must resolve to cpuA's own RAM instead.
        editor.openSubcircuit(cpuA);
        editor.writeMemoryWord(sharedId, 0, LogicVector.fromUnsignedLong(0x55, 8));
        assertEquals(0x55, editor.memorySnapshot(sharedId).orElseThrow()
                .wordAt(0).toUnsignedLong().orElseThrow(), "must resolve to cpuA's nested instance");

        // Back at root, the root-level component must be untouched by the nested write.
        editor.navigateBack();
        assertEquals(0xAA, editor.memorySnapshot(sharedId).orElseThrow()
                .wordAt(0).toUnsignedLong().orElseThrow(),
                "the nested write under the same local UUID must not have leaked into the root component");
    }

    @Test
    void nestedUnconnectedPortUsesGeneratedFlattenedUuidDespiteRootCollision() {
        ComponentRegistry registry = ComponentRegistry.standard();
        ComponentInstance rootSource = ComponentInstance.create("routing.bus_constant",
                new CircuitPoint(0, 0),
                registry.require("routing.bus_constant").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8)
                        .with(LibraryParameters.BUS_CONSTANT_VALUE, "aa"));
        java.util.UUID sharedId = rootSource.id();

        CircuitDocument child = new CircuitDocument(new CircuitMetadata("Cpu", ""));
        ComponentInstance childSource = ComponentInstance.create("routing.bus_constant",
                new CircuitPoint(0, 0),
                registry.require("routing.bus_constant").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8)
                        .with(LibraryParameters.BUS_CONSTANT_VALUE, "3c"))
                .withId(sharedId);
        child.addComponent(childSource);

        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        main.addComponent(rootSource);
        ComponentInstance cpu = SubcircuitSupport.instantiate("Cpu", new CircuitPoint(200, 0));
        main.addComponent(cpu);
        CircuitProject project = CircuitProject.of("unconnected-port-collision", main);
        project.putCircuit(child);
        editor.setProject(project, false);

        var compilation = editor.compilation().orElseThrow();
        int rootRuntimeId = compilation.sourceMap().componentId(sharedId).orElseThrow();
        int rootNet = editor.netOf(new PortReference(sharedId, "OUT")).orElseThrow();

        editor.openSubcircuit(cpu);
        HierarchyRuntimeContext context = new HierarchyRuntimeContext(
                compilation, editor.activeInstancePath());
        int childRuntimeId = context.resolveComponent(sharedId).orElseThrow();
        assertNotEquals(rootRuntimeId, childRuntimeId,
                "the local child UUID must resolve through the concrete instance path");
        assertNotEquals(sharedId,
                compilation.sourceMap().componentUuid(childRuntimeId).orElseThrow(),
                "nested primitives use their deterministic flattened UUID");

        PortReference localPort = new PortReference(sharedId, "OUT");
        PortEndpoint whole = PortEndpoint.whole(localPort);
        PortEndpoint bit = PortEndpoint.bit(localPort, 2);
        PortEndpoint range = PortEndpoint.range(localPort, 5, 2);
        int childNet = context.resolveNet(whole).orElseThrow();

        assertNotEquals(rootNet, childNet, "the nested unconnected port must never bind root");
        assertEquals(childNet, context.resolveNet(bit).orElseThrow());
        assertEquals(childNet, context.resolveNet(range).orElseThrow());
        assertEquals(LogicVector.fromUnsignedLong(0x3c, 8), editor.valueAt(whole).orElseThrow());
        assertEquals(LogicVector.ONE, editor.valueAt(bit).orElseThrow());
        assertEquals(LogicVector.fromUnsignedLong(0xf, 4), editor.valueAt(range).orElseThrow());
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
