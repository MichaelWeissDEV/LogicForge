package dev.logicforge.ui.study;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.compiler.CircuitCompiler;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.logic.LogicState;
import dev.logicforge.processor.lf8.Lf8ComputerFactory;
import dev.logicforge.processor.lf8.Lf8Isa;
import dev.logicforge.processor.lf8.runtime.Breakpoint;
import dev.logicforge.processor.lf8.runtime.PcBreakpoint;
import dev.logicforge.simulation.Simulation;
import org.junit.jupiter.api.Test;

/**
 * The Study window's breakpoint UI is a thin wrapper over the existing headless {@code
 * BreakpointEngine} (see {@code processor-lf8-runtime}'s own tests for the engine's own
 * matching rules) — these tests exercise the wrapper itself: adding/removing/toggling
 * through {@link StudyController}, and {@link StudyController#advanceWhileRunning()}
 * actually driving the LF-8's own manually-clocked {@code CLK} forward and pausing the
 * shared run control on a hit, the same way a fired logic analyzer trigger does.
 *
 * <p>{@code advanceWhileRunning} is bounded per call (one Study refresh tick's worth), so
 * these tests call it repeatedly — exactly like the real refresh loop — until it stops
 * running or a safety cap is hit.
 */
class StudyBreakpointTest {

    private static final int MAX_TICKS = 20;

    @Test
    void pcBreakpointStopsTheRunAtTheTargetAddress() {
        var project = Lf8ComputerFactory.create(
                Lf8Isa.LDI.opcode(), 0, 0x42, Lf8Isa.HLT.opcode());
        var compiled = new CircuitCompiler(ComponentRegistry.standard()).compile(project, "main");
        Simulation simulation = new Simulation(compiled.circuit());
        int reset = compiled.componentByLabel("RESET").orElseThrow();
        simulation.setInput(reset, LogicState.ONE);
        simulation.setInput(reset, LogicState.ZERO);
        StudyController controller = new StudyController(new StudyTarget.LiveInstance(
                project, compiled, simulation, "LF-8"));

        assertTrue(controller.canAddBreakpoint());
        controller.addPcBreakpoint(3);
        assertEquals(1, controller.breakpoints().size());

        controller.editor().setRunning(true);
        boolean hit = runUntilStopped(controller);

        assertTrue(hit, "the breakpoint must have paused the run before MAX_STEPS elapsed");
        assertFalse(controller.editor().isRunning(), "a hit pauses the shared run control");
        assertEquals(3, controller.lastBreakpointHit().orElseThrow().address());
    }

    @Test
    void disabledBreakpointNeverStopsTheRun() {
        var project = Lf8ComputerFactory.create(
                Lf8Isa.LDI.opcode(), 0, 0x42, Lf8Isa.HLT.opcode());
        var compiled = new CircuitCompiler(ComponentRegistry.standard()).compile(project, "main");
        Simulation simulation = new Simulation(compiled.circuit());
        int reset = compiled.componentByLabel("RESET").orElseThrow();
        simulation.setInput(reset, LogicState.ONE);
        simulation.setInput(reset, LogicState.ZERO);
        StudyController controller = new StudyController(new StudyTarget.LiveInstance(
                project, compiled, simulation, "LF-8"));

        controller.addPcBreakpoint(3);
        Breakpoint added = controller.breakpoints().get(0);
        controller.setBreakpointEnabled(added, false);

        controller.editor().setRunning(true);
        runUntilStopped(controller);

        assertTrue(controller.lastBreakpointHit().isEmpty(),
                "a disabled breakpoint must never pause the run — the program halting on its "
                        + "own is fine, but not attributed to this breakpoint");
    }

    @Test
    void removingABreakpointClearsAPriorHitRecordedAgainstIt() {
        var project = Lf8ComputerFactory.create(
                Lf8Isa.LDI.opcode(), 0, 0x42, Lf8Isa.HLT.opcode());
        var compiled = new CircuitCompiler(ComponentRegistry.standard()).compile(project, "main");
        Simulation simulation = new Simulation(compiled.circuit());
        int reset = compiled.componentByLabel("RESET").orElseThrow();
        simulation.setInput(reset, LogicState.ONE);
        simulation.setInput(reset, LogicState.ZERO);
        StudyController controller = new StudyController(new StudyTarget.LiveInstance(
                project, compiled, simulation, "LF-8"));

        controller.addPcBreakpoint(3);
        Breakpoint added = controller.breakpoints().get(0);
        controller.editor().setRunning(true);
        assertTrue(runUntilStopped(controller));
        assertTrue(controller.lastBreakpointHit().isPresent());

        controller.removeBreakpoint(added);

        assertTrue(controller.breakpoints().isEmpty());
        assertTrue(controller.lastBreakpointHit().isEmpty());
    }

    @Test
    void aRuntimePathThatMatchesNoBreakpointNeverPausesEvenWhenBreakpointsExist() {
        var project = Lf8ComputerFactory.create(
                Lf8Isa.LDI.opcode(), 0, 0x42, Lf8Isa.HLT.opcode());
        var compiled = new CircuitCompiler(ComponentRegistry.standard()).compile(project, "main");
        Simulation simulation = new Simulation(compiled.circuit());
        int reset = compiled.componentByLabel("RESET").orElseThrow();
        simulation.setInput(reset, LogicState.ONE);
        simulation.setInput(reset, LogicState.ZERO);
        StudyController controller = new StudyController(new StudyTarget.LiveInstance(
                project, compiled, simulation, "LF-8"));

        // A PC that this tiny program never reaches.
        controller.addPcBreakpoint(0x1234);

        controller.editor().setRunning(true);
        runUntilStopped(controller);

        assertTrue(controller.lastBreakpointHit().isEmpty(),
                "the program halting on its own must not be mistaken for a breakpoint hit");
    }

    @Test
    void runningWithNoBreakpointsFreeRunsTheClockUntilTheCpuHalts() {
        var project = Lf8ComputerFactory.create(
                Lf8Isa.LDI.opcode(), 0, 0x42, Lf8Isa.HLT.opcode());
        var compiled = new CircuitCompiler(ComponentRegistry.standard()).compile(project, "main");
        Simulation simulation = new Simulation(compiled.circuit());
        int reset = compiled.componentByLabel("RESET").orElseThrow();
        simulation.setInput(reset, LogicState.ONE);
        simulation.setInput(reset, LogicState.ZERO);
        StudyController controller = new StudyController(new StudyTarget.LiveInstance(
                project, compiled, simulation, "LF-8"));

        assertTrue(controller.breakpoints().isEmpty());
        controller.editor().setRunning(true);
        boolean stopped = runUntilStopped(controller);

        assertTrue(stopped, "Run must drive CLK on its own — LF-8's CLK is a manual toggle, "
                + "not a scheduled clock, so nothing else advances it");
        assertTrue(controller.lastBreakpointHit().isEmpty(), "stopped by HALT, not a breakpoint");
    }

    @Test
    void advanceWhileRunningIsANoOpForAReferenceImplementationTarget() {
        StudyController controller = new StudyController(new StudyTarget.ReferenceImplementation(
                Lf8ComputerFactory.create(Lf8Isa.HLT.opcode()), "main", "reference"));
        assertFalse(controller.canAddBreakpoint());
        controller.advanceWhileRunning();
        assertTrue(controller.lastBreakpointHit().isEmpty());
    }

    private static boolean runUntilStopped(StudyController controller) {
        for (int tick = 0; tick < MAX_TICKS && controller.editor().isRunning(); tick++) {
            controller.advanceWhileRunning();
        }
        return !controller.editor().isRunning();
    }
}
