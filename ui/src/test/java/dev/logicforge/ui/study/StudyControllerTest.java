package dev.logicforge.ui.study;

import dev.logicforge.processor.lf8.runtime.Lf8RuntimeProbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitMetadata;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.ElectricalEndpoint;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.SubcircuitSupport;
import dev.logicforge.compiler.RuntimeInstancePath;
import dev.logicforge.compiler.CircuitCompiler;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.processor.lf8.Lf8ComputerFactory;
import dev.logicforge.processor.lf8.Lf8ImplementationMode;
import dev.logicforge.processor.lf8.Lf8Isa;
import dev.logicforge.simulation.Simulation;
import dev.logicforge.ui.edit.CircuitEditor;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class StudyControllerTest {

    @Test
    void liveTargetSharesTheExactSimulationAndStepsToInstructionBoundaries() {
        var project = Lf8ComputerFactory.create(
                Lf8Isa.LDI.opcode(), 0, 0x42, Lf8Isa.HLT.opcode());
        var compiled = new CircuitCompiler(ComponentRegistry.standard()).compile(project, "main");
        Simulation simulation = new Simulation(compiled.circuit());
        int reset = compiled.componentByLabel("RESET").orElseThrow();
        simulation.setInput(reset, LogicState.ONE);
        simulation.setInput(reset, LogicState.ZERO);
        StudyController controller = new StudyController(new StudyTarget.LiveInstance(
                project, compiled, simulation, "LF-8"));

        assertSame(simulation, controller.editor().simulation().orElseThrow());
        assertTrue(controller.stepInstruction(), "first boundary completes RESET vector fetch");
        assertTrue(controller.stepInstruction(), "second boundary completes LDI");
        int pc = compiled.componentByLabel("PC").orElseThrow();
        assertEquals(LogicVector.fromUnsignedLong(3, 16), simulation.readOutput(pc, 0));
    }

    @Test
    void navigationPreservesConcreteLogicAndStatePathsAndSupportsForward() {
        var project = Lf8ComputerFactory.create(Lf8ImplementationMode.GATE_LEVEL,
                Lf8Isa.HLT.opcode());
        var compiled = new CircuitCompiler(ComponentRegistry.standard()).compile(project, "main");
        Simulation simulation = new Simulation(compiled.circuit());
        StudyController controller = new StudyController(new StudyTarget.LiveInstance(
                project, compiled, simulation, "Gate-level LF-8"));

        descendLabel(controller, "CPU");
        descendLabel(controller, "DATAPATH");
        descendLabel(controller, "ALU");
        descendLabel(controller, "ALU8");
        descendLabel(controller, "RIPPLE_ADDER8");
        descendLabel(controller, "FULL_ADDER_0");
        descendLabel(controller, "HALF_ADDER_1");
        assertEquals("STRUCT_HALF_ADDER", controller.editor().activeCircuitName());
        assertTrue(controller.editor().activeInstancePath().orElseThrow().split("/").length >= 8);

        controller.back();
        assertEquals("STRUCT_FULL_ADDER", controller.editor().activeCircuitName());
        assertTrue(controller.canForward());
        controller.forward();
        assertEquals("STRUCT_HALF_ADDER", controller.editor().activeCircuitName());
        assertFalse(controller.canForward());

        while (controller.canBack()) {
            controller.back();
        }
        descendLabel(controller, "CPU");
        descendLabel(controller, "DATAPATH");
        descendLabel(controller, "REGISTER_FILE");
        descendLabel(controller, "REGISTER_0");
        descendLabel(controller, "DFF_0");
        descendLabel(controller, "MASTER_LATCH");
        descendLabel(controller, "SR_LATCH");
        assertEquals("STRUCT_SR_LATCH_NOR", controller.editor().activeCircuitName());
        assertTrue(controller.editor().activeInstancePath().orElseThrow().split("/").length >= 8);
    }

    @Test
    void twoCpuInstancesAreProbedAndStudiedIndependently() {
        CircuitProject cpuA = Lf8ComputerFactory.create(
                Lf8Isa.LDI.opcode(), 0, 0x11, Lf8Isa.HLT.opcode());
        CircuitProject cpuB = Lf8ComputerFactory.create(
                Lf8Isa.LDI.opcode(), 0, 0x21,
                Lf8Isa.INC.opcode(), 0, Lf8Isa.HLT.opcode());
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", "two LF-8s"));
        UUID cpuAId = copyComputer(cpuA.mainCircuit(), main, "CPU_A", 0);
        UUID cpuBId = copyComputer(cpuB.mainCircuit(), main, "CPU_B", 1200);
        cpuA.putCircuit(main);

        var compiled = new CircuitCompiler(ComponentRegistry.standard()).compile(cpuA, "main");
        Simulation simulation = new Simulation(compiled.circuit());
        RuntimeInstancePath pathA = RuntimeInstancePath.root("main").child(cpuAId);
        RuntimeInstancePath pathB = RuntimeInstancePath.root("main").child(cpuBId);
        Lf8RuntimeProbe probeA = new Lf8RuntimeProbe(cpuA, compiled, simulation, pathA);
        Lf8RuntimeProbe probeB = new Lf8RuntimeProbe(cpuA, compiled, simulation, pathB);

        pulseReset(simulation, probeA);
        pulseReset(simulation, probeB);
        StudyController studyA = study(cpuA, compiled, simulation, pathA, "CPU A");
        StudyController studyB = study(cpuA, compiled, simulation, pathB, "CPU B");
        assertTrue(studyA.stepInstruction());
        assertTrue(studyA.stepInstruction());
        assertTrue(studyB.stepInstruction());
        assertTrue(studyB.stepInstruction());
        assertTrue(studyB.stepInstruction());

        assertEquals(3, unsigned(probeA.pc().orElseThrow()));
        assertEquals(5, unsigned(probeB.pc().orElseThrow()));
        assertEquals(0x11, unsigned(probeA.registerFile().orElseThrow().registers().getFirst()));
        assertEquals(0x22, unsigned(probeB.registerFile().orElseThrow().registers().getFirst()));
        assertEquals(0xBFFF, unsigned(probeA.sp().orElseThrow()));
        assertEquals(0xBFFF, unsigned(probeB.sp().orElseThrow()));
        assertEquals(Lf8Isa.LDI.opcode(), unsigned(probeA.ir().orElseThrow()));
        assertEquals(Lf8Isa.INC.opcode(), unsigned(probeB.ir().orElseThrow()));
        assertEquals(0, unsigned(probeA.microstep().orElseThrow()));
        assertEquals(0, unsigned(probeB.microstep().orElseThrow()));

        // Both targets hold the same project/runtime, but retain inverse CPU roots.
        assertEquals(pathA.toString(), studyA.lf8Probe().orElseThrow().cpuPath().toString());
        assertEquals(pathB.toString(), studyB.lf8Probe().orElseThrow().cpuPath().toString());
    }

    @Test
    void workbenchAndStudyEditorsShareRunPauseWithoutOwningEachOthersLifetime() {
        CircuitEditor workbench = new CircuitEditor(ComponentRegistry.standard());
        workbench.setProject(Lf8ComputerFactory.create(Lf8Isa.HLT.opcode()), false);
        StudyController study = new StudyController(StudyTarget.live(workbench));

        study.setRunning(false);
        assertFalse(workbench.isRunning());
        assertFalse(study.editor().isRunning());

        workbench.setRunning(true);
        assertTrue(workbench.isRunning());
        assertTrue(study.editor().isRunning());
        assertSame(workbench.simulation().orElseThrow(), study.editor().simulation().orElseThrow());
    }

    private static StudyController study(CircuitProject project,
            dev.logicforge.compiler.CompilationResult compiled, Simulation simulation,
            RuntimeInstancePath path, String label) {
        return new StudyController(new StudyTarget.LiveInstance(project, compiled, simulation,
                "LF8_CPU", Optional.of(path.toString()), Optional.empty(), label));
    }

    private static void pulseReset(Simulation simulation, Lf8RuntimeProbe probe) {
        int reset = probe.inputSource("RESET").orElseThrow();
        simulation.setInput(reset, LogicState.ONE);
        simulation.setInput(reset, LogicState.ZERO);
    }

    private static long unsigned(LogicVector value) {
        return value.toUnsignedLong().orElseThrow();
    }

    /** Copies a complete top-level LF-8 computer with fresh identities into one main. */
    private static UUID copyComputer(CircuitDocument source, CircuitDocument target,
                                     String cpuLabel, double xOffset) {
        Map<UUID, UUID> ids = new LinkedHashMap<>();
        UUID cpuId = null;
        for (ComponentInstance component : source.components()) {
            UUID id = UUID.randomUUID();
            ids.put(component.id(), id);
            ComponentInstance copy = component.withId(id)
                    .withPosition(component.position().plus(xOffset, 0));
            if (component.label().equals("CPU")) {
                copy = copy.withLabel(cpuLabel);
                cpuId = id;
            }
            target.addComponent(copy);
        }
        for (Connection connection : source.connections()) {
            target.addConnection(new Connection(UUID.randomUUID(),
                    remap(connection.from(), ids), remap(connection.to(), ids),
                    connection.waypoints().stream()
                            .map(point -> point.plus(xOffset, 0)).toList()));
        }
        if (cpuId == null) {
            throw new AssertionError("LF-8 main circuit has no CPU instance");
        }
        return cpuId;
    }

    private static ElectricalEndpoint remap(ElectricalEndpoint endpoint, Map<UUID, UUID> ids) {
        return switch (endpoint) {
            case ElectricalEndpoint.ComponentEndpoint component -> {
                PortEndpoint port = component.port();
                yield new ElectricalEndpoint.ComponentEndpoint(new PortEndpoint(
                        new PortReference(ids.get(port.componentId()), port.portName()), port.slice()));
            }
            case ElectricalEndpoint.ChipPinEndpoint chip -> new ElectricalEndpoint.ChipPinEndpoint(
                    ids.get(chip.chipInstanceId()), chip.physicalPinNumber());
        };
    }

    private static void descendLabel(StudyController controller, String label) {
        ComponentInstance instance = controller.editor().document().components().stream()
                .filter(component -> label.equals(component.label()))
                .filter(component -> SubcircuitSupport.isInstanceDefinition(component.definitionId()))
                .findFirst().orElseThrow(() -> new AssertionError("Missing child " + label
                        + " in " + controller.editor().activeCircuitName()));
        controller.descend(instance);
    }
}
