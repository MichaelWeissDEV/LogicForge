package dev.logicforge.processor.lf8.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.compiler.CircuitCompiler;
import dev.logicforge.compiler.RuntimeInstancePath;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.processor.lf8.Lf8CircuitFactory;
import dev.logicforge.processor.lf8.Lf8ComponentRoles;
import dev.logicforge.processor.lf8.Lf8ComputerFactory;
import dev.logicforge.simulation.Simulation;
import dev.logicforge.logic.LogicState;
import org.junit.jupiter.api.Test;

class Lf8RuntimeProbeTest {

    @Test
    void architecturalResolutionSurvivesEveryDisplayLabelBeingRenamed() {
        var project = Lf8ComputerFactory.create(0xff);
        project.circuits().forEach(circuit -> circuit.components().stream().toList()
                .forEach(component -> circuit.replaceComponent(component.withLabel("renamed"))));
        var compiled = new CircuitCompiler(ComponentRegistry.standard())
                .compile(project, CircuitProject.MAIN_CIRCUIT);
        var cpu = project.mainCircuit().components().stream()
                .filter(component -> Lf8ComponentRoles.CPU.equals(component.semanticRole()))
                .findFirst().orElseThrow();
        var probe = new Lf8RuntimeProbe(project, compiled, new Simulation(compiled.circuit()),
                RuntimeInstancePath.root(CircuitProject.MAIN_CIRCUIT).child(cpu.id()));

        assertTrue(probe.pc().isPresent());
        assertTrue(probe.sp().isPresent());
        assertTrue(probe.ir().isPresent());
        assertTrue(probe.registerFile().isPresent());
        assertTrue(probe.microstep().isPresent());
        assertEquals(Lf8CircuitFactory.CPU_CIRCUIT,
                project.circuit(Lf8CircuitFactory.CPU_CIRCUIT).orElseThrow().metadata().name());
    }

    @Test
    void clockControlUsesRealScheduledEdgeWithoutOverwritingClockState() {
        var project = Lf8ComputerFactory.create(0xff);
        var clock = project.mainCircuit().components().stream()
                .filter(component -> "CLK".equals(component.label())).findFirst().orElseThrow();
        var clockParameters = ComponentRegistry.standard().require("source.clock")
                .definition().defaultParameters();
        project.mainCircuit().replaceComponent(clock.withDefinitionId("source.clock")
                .withParameters(clockParameters));
        var compiled = new CircuitCompiler(ComponentRegistry.standard())
                .compile(project, CircuitProject.MAIN_CIRCUIT);
        var cpu = project.mainCircuit().components().stream()
                .filter(component -> Lf8ComponentRoles.CPU.equals(component.semanticRole()))
                .findFirst().orElseThrow();
        var simulation = new Simulation(compiled.circuit());
        var control = ClockControl.discover(compiled, simulation,
                RuntimeInstancePath.root(CircuitProject.MAIN_CIRCUIT).child(cpu.id()))
                .orElseThrow();

        assertEquals(ClockControl.Mode.SCHEDULED_CLOCK, control.mode());
        long before = simulation.currentTime();
        assertTrue(control.stepActiveEdge());
        assertTrue(simulation.currentTime() > before);
    }

    @Test
    void pcBreakpointTriggersOnceAtAnInstructionBoundary() {
        var project = Lf8ComputerFactory.create(0x01, 0, 0x2a, 0xff);
        var compiled = new CircuitCompiler(ComponentRegistry.standard())
                .compile(project, CircuitProject.MAIN_CIRCUIT);
        var cpu = project.mainCircuit().components().stream()
                .filter(component -> Lf8ComponentRoles.CPU.equals(component.semanticRole()))
                .findFirst().orElseThrow();
        var simulation = new Simulation(compiled.circuit());
        var path = RuntimeInstancePath.root(CircuitProject.MAIN_CIRCUIT).child(cpu.id());
        var probe = new Lf8RuntimeProbe(project, compiled, simulation, path);
        simulation.setInput(probe.inputSource("RESET").orElseThrow(), LogicState.ONE);
        simulation.setInput(probe.inputSource("RESET").orElseThrow(), LogicState.ZERO);
        var clock = ClockControl.discover(compiled, simulation, path).orElseThrow();
        var breakpoints = new BreakpointEngine();
        breakpoints.setBreakpoints(java.util.List.of(new PcBreakpoint(path, 3)));

        BreakpointEngine.BreakpointHit hit = null;
        for (int edge = 0; edge < 32 && hit == null; edge++) {
            assertTrue(clock.stepActiveEdge());
            hit = breakpoints.evaluate(probe).orElse(null);
        }
        assertEquals(3, hit.address());
        assertTrue(breakpoints.evaluate(probe).isEmpty(), "held boundary must not retrigger");
    }
}
