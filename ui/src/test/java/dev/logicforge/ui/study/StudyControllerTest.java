package dev.logicforge.ui.study;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.SubcircuitSupport;
import dev.logicforge.compiler.CircuitCompiler;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.processor.lf8.Lf8ComputerFactory;
import dev.logicforge.processor.lf8.Lf8ImplementationMode;
import dev.logicforge.processor.lf8.Lf8Isa;
import dev.logicforge.simulation.Simulation;
import org.junit.jupiter.api.Test;

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
        var project = Lf8ComputerFactory.create(Lf8ImplementationMode.STRUCTURAL,
                Lf8Isa.HLT.opcode());
        var compiled = new CircuitCompiler(ComponentRegistry.standard()).compile(project, "main");
        Simulation simulation = new Simulation(compiled.circuit());
        StudyController controller = new StudyController(new StudyTarget.LiveInstance(
                project, compiled, simulation, "Structural LF-8"));

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

    private static void descendLabel(StudyController controller, String label) {
        ComponentInstance instance = controller.editor().document().components().stream()
                .filter(component -> label.equals(component.label()))
                .filter(component -> SubcircuitSupport.isInstanceDefinition(component.definitionId()))
                .findFirst().orElseThrow(() -> new AssertionError("Missing child " + label
                        + " in " + controller.editor().activeCircuitName()));
        controller.descend(instance);
    }
}
