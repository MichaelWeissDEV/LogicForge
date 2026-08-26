package dev.logicforge.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import dev.logicforge.processor.lf8.Lf8Instruction;
import dev.logicforge.processor.lf8.Lf8Isa;
import dev.logicforge.simulation.Simulation;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end execution tests for the production structural LF-8 factory. */
class Lf8CpuIntegrationTest {

    @TempDir
    Path directory;

    @Test
    void ldiAddStoreHaltRunsToCompletionAndStoresTheExpectedResult() {
        int[] program = {
                opcode(Lf8Isa.LDI_R0), 5,
                opcode(Lf8Isa.LDI_R1), 3,
                opcode(Lf8Isa.ADD_R0_R1),
                opcode(Lf8Isa.STORE_R0), 0x00, 0x20,
                opcode(Lf8Isa.HLT),
        };

        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        assertEquals(LogicVector.fromUnsignedLong(0x08, 8),
                simulation.memoryPage(probe.ramId(), 0x2000, 1).orElseThrow().wordAt(0x2000));
    }

    @Test
    void widenedIsaExecutesSubMovLoadAndJmpCorrectly() {
        int[] program = {
                opcode(Lf8Isa.LDI_R0), 10,
                opcode(Lf8Isa.LDI_R1), 4,
                opcode(Lf8Isa.SUB_R0_R1),
                opcode(Lf8Isa.MOV_R1_R0),
                opcode(Lf8Isa.ADD_R0_R1),
                opcode(Lf8Isa.MOV_R0_R1),
                opcode(Lf8Isa.STORE_R0), 0x00, 0x30,
                opcode(Lf8Isa.LDI_R0), 99,
                opcode(Lf8Isa.LOAD_R0), 0x00, 0x30,
                opcode(Lf8Isa.JMP), 0x14, 0x00,
                opcode(Lf8Isa.HLT),
                opcode(Lf8Isa.LDI_R1), 77,
                opcode(Lf8Isa.HLT),
        };

        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        assertEquals(LogicVector.fromUnsignedLong(6, 8),
                simulation.memoryPage(probe.ramId(), 0x3000, 1).orElseThrow().wordAt(0x3000));
        assertEquals(LogicVector.fromUnsignedLong(6, 8), simulation.readNet(probe.r0Net()));
        assertEquals(LogicVector.fromUnsignedLong(77, 8), simulation.readNet(probe.r1Net()));
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
        int halted = net(main, compiled, "HALT_LATCH", "Q");
        int r0Net = net(main, compiled, "R0", "Q");
        int r1Net = net(main, compiled, "R1", "Q");
        return new CompiledProbe(clkId, resetId, halted, ramId, r0Net, r1Net);
    }

    private int net(CircuitDocument document, CompilationResult compiled,
                    String componentLabel, String port) {
        var component = document.components().stream()
                .filter(candidate -> componentLabel.equals(candidate.label())).findFirst().orElseThrow();
        return compiled.sourceMap().netOf(new PortReference(component.id(), port)).orElseThrow();
    }

    private void runToHalt(Simulation simulation, CompiledProbe probe) {
        simulation.setInput(probe.resetId(), LogicState.ONE);
        simulation.setInput(probe.resetId(), LogicState.ZERO);
        int edges = 0;
        while (simulation.readNet(probe.haltedNet()).singleBit() != LogicState.ONE && edges < 400) {
            simulation.setInput(probe.clkId(), LogicState.ZERO);
            simulation.setInput(probe.clkId(), LogicState.ONE);
            edges++;
        }
        assertTrue(edges < 400, "CPU did not halt within 400 clock edges");
    }

    private static int opcode(Lf8Instruction instruction) {
        return instruction.opcode();
    }

    private record CompiledProbe(
            int clkId, int resetId, int haltedNet, int ramId, int r0Net, int r1Net) {
    }
}
