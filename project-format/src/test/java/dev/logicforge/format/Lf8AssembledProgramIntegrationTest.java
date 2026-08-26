package dev.logicforge.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.compiler.CircuitCompiler;
import dev.logicforge.compiler.CompilationResult;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.processor.lf8.Lf8ComputerFactory;
import dev.logicforge.processor.lf8.Lf8MemoryMap;
import dev.logicforge.processor.lf8.Lf8ImplementationMode;
import dev.logicforge.simulation.Simulation;
import dev.logicforge.tools.lf8.Lf8Assembler;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Confirms {@code lf8-tools}' assembler output actually runs on the real LF-8 circuit. */
class Lf8AssembledProgramIntegrationTest {

    @TempDir
    Path directory;

    @Test
    void assembledSourceRunsOnTheRealCircuitAndProducesTheExpectedResult() {
        int[] program = Lf8Assembler.assemble("""
                start:
                    LDI R0, 5
                    LDI R1, 3
                    ADD R0, R1
                    CMP R0, R2
                    JZ done
                    STORE R0, 0x8000
                    JMP finish

                done:
                    LDI R0, 0
                    STORE R0, 0x8000

                finish:
                    HLT
                """);

        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        assertEquals(LogicVector.fromUnsignedLong(8, 8),
                simulation.memoryPage(probe.ramId(), 0, 1).orElseThrow().wordAt(0),
                "5 + 3 = 8, not equal to R2 (0), so the JZ must not have been taken");
    }

    @Test
    void assembledSubroutineWithStackAndCallRunsOnTheRealCircuit() {
        int[] program = Lf8Assembler.assemble("""
                    LDI R0, 1
                    PUSH R0
                    CALL addTen
                    POP R1
                    ADD R0, R1
                    STORE R0, 0x8000
                    HLT

                addTen:
                    LDI R1, 10
                    ADD R0, R1
                    RET
                """);

        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        assertEquals(LogicVector.fromUnsignedLong(12, 8),
                simulation.memoryPage(probe.ramId(), 0, 1).orElseThrow().wordAt(0),
                "R0 starts at 1, addTen makes it 11, then + the pushed/popped 1 = 12");
    }

    @Test
    void thousandsOfInstructionsWithCallsAndMemoryWritesDoNotFalselyOscillate() {
        // Regression test for a simulation-core bug where setInput() advancing `time`
        // directly (without resetting the per-timestamp delta-cycle counter the way step()
        // does) let SimulationOscillationException fire on a perfectly healthy circuit once
        // enough clock edges had accumulated, regardless of whether anything was actually
        // oscillating. This program runs a nested loop (15 outer x 40 inner = 600
        // iterations), each inner iteration doing a CALL into a subroutine that itself
        // PUSHes/POPs and does a RAM read-modify-write, for several thousand executed
        // instructions and tens of thousands of clock edges - an order of magnitude past
        // the ~400-edge point where the bug used to fire.
        int[] program = Lf8Assembler.assemble("""
                    LDI R0, 0
                    STORE R0, 0x8000    ; running total

                    LDI R1, 15          ; outer count
                outer:
                    STORE R1, 0x8001
                    LDI R2, 40          ; inner count
                inner:
                    CALL bump
                    DEC R2
                    JNZ inner

                    LOAD R1, 0x8001
                    DEC R1
                    JNZ outer
                    HLT

                bump:
                    PUSH R0
                    LOAD R0, 0x8000
                    INC R0
                    STORE R0, 0x8000
                    POP R0
                    RET
                """);

        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe, 100_000);

        assertEquals(LogicVector.fromUnsignedLong(15 * 40, 8),
                simulation.memoryPage(probe.ramId(), 0, 1).orElseThrow().wordAt(0),
                "the subroutine must have run exactly outer*inner times");
        int spId = compiled.componentByLabel("SP").orElseThrow();
        assertEquals(LogicVector.fromUnsignedLong(0xbfff, 16), simulation.readOutput(spId, 0),
                "600 balanced CALL/PUSH/POP/RET cycles must leave SP back at its reset value");
    }

    @Test
    void timerRaisesThreeRealInterruptsThroughMmioAndLeavesTheStackBalanced() {
        int[] program = Lf8Assembler.assemble("""
                    LDI R0, 0
                    STORE R0, 0x8000
                    LDI R0, 120
                    STORE R0, %d
                    LDI R0, 0
                    STORE R0, %d
                    LDI R0, 7
                    STORE R0, %d
                    EI

                wait:
                    LOAD R1, 0x8000
                    LDI R2, 3
                    CMP R1, R2
                    JNZ wait
                    DI
                    LDI R0, 0
                    STORE R0, %d
                    HLT

                .org 0x0100
                handler:
                    LOAD R1, 0x8000
                    INC R1
                    STORE R1, 0x8000
                    LDI R0, 1
                    STORE R0, %d
                    IRET
                """.formatted(
                Lf8MemoryMap.TIMER_RELOAD_LOW,
                Lf8MemoryMap.TIMER_RELOAD_HIGH,
                Lf8MemoryMap.TIMER_CONTROL,
                Lf8MemoryMap.TIMER_CONTROL,
                Lf8MemoryMap.TIMER_STATUS));

        CircuitProject project = Lf8ComputerFactory.createWithVectors(program, 0x0100, 0, 0);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe, 5_000);

        assertEquals(LogicVector.fromUnsignedLong(3, 8),
                simulation.memoryPage(probe.ramId(), 0, 1).orElseThrow().wordAt(0),
                "the real timer IRQ handler must run exactly three times");
        int spId = compiled.componentByLabel("SP").orElseThrow();
        int ieId = compiled.componentByLabel("IE_REGISTER").orElseThrow();
        int timerId = compiled.componentByLabel("TIMER").orElseThrow();
        assertEquals(LogicVector.fromUnsignedLong(0xbfff, 16), simulation.readOutput(spId, 0));
        assertEquals(LogicVector.ZERO, simulation.readOutput(ieId, 0),
                "the final DI must leave interrupts disabled");
        assertEquals(LogicVector.ZERO, simulation.readOutput(timerId, 1),
                "disabling the timer must deassert its IRQ output");
    }

    @Test
    void structuralLf8RunsTheSameProgramThroughTheDeepAdderHierarchy() {
        int[] program = Lf8Assembler.assemble("""
                    LDI R0, 5
                    LDI R1, 3
                    ADD R0, R1
                    PUSH R0
                    CALL addTwo
                    POP R3
                    CMP R0, R3
                    JNZ correct
                    LDI R0, 0xff
                correct:
                    STORE R0, 0x8002
                    HLT
                addTwo:
                    LDI R2, 2
                    ADD R0, R2
                    RET
                """);

        for (Lf8ImplementationMode mode : new Lf8ImplementationMode[]{
                Lf8ImplementationMode.FAST, Lf8ImplementationMode.STRUCTURAL}) {
            CircuitProject project = Lf8ComputerFactory.create(mode, program);
            CompilationResult compiled = compileAndRoundTrip(project);
            Simulation simulation = new Simulation(compiled.circuit());
            CompiledProbe probe = probeOf(project.mainCircuit(), compiled);
            runToHalt(simulation, probe, 1_000);
            assertEquals(LogicVector.fromUnsignedLong(10, 8),
                    simulation.memoryPage(probe.ramId(), 2, 1).orElseThrow().wordAt(2),
                    mode + " must execute identical ISA/microcode behavior");
            if (mode == Lf8ImplementationMode.STRUCTURAL) {
                assertTrue(project.circuit("STRUCT_ALU8").isPresent());
                assertTrue(project.circuit("RIPPLE_ADDER8").isPresent());
                assertTrue(project.circuit("STRUCT_FULL_ADDER").isPresent());
                assertTrue(project.circuit("STRUCT_HALF_ADDER").isPresent());
            }
        }
    }

    private CompilationResult compileAndRoundTrip(CircuitProject project) {
        Path file = directory.resolve(java.util.UUID.randomUUID() + "." + ProjectFormat.EXTENSION);
        ProjectFormat.save(project, file);
        CircuitProject loaded = ProjectFormat.load(file);
        return new CircuitCompiler(ComponentRegistry.standard()).compile(loaded, "main");
    }

    private CompiledProbe probeOf(CircuitDocument main, CompilationResult compiled) {
        int clkId = compiled.componentByLabel("CLK").orElseThrow();
        int resetId = compiled.componentByLabel("RESET").orElseThrow();
        int ramId = compiled.componentByLabel("RAM").orElseThrow();
        int halted = net(main, compiled, "HALT_PROBE", "IN");
        int memoryRead = hierarchyNet(main, compiled, "CPU", "MEMORY_READ");
        int memoryWrite = hierarchyNet(main, compiled, "CPU", "MEMORY_WRITE");
        int data = hierarchyNet(main, compiled, "CPU", "DATA");
        return new CompiledProbe(clkId, resetId, halted, ramId, data, memoryRead, memoryWrite);
    }

    private int net(CircuitDocument document, CompilationResult compiled,
                    String componentLabel, String port) {
        var component = document.components().stream()
                .filter(candidate -> componentLabel.equals(candidate.label())).findFirst().orElseThrow();
        return compiled.sourceMap().netOf(new PortReference(component.id(), port)).orElseThrow();
    }

    private int hierarchyNet(CircuitDocument document, CompilationResult compiled,
                             String componentLabel, String port) {
        var component = document.components().stream()
                .filter(candidate -> componentLabel.equals(candidate.label())).findFirst().orElseThrow();
        return compiled.hierarchySourceMap()
                .netId("main/" + component.id() + "." + port).orElseThrow();
    }

    private void runToHalt(Simulation simulation, CompiledProbe probe) {
        runToHalt(simulation, probe, 400);
    }

    private void runToHalt(Simulation simulation, CompiledProbe probe, int maxEdges) {
        simulation.setInput(probe.resetId(), LogicState.ONE);
        simulation.setInput(probe.resetId(), LogicState.ZERO);
        int edges = 0;
        while (simulation.readNet(probe.haltedNet()).singleBit() != LogicState.ONE && edges < maxEdges) {
            simulation.setInput(probe.clkId(), LogicState.ZERO);
            assertFalse(simulation.readNet(probe.memoryReadNet()).singleBit() == LogicState.ONE
                            && simulation.readNet(probe.memoryWriteNet()).singleBit() == LogicState.ONE,
                    "MEMORY_READ and MEMORY_WRITE must never overlap");
            assertFalse(simulation.hasDriverConflict(probe.dataNet()),
                    "the shared DATA bus must have at most one active driver");
            simulation.setInput(probe.clkId(), LogicState.ONE);
            edges++;
        }
        assertTrue(edges < maxEdges, "CPU did not halt within " + maxEdges + " clock edges");
    }

    private record CompiledProbe(
            int clkId,
            int resetId,
            int haltedNet,
            int ramId,
            int dataNet,
            int memoryReadNet,
            int memoryWriteNet) {
    }
}
