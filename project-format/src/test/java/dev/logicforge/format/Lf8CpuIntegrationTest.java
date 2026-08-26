package dev.logicforge.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.component.PortDirection;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.SubcircuitSupport;
import dev.logicforge.compiler.CircuitCompiler;
import dev.logicforge.compiler.CompilationResult;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.processor.lf8.Lf8ComputerFactory;
import dev.logicforge.processor.lf8.Lf8CircuitFactory;
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
                opcode(Lf8Isa.LDI), 0, 5,
                opcode(Lf8Isa.LDI), 1, 3,
                opcode(Lf8Isa.ADD), 0, 1,
                opcode(Lf8Isa.STORE), 0, 0x00, 0x80,
                opcode(Lf8Isa.HLT),
        };

        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        assertEquals(LogicVector.fromUnsignedLong(0x08, 8),
                simulation.memoryPage(probe.ramId(), 0, 1).orElseThrow().wordAt(0));
    }

    @Test
    void widenedIsaExecutesSubMovLoadAndJmpCorrectly() {
        int[] program = {
                opcode(Lf8Isa.LDI), 0, 10,
                opcode(Lf8Isa.LDI), 1, 4,
                opcode(Lf8Isa.SUB), 0, 1,
                opcode(Lf8Isa.MOV), 1, 0,
                opcode(Lf8Isa.ADD), 0, 1,
                opcode(Lf8Isa.MOV), 0, 1,
                opcode(Lf8Isa.STORE), 0, 0x00, 0x80,
                opcode(Lf8Isa.LDI), 0, 99,
                opcode(Lf8Isa.LOAD), 0, 0x00, 0x80,
                opcode(Lf8Isa.STORE), 0, 0x01, 0x80,
                opcode(Lf8Isa.JMP), 0x25, 0x00,
                opcode(Lf8Isa.HLT),
                opcode(Lf8Isa.LDI), 1, 77,
                opcode(Lf8Isa.STORE), 1, 0x02, 0x80,
                opcode(Lf8Isa.HLT),
        };

        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        assertEquals(LogicVector.fromUnsignedLong(6, 8),
                simulation.memoryPage(probe.ramId(), 0, 1).orElseThrow().wordAt(0));
        assertEquals(LogicVector.fromUnsignedLong(6, 8),
                simulation.memoryPage(probe.ramId(), 1, 1).orElseThrow().wordAt(1),
                "LOAD must read the stored value back before the second STORE");
        assertEquals(LogicVector.fromUnsignedLong(77, 8),
                simulation.memoryPage(probe.ramId(), 2, 1).orElseThrow().wordAt(2),
                "JMP must reach the post-halt block and STORE its result");
    }

    @Test
    void registerEncodingReachesR0ThroughR7WithoutDedicatedOpcodes() {
        int[] program = {
                opcode(Lf8Isa.LDI), 7, 0x22,
                opcode(Lf8Isa.LDI), 6, 0x11,
                opcode(Lf8Isa.ADD), 7, 6,
                opcode(Lf8Isa.STORE), 7, 0x02, 0x80,
                opcode(Lf8Isa.HLT),
        };
        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        assertEquals(LogicVector.fromUnsignedLong(0x33, 8),
                simulation.memoryPage(probe.ramId(), 2, 1).orElseThrow().wordAt(2));
    }

    @Test
    void factoryExposesStableCpuDatapathAndControlBoundaries() {
        CircuitProject project = Lf8ComputerFactory.create(opcode(Lf8Isa.HLT));

        assertEquals(java.util.List.of("main", Lf8CircuitFactory.CPU_CIRCUIT,
                        Lf8CircuitFactory.DATAPATH_CIRCUIT, Lf8CircuitFactory.CONTROL_CIRCUIT),
                project.circuitNames());
        var cpuPorts = SubcircuitSupport.interfacePorts(
                project.circuit(Lf8CircuitFactory.CPU_CIRCUIT).orElseThrow());
        assertTrue(cpuPorts.stream().anyMatch(port -> port.name().equals("ADDRESS")
                && port.width().bits() == 16 && port.direction() == PortDirection.OUTPUT));
        assertTrue(cpuPorts.stream().anyMatch(port -> port.name().equals("DATA")
                && port.width().bits() == 8 && port.direction() == PortDirection.INOUT));
        assertTrue(cpuPorts.stream().anyMatch(port -> port.name().equals("MEMORY_READ")
                && port.direction() == PortDirection.OUTPUT));
        assertTrue(cpuPorts.stream().anyMatch(port -> port.name().equals("MEMORY_WRITE")
                && port.direction() == PortDirection.OUTPUT));
    }

    @Test
    void unmappedReadsLeaveTheSharedDataBusFloating() {
        int[] program = {
                opcode(Lf8Isa.LOAD), 0, 0x00, 0xc0,
                opcode(Lf8Isa.HLT),
        };
        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        simulation.setInput(probe.resetId(), LogicState.ONE);
        simulation.setInput(probe.resetId(), LogicState.ZERO);
        boolean sawUnmappedRead = false;
        for (int edges = 0; edges < 100
                && simulation.readNet(probe.haltedNet()).singleBit() != LogicState.ONE; edges++) {
            simulation.setInput(probe.clkId(), LogicState.ZERO);
            if (simulation.readNet(probe.memoryReadNet()).singleBit() == LogicState.ONE
                    && simulation.readNet(probe.addressNet()).toUnsignedLong().orElse(-1) == 0xc000) {
                sawUnmappedRead = true;
                assertEquals(LogicState.ZERO,
                        simulation.readNet(probe.memoryWriteNet()).singleBit());
                assertTrue(simulation.readNet(probe.dataNet()).isHighImpedance(),
                        "neither ROM nor RAM may drive an unmapped read");
                assertFalse(simulation.hasDriverConflict(probe.dataNet()));
            }
            simulation.setInput(probe.clkId(), LogicState.ONE);
        }
        assertTrue(sawUnmappedRead, "the LOAD must issue a read at 0xc000");
    }

    @Test
    void arithmeticAluLatchesRealZncvFlags() {
        int[] program = {
                opcode(Lf8Isa.LDI), 0, 0x7f,
                opcode(Lf8Isa.LDI), 1, 0x01,
                opcode(Lf8Isa.ADD), 0, 1,
                opcode(Lf8Isa.HLT),
        };
        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        int flagsId = compiled.componentByLabel("FLAGS_REGISTER").orElseThrow();
        assertEquals(LogicVector.fromUnsignedLong(0b1100, 4), simulation.readOutput(flagsId, 0),
                "0x7f + 1 must set N and V while clearing Z and C");
    }

    @Test
    void everyConditionalBranchHandlesTakenAndNotTakenPaths() {
        java.util.List<BranchCase> cases = java.util.List.of(
                new BranchCase("JZ taken", Lf8Isa.JZ, Lf8Isa.SUB, 5, 5, true),
                new BranchCase("JZ not taken", Lf8Isa.JZ, Lf8Isa.ADD, 1, 1, false),
                new BranchCase("JNZ taken", Lf8Isa.JNZ, Lf8Isa.ADD, 1, 1, true),
                new BranchCase("JNZ not taken", Lf8Isa.JNZ, Lf8Isa.SUB, 5, 5, false),
                new BranchCase("JC taken", Lf8Isa.JC, Lf8Isa.SUB, 5, 5, true),
                new BranchCase("JC not taken", Lf8Isa.JC, Lf8Isa.SUB, 0, 1, false),
                new BranchCase("JNC taken", Lf8Isa.JNC, Lf8Isa.SUB, 0, 1, true),
                new BranchCase("JNC not taken", Lf8Isa.JNC, Lf8Isa.SUB, 5, 5, false),
                new BranchCase("JN taken", Lf8Isa.JN, Lf8Isa.SUB, 0, 1, true),
                new BranchCase("JN not taken", Lf8Isa.JN, Lf8Isa.ADD, 1, 1, false),
                new BranchCase("JNN taken", Lf8Isa.JNN, Lf8Isa.ADD, 1, 1, true),
                new BranchCase("JNN not taken", Lf8Isa.JNN, Lf8Isa.SUB, 0, 1, false));

        for (BranchCase branchCase : cases) {
            assertBranchPath(branchCase);
        }
    }

    @Test
    void remainingBaseAluInstructionsExecuteAndCmpDoesNotWriteBack() {
        int[] program = {
                opcode(Lf8Isa.LDI), 0, 0xf0,
                opcode(Lf8Isa.LDI), 1, 0x0f,
                opcode(Lf8Isa.AND), 0, 1,
                opcode(Lf8Isa.STORE), 0, 0x00, 0x80,
                opcode(Lf8Isa.LDI), 0, 0xf0,
                opcode(Lf8Isa.OR), 0, 1,
                opcode(Lf8Isa.STORE), 0, 0x01, 0x80,
                opcode(Lf8Isa.LDI), 0, 0xf0,
                opcode(Lf8Isa.XOR), 0, 1,
                opcode(Lf8Isa.STORE), 0, 0x02, 0x80,
                opcode(Lf8Isa.INC), 1,
                opcode(Lf8Isa.STORE), 1, 0x03, 0x80,
                opcode(Lf8Isa.DEC), 1,
                opcode(Lf8Isa.STORE), 1, 0x04, 0x80,
                opcode(Lf8Isa.SHL), 1,
                opcode(Lf8Isa.STORE), 1, 0x05, 0x80,
                opcode(Lf8Isa.SHR), 1,
                opcode(Lf8Isa.STORE), 1, 0x06, 0x80,
                opcode(Lf8Isa.CMP), 0, 1,
                opcode(Lf8Isa.STORE), 0, 0x07, 0x80,
                opcode(Lf8Isa.HLT),
        };
        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        int[] expected = {0x00, 0xff, 0xff, 0x10, 0x0f, 0x1e, 0x0f, 0xff};
        for (int address = 0; address < expected.length; address++) {
            assertEquals(LogicVector.fromUnsignedLong(expected[address], 8),
                    simulation.memoryPage(probe.ramId(), address, 1).orElseThrow().wordAt(address),
                    "RAM result slot " + address);
        }
        int flagsId = compiled.componentByLabel("FLAGS_REGISTER").orElseThrow();
        assertEquals(LogicVector.fromUnsignedLong(0b0110, 4), simulation.readOutput(flagsId, 0),
                "CMP 0xff,0x0f sets C and N without changing R0");
    }

    @Test
    void shiftUpdatesCarryWhilePreservingOverflow() {
        int[] program = {
                opcode(Lf8Isa.LDI), 0, 0x7f,
                opcode(Lf8Isa.LDI), 1, 0x01,
                opcode(Lf8Isa.ADD), 0, 1,
                opcode(Lf8Isa.SHL), 0,
                opcode(Lf8Isa.HLT),
        };
        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        int flagsId = compiled.componentByLabel("FLAGS_REGISTER").orElseThrow();
        assertEquals(LogicVector.fromUnsignedLong(0b1011, 4), simulation.readOutput(flagsId, 0),
                "SHL 0x80 must set Z/C, clear N, and preserve the prior V flag");
    }

    @Test
    void newSingleOperandAluInstructionsProduceCorrectResults() {
        int[] program = {
                opcode(Lf8Isa.LDI), 0, 0x0f,
                opcode(Lf8Isa.NOT), 0,
                opcode(Lf8Isa.STORE), 0, 0x00, 0x80,
                opcode(Lf8Isa.LDI), 1, 0x81,
                opcode(Lf8Isa.ROL), 1,
                opcode(Lf8Isa.STORE), 1, 0x01, 0x80,
                opcode(Lf8Isa.LDI), 2, 0x01,
                opcode(Lf8Isa.ROR), 2,
                opcode(Lf8Isa.STORE), 2, 0x02, 0x80,
                opcode(Lf8Isa.LDI), 3, 0x01,
                opcode(Lf8Isa.NEG), 3,
                opcode(Lf8Isa.STORE), 3, 0x03, 0x80,
                opcode(Lf8Isa.HLT),
        };
        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        int[] expected = {0xf0, 0x03, 0x80, 0xff};
        for (int address = 0; address < expected.length; address++) {
            assertEquals(LogicVector.fromUnsignedLong(expected[address], 8),
                    simulation.memoryPage(probe.ramId(), address, 1).orElseThrow().wordAt(address),
                    "RAM result slot " + address);
        }
    }

    @Test
    void rotateSetsCarryFromTheWrappedBitWhilePreservingOverflow() {
        int[] program = {
                opcode(Lf8Isa.LDI), 0, 0x81,
                opcode(Lf8Isa.ROL), 0,
                opcode(Lf8Isa.HLT),
        };
        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        int flagsId = compiled.componentByLabel("FLAGS_REGISTER").orElseThrow();
        assertEquals(LogicVector.fromUnsignedLong(0b0010, 4), simulation.readOutput(flagsId, 0),
                "ROL 0x81 -> 0x03 must set C from the wrapped MSB and clear Z/N");
    }

    @Test
    void negateProducesTwosComplementFlags() {
        int[] program = {
                opcode(Lf8Isa.LDI), 0, 0x00,
                opcode(Lf8Isa.NEG), 0,
                opcode(Lf8Isa.HLT),
        };
        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        int flagsId = compiled.componentByLabel("FLAGS_REGISTER").orElseThrow();
        assertEquals(LogicVector.fromUnsignedLong(0b0011, 4), simulation.readOutput(flagsId, 0),
                "NEG 0x00 -> 0x00 must set Z and C (no borrow) and clear N/V");
    }

    @Test
    void pushAndPopFollowLastInFirstOutOrder() {
        int[] program = {
                opcode(Lf8Isa.LDI), 0, 11,
                opcode(Lf8Isa.LDI), 1, 22,
                opcode(Lf8Isa.PUSH), 0,
                opcode(Lf8Isa.PUSH), 1,
                opcode(Lf8Isa.POP), 2,
                opcode(Lf8Isa.POP), 3,
                opcode(Lf8Isa.STORE), 2, 0x00, 0x80,
                opcode(Lf8Isa.STORE), 3, 0x01, 0x80,
                opcode(Lf8Isa.HLT),
        };
        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        assertEquals(LogicVector.fromUnsignedLong(22, 8),
                simulation.memoryPage(probe.ramId(), 0, 1).orElseThrow().wordAt(0),
                "R2 must receive the most recently pushed value (22)");
        assertEquals(LogicVector.fromUnsignedLong(11, 8),
                simulation.memoryPage(probe.ramId(), 1, 1).orElseThrow().wordAt(1),
                "R3 must receive the first-pushed value (11)");
    }

    @Test
    void pushWritesAtCurrentSpThenDecrementsAndPopIncrementsThenReads() {
        int[] program = {
                opcode(Lf8Isa.LDI), 0, 0x42,
                opcode(Lf8Isa.PUSH), 0,
                opcode(Lf8Isa.HLT),
        };
        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        int spId = compiled.componentByLabel("SP").orElseThrow();
        assertEquals(LogicVector.fromUnsignedLong(0xbffe, 16), simulation.readOutput(spId, 0),
                "a single PUSH must move SP from 0xbfff down to 0xbffe");
        assertEquals(LogicVector.fromUnsignedLong(0x42, 8),
                simulation.memoryPage(probe.ramId(), 0x3fff, 1).orElseThrow().wordAt(0x3fff),
                "PUSH must write the register's value at the pre-decrement SP address (0xbfff, "
                        + "RAM-relative 0x3fff)");
    }

    @Test
    void stackPointerResetsToTheTopOfRam() {
        CircuitProject project = Lf8ComputerFactory.create(opcode(Lf8Isa.HLT));
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        simulation.setInput(probe.resetId(), LogicState.ONE);
        simulation.setInput(probe.resetId(), LogicState.ZERO);

        int spId = compiled.componentByLabel("SP").orElseThrow();
        assertEquals(LogicVector.fromUnsignedLong(0xbfff, 16), simulation.readOutput(spId, 0),
                "SP must reset to the top of the 0x8000-0xbfff RAM region");
    }

    @Test
    void callPushesTheReturnAddressAndRetRestoresIt() {
        int[] program = {
                // main:
                opcode(Lf8Isa.LDI), 0, 1,
                opcode(Lf8Isa.CALL), 0x0b, 0x00,
                opcode(Lf8Isa.STORE), 0, 0x00, 0x80,
                opcode(Lf8Isa.HLT),
                // function (address 0x0b): R0 += 41, then return
                opcode(Lf8Isa.LDI), 1, 41,
                opcode(Lf8Isa.ADD), 0, 1,
                opcode(Lf8Isa.RET),
        };
        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        assertEquals(LogicVector.fromUnsignedLong(42, 8),
                simulation.memoryPage(probe.ramId(), 0, 1).orElseThrow().wordAt(0),
                "CALL must reach the function and RET must resume main to store R0=1+41");
        int spId = compiled.componentByLabel("SP").orElseThrow();
        assertEquals(LogicVector.fromUnsignedLong(0xbfff, 16), simulation.readOutput(spId, 0),
                "after RET the stack must be balanced back to its reset value");
    }

    @Test
    void nestedCallsReturnToTheCorrectCallerInOrder() {
        int[] program = {
                // main (0-10): call A, then store R0, then halt
                opcode(Lf8Isa.LDI), 0, 0,
                opcode(Lf8Isa.CALL), 0x0b, 0x00,
                opcode(Lf8Isa.STORE), 0, 0x00, 0x80,
                opcode(Lf8Isa.HLT),
                // A (11-22): R0 += 1, call B, R0 += 10, return
                opcode(Lf8Isa.INC), 0,
                opcode(Lf8Isa.CALL), 0x17, 0x00,
                opcode(Lf8Isa.LDI), 1, 10,
                opcode(Lf8Isa.ADD), 0, 1,
                opcode(Lf8Isa.RET),
                // B (23-29): R0 += 100, return
                opcode(Lf8Isa.LDI), 1, 100,
                opcode(Lf8Isa.ADD), 0, 1,
                opcode(Lf8Isa.RET),
        };
        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        assertEquals(LogicVector.fromUnsignedLong(111, 8),
                simulation.memoryPage(probe.ramId(), 0, 1).orElseThrow().wordAt(0),
                "main -> A -> B must unwind in order: (0+1)+100 in B, then +10 back in A");
        int spId = compiled.componentByLabel("SP").orElseThrow();
        assertEquals(LogicVector.fromUnsignedLong(0xbfff, 16), simulation.readOutput(spId, 0),
                "two balanced CALL/RET pairs must leave SP back at its reset value");
    }

    private void assertBranchPath(BranchCase branchCase) {
        int[] program = {
                opcode(Lf8Isa.LDI), 0, branchCase.left(),
                opcode(Lf8Isa.LDI), 1, branchCase.right(),
                opcode(branchCase.flagInstruction()), 0, 1,
                opcode(branchCase.branch()), 0x12, 0x00,
                opcode(Lf8Isa.LDI), 2, 0x55,
                opcode(Lf8Isa.JMP), 0x15, 0x00,
                opcode(Lf8Isa.LDI), 2, 0xaa,
                opcode(Lf8Isa.STORE), 2, 0x03, 0x80,
                opcode(Lf8Isa.HLT),
        };
        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        int expected = branchCase.taken() ? 0xaa : 0x55;
        assertEquals(LogicVector.fromUnsignedLong(expected, 8),
                simulation.memoryPage(probe.ramId(), 3, 1).orElseThrow().wordAt(3),
                branchCase.description());
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
        int data = hierarchyNet(main, compiled, "CPU", "DATA");
        int address = hierarchyNet(main, compiled, "CPU", "ADDRESS");
        int memoryRead = hierarchyNet(main, compiled, "CPU", "MEMORY_READ");
        int memoryWrite = hierarchyNet(main, compiled, "CPU", "MEMORY_WRITE");
        return new CompiledProbe(clkId, resetId, halted, ramId,
                data, address, memoryRead, memoryWrite);
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
            try {
                simulation.setInput(probe.clkId(), LogicState.ONE);
            } catch (dev.logicforge.simulation.SimulationOscillationException failure) {
                System.err.println("LF8 oscillation at edge " + edges);
                throw failure;
            }
            edges++;
        }
        assertTrue(edges < 400, "CPU did not halt within 400 clock edges");
    }

    private static int opcode(Lf8Instruction instruction) {
        return instruction.opcode();
    }

    private record CompiledProbe(
            int clkId,
            int resetId,
            int haltedNet,
            int ramId,
            int dataNet,
            int addressNet,
            int memoryReadNet,
            int memoryWriteNet) {
    }

    private record BranchCase(
            String description,
            Lf8Instruction branch,
            Lf8Instruction flagInstruction,
            int left,
            int right,
            boolean taken) {
    }
}
