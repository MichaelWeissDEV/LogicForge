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

    @Test
    void allImplementationsMatchForSubAndAndOrNotAndShiftInstructions() {
        int[] program = Lf8Assembler.assemble("""
                    LDI R0, 0x0f
                    LDI R1, 0x33
                    SUB R0, R1
                    AND R0, R1
                    LDI R0, 0x0f
                    OR R0, R1
                    NOT R0
                    SHL R0
                    SHL R0
                    SHL R0
                    SHR R0
                    SHR R0
                    SHR R0
                    STORE R0, 0x8000
                    HLT
                """);

        List<ArchitecturalState> fast = run(Lf8ImplementationMode.FAST, program);
        List<ArchitecturalState> structural = run(Lf8ImplementationMode.STRUCTURAL, program);
        List<ArchitecturalState> gate = run(Lf8ImplementationMode.GATE_LEVEL, program);
        assertEquals(fast, structural, "STRUCTURAL diverged from FAST at an instruction boundary");
        assertEquals(fast, gate, "GATE_LEVEL diverged from FAST at an instruction boundary");
        assertTrue(fast.getLast().halted());
    }

    /**
     * Every flag-setting instruction at the boundary values that most often expose an
     * off-by-one in a carry/borrow/zero/sign implementation: the smallest and largest
     * 8-bit values, the sign-bit edge (0x7F/0x80), and the wrap points either side of it
     * (0xFE/0xFF). The three implementations only need to agree with each other — see the
     * class javadoc on why this file never hand-predicts an expected flag encoding.
     */
    @Test
    void allImplementationsMatchAtEveryFlagBoundaryValue() {
        int[] program = Lf8Assembler.assemble("""
                    LDI R0, 0x00
                    LDI R1, 0x01
                    ADD R0, R1
                    LDI R0, 0xff
                    ADD R0, R1
                    LDI R0, 0x7f
                    ADD R0, R1
                    LDI R0, 0x80
                    SUB R0, R1
                    LDI R0, 0x00
                    SUB R0, R1
                    LDI R0, 0xfe
                    ADD R0, R1
                    LDI R0, 0x01
                    SUB R0, R1
                    STORE R0, 0x8000
                    HLT
                """);

        List<ArchitecturalState> fast = run(Lf8ImplementationMode.FAST, program);
        List<ArchitecturalState> structural = run(Lf8ImplementationMode.STRUCTURAL, program);
        List<ArchitecturalState> gate = run(Lf8ImplementationMode.GATE_LEVEL, program);
        assertEquals(fast, structural, "STRUCTURAL diverged from FAST at an instruction boundary");
        assertEquals(fast, gate, "GATE_LEVEL diverged from FAST at an instruction boundary");
        assertTrue(fast.getLast().halted());
    }

    /**
     * IRQ is held asserted for the whole run (the way a real device typically holds its
     * request line until acknowledged); the handler is responsible for not re-entering, so
     * it disables interrupts as its first action, matching how real ISRs behave. The main
     * path's five ADDs run to completion regardless of exactly which one the interrupt lands
     * between, since IRET resumes exactly where it left off — R1 landing on 5 alongside the
     * ISR's own marker in RAM proves both "the interrupt was taken" and "control returned
     * correctly," on top of the primary assertion that all three implementations agree.
     */
    @Test
    void allImplementationsMatchAcrossAMaskableInterruptTakenAndReturnedFromWithIret() {
        int irqVector = 0x40;
        int[] program = Lf8Assembler.assemble("""
                .org 0
                start:
                    LDI R0, 1
                    LDI R1, 0
                    EI
                    ADD R1, R0
                    ADD R1, R0
                    ADD R1, R0
                    ADD R1, R0
                    ADD R1, R0
                    STORE R1, 0x8000
                    HLT
                .org 0x40
                isr:
                    DI
                    LDI R2, 0x99
                    STORE R2, 0x8001
                    IRET
                """);

        // IRQ has to stay asserted long enough for LDI/LDI/EI to actually execute (each
        // instruction takes several clock edges to fetch and run) before it is sampled;
        // unlike NMI, nothing takes it until EI runs.
        List<ArchitecturalState> fast =
                runWithInterrupt(Lf8ImplementationMode.FAST, program, irqVector, 0, "IRQ", 40);
        List<ArchitecturalState> structural =
                runWithInterrupt(Lf8ImplementationMode.STRUCTURAL, program, irqVector, 0, "IRQ", 40);
        List<ArchitecturalState> gate =
                runWithInterrupt(Lf8ImplementationMode.GATE_LEVEL, program, irqVector, 0, "IRQ", 40);
        assertEquals(fast, structural, "STRUCTURAL diverged from FAST during interrupt handling");
        assertEquals(fast, gate, "GATE_LEVEL diverged from FAST during interrupt handling");
        assertTrue(fast.getLast().halted());
        assertEquals(LogicVector.fromUnsignedLong(5, 8), fast.getLast().ram().get(0),
                "the five ADDs completed despite the interrupt landing somewhere among them");
        assertEquals(LogicVector.fromUnsignedLong(0x99, 8), fast.getLast().ram().get(1),
                "the ISR actually ran and IRET returned control to the main path");
    }

    /**
     * NMI must fire even though interrupts are never enabled with {@code EI} — that is
     * exactly what "non-maskable" means — and it uses its own vector, independent of IRQ's.
     */
    @Test
    void allImplementationsMatchAcrossANonMaskableInterruptTakenWithoutEverEnablingInterrupts() {
        int nmiVector = 0x40;
        int[] program = Lf8Assembler.assemble("""
                .org 0
                start:
                    LDI R0, 1
                    LDI R1, 0
                    ADD R1, R0
                    ADD R1, R0
                    ADD R1, R0
                    STORE R1, 0x8000
                    HLT
                .org 0x40
                isr:
                    DI
                    LDI R2, 0x55
                    STORE R2, 0x8001
                    IRET
                """);

        List<ArchitecturalState> fast =
                runWithInterrupt(Lf8ImplementationMode.FAST, program, 0, nmiVector, "NMI", 8);
        List<ArchitecturalState> structural =
                runWithInterrupt(Lf8ImplementationMode.STRUCTURAL, program, 0, nmiVector, "NMI", 8);
        List<ArchitecturalState> gate =
                runWithInterrupt(Lf8ImplementationMode.GATE_LEVEL, program, 0, nmiVector, "NMI", 8);
        assertEquals(fast, structural, "STRUCTURAL diverged from FAST during NMI handling");
        assertEquals(fast, gate, "GATE_LEVEL diverged from FAST during NMI handling");
        assertTrue(fast.getLast().halted());
        assertEquals(LogicVector.fromUnsignedLong(3, 8), fast.getLast().ram().get(0),
                "the three ADDs completed despite the NMI landing somewhere among them");
        assertEquals(LogicVector.fromUnsignedLong(0x55, 8), fast.getLast().ram().get(1),
                "the NMI actually ran despite interrupts never being enabled with EI");
    }

    private static List<ArchitecturalState> run(Lf8ImplementationMode mode, int[] program) {
        return run(Lf8ComputerFactory.create(mode, program), mode, null, 0);
    }

    /**
     * Like {@link #run(Lf8ImplementationMode, int[])}, but the computer's vector table is
     * explicit and one interrupt line ({@code "IRQ"} or {@code "NMI"}) is pulsed for a few
     * clock edges right after reset, then released — the top-level toggle is driven directly
     * (not through {@link Lf8RuntimeProbe#inputSource}, which resolves a CPU port back to its
     * driving source and fails for {@code IRQ}: at the computer level that pin is fed through
     * an OR gate combining the toggle with the timer peripheral's own request, not driven
     * directly by one source). A held-forever line risks an interrupt storm — NMI is
     * non-maskable by definition, so nothing in the handler can stop it from being retaken
     * for as long as the line stays asserted.
     */
    private static List<ArchitecturalState> runWithInterrupt(Lf8ImplementationMode mode, int[] program,
            int irqVector, int nmiVector, String interruptLine, int pulseEdges) {
        CircuitProject project = Lf8ComputerFactory.createWithVectors(mode, program, irqVector, nmiVector, 0);
        return run(project, mode, interruptLine, pulseEdges);
    }

    private static List<ArchitecturalState> run(CircuitProject project, Lf8ImplementationMode mode,
            String interruptLine, int pulseEdges) {
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
        Integer interrupt = interruptLine == null ? null
                : compilation.componentByLabel(interruptLine).orElseThrow();

        simulation.setInput(clock, LogicState.ZERO);
        simulation.setInput(reset, LogicState.ONE);
        simulation.setInput(reset, LogicState.ZERO);
        if (interrupt != null) {
            simulation.setInput(interrupt, LogicState.ONE);
        }
        ArrayList<ArchitecturalState> boundaries = new ArrayList<>();
        for (int edges = 0; edges < 1_000; edges++) {
            if (interrupt != null && edges == pulseEdges) {
                simulation.setInput(interrupt, LogicState.ZERO);
            }
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
