package dev.logicforge.processor.lf8.runtime;

import dev.logicforge.processor.lf8.Lf8ComponentRoles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.compiler.CircuitCompiler;
import dev.logicforge.compiler.RuntimeInstancePath;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.processor.lf8.Lf8ComputerFactory;
import dev.logicforge.processor.lf8.Lf8ImplementationMode;
import dev.logicforge.processor.lf8.Lf8MemoryMap;
import dev.logicforge.simulation.Simulation;
import dev.logicforge.tools.lf8.Lf8Assembler;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Architectural differential validation deliberately avoids intermediate delta cycles. */
class Lf8ModeDifferentialTest {
    @Test
    void allImplementationsMatchAtEveryInstructionBoundary() {
        int[] program = Lf8Assembler.assemble("""
                    LDI R0, 5
                    LDI R1, 3
                    ADD R0, R1
                    XOR R0, R1
                    PUSH R0
                    CALL increment
                    POP R2
                    CMP R0, R2
                    JNZ correct
                    LDI R0, 0xff
                correct:
                    STORE R0, 0x8000
                    STORE R0, 0xc000
                    HLT
                increment:
                    INC R0
                    RET
                """);

        List<ArchitecturalState> fast = run(Lf8ImplementationMode.FAST, program);
        List<ArchitecturalState> structural = run(Lf8ImplementationMode.STRUCTURAL, program);
        List<ArchitecturalState> gate = run(Lf8ImplementationMode.GATE_LEVEL, program);
        assertEquals(fast, structural, "STRUCTURAL diverged from FAST at an instruction boundary");
        assertEquals(fast, gate, "GATE_LEVEL diverged from FAST at an instruction boundary");
        assertTrue(fast.getLast().halted());
    }

    private static List<ArchitecturalState> run(Lf8ImplementationMode mode, int[] program) {
        CircuitProject project = Lf8ComputerFactory.create(mode, program);
        var compilation = new CircuitCompiler(ComponentRegistry.standard())
                .compile(project, CircuitProject.MAIN_CIRCUIT);
        Simulation simulation = new Simulation(compilation.circuit());
        var cpu = project.mainCircuit().components().stream()
                .filter(component -> "CPU".equals(component.label())).findFirst().orElseThrow();
        RuntimeInstancePath cpuPath = RuntimeInstancePath.root(CircuitProject.MAIN_CIRCUIT)
                .child(cpu.id());
        Lf8RuntimeProbe probe = new Lf8RuntimeProbe(project, compilation, simulation, cpuPath);
        int clock = probe.inputSource("CLK").orElseThrow();
        int reset = probe.inputSource("RESET").orElseThrow();
        int ram = compilation.componentByLabel("RAM").orElseThrow();
        int characterOutput = compilation.componentByLabel("CHARACTER_OUTPUT").orElseThrow();
        int haltNet = compilation.hierarchySourceMap().netId(cpuPath + ".HALT").orElseThrow();

        simulation.setInput(clock, LogicState.ZERO);
        simulation.setInput(reset, LogicState.ONE);
        simulation.setInput(reset, LogicState.ZERO);
        ArrayList<ArchitecturalState> boundaries = new ArrayList<>();
        for (int edges = 0; edges < 1_000; edges++) {
            simulation.setInput(clock, LogicState.ZERO);
            simulation.setInput(clock, LogicState.ONE);
            boolean halted = simulation.readNet(haltNet).singleBit() == LogicState.ONE;
            boolean instructionBoundary = probe.microstep().orElseThrow()
                    .toUnsignedLong().orElseThrow() == 0;
            if (instructionBoundary || halted) {
                ArchitecturalState state = snapshot(probe, simulation, ram, characterOutput,
                        halted);
                if (boundaries.isEmpty() || !boundaries.getLast().equals(state)) {
                    boundaries.add(state);
                }
            }
            if (halted) {
                return List.copyOf(boundaries);
            }
        }
        throw new AssertionError(mode + " did not halt");
    }

    private static ArchitecturalState snapshot(Lf8RuntimeProbe probe, Simulation simulation,
                                                int ram, int characterOutput, boolean halted) {
        List<LogicVector> registers = probe.registerFile().orElseThrow().registers();
        return new ArchitecturalState(
                probe.pc().orElseThrow(), probe.sp().orElseThrow(), probe.ir().orElseThrow(),
                registers,
                probe.value(Lf8ComponentRoles.path(Lf8ComponentRoles.CPU_DATAPATH,
                        Lf8ComponentRoles.DATAPATH_FLAGS), "Q").orElseThrow(),
                probe.value(Lf8ComponentRoles.path(Lf8ComponentRoles.CPU_DATAPATH,
                        Lf8ComponentRoles.DATAPATH_IE), "Q").orElseThrow(), halted,
                java.util.Arrays.asList(simulation.memoryPage(ram, 0, 4)
                        .orElseThrow().words()),
                simulation.debugSnapshot(characterOutput).textValues().getOrDefault("TEXT", ""));
    }

    private record ArchitecturalState(LogicVector pc, LogicVector sp, LogicVector ir,
                                      List<LogicVector> registers, LogicVector flags,
                                      LogicVector interruptEnable, boolean halted,
                                      List<LogicVector> ram, String characterOutput) {
        private ArchitecturalState {
            registers = List.copyOf(registers);
            ram = List.copyOf(ram);
        }
    }
}
