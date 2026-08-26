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
        simulation.setInput(probe.resetId(), LogicState.ONE);
        simulation.setInput(probe.resetId(), LogicState.ZERO);
        int edges = 0;
        while (simulation.readNet(probe.haltedNet()).singleBit() != LogicState.ONE && edges < 400) {
            simulation.setInput(probe.clkId(), LogicState.ZERO);
            assertFalse(simulation.readNet(probe.memoryReadNet()).singleBit() == LogicState.ONE
                            && simulation.readNet(probe.memoryWriteNet()).singleBit() == LogicState.ONE,
                    "MEMORY_READ and MEMORY_WRITE must never overlap");
            assertFalse(simulation.hasDriverConflict(probe.dataNet()),
                    "the shared DATA bus must have at most one active driver");
            simulation.setInput(probe.clkId(), LogicState.ONE);
            edges++;
        }
        assertTrue(edges < 400, "CPU did not halt within 400 clock edges");
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
