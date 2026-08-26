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
import dev.logicforge.processor.lf8.Lf8MemoryMap;
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
        // This one program used to trip a simulation-core bug: SimulationOscillationException
        // fired once a run was clocked several hundred edges past halt, regardless of whether
        // anything was actually oscillating (root cause: deltaCyclesAtCurrentTime was never
        // reset across setInput calls). That is now fixed in Simulation.setInput; this test
        // (previously split into three shorter ones to route around it) is restored to its
        // original single long-running form as direct proof.
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

    // The three tests below cover the same instructions individually; kept alongside the
    // restored long-form test above for fast per-instruction failure localization.

    @Test
    void logicalInstructionsCombineOperandsBitwise() {
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
                opcode(Lf8Isa.HLT),
        };
        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        int[] expected = {0x00, 0xff, 0xff};
        for (int address = 0; address < expected.length; address++) {
            assertEquals(LogicVector.fromUnsignedLong(expected[address], 8),
                    simulation.memoryPage(probe.ramId(), address, 1).orElseThrow().wordAt(address),
                    "RAM result slot " + address);
        }
    }

    @Test
    void incDecShlShrUpdateTheirOperandRegister() {
        int[] program = {
                opcode(Lf8Isa.LDI), 1, 0x0f,
                opcode(Lf8Isa.INC), 1,
                opcode(Lf8Isa.STORE), 1, 0x00, 0x80,
                opcode(Lf8Isa.DEC), 1,
                opcode(Lf8Isa.STORE), 1, 0x01, 0x80,
                opcode(Lf8Isa.SHL), 1,
                opcode(Lf8Isa.STORE), 1, 0x02, 0x80,
                opcode(Lf8Isa.SHR), 1,
                opcode(Lf8Isa.STORE), 1, 0x03, 0x80,
                opcode(Lf8Isa.HLT),
        };
        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        int[] expected = {0x10, 0x0f, 0x1e, 0x0f};
        for (int address = 0; address < expected.length; address++) {
            assertEquals(LogicVector.fromUnsignedLong(expected[address], 8),
                    simulation.memoryPage(probe.ramId(), address, 1).orElseThrow().wordAt(address),
                    "RAM result slot " + address);
        }
    }

    @Test
    void cmpSetsFlagsWithoutWritingBackToTheDestinationRegister() {
        int[] program = {
                opcode(Lf8Isa.LDI), 0, 0xff,
                opcode(Lf8Isa.LDI), 1, 0x0f,
                opcode(Lf8Isa.CMP), 0, 1,
                opcode(Lf8Isa.STORE), 0, 0x00, 0x80,
                opcode(Lf8Isa.HLT),
        };
        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        assertEquals(LogicVector.fromUnsignedLong(0xff, 8),
                simulation.memoryPage(probe.ramId(), 0, 1).orElseThrow().wordAt(0),
                "CMP must not write its result back into R0");
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

        // The 3-byte CALL at address 3 returns to address 6 (0x0006). RET does not erase the
        // stack, only walks past it, so the pushed bytes are still readable after halt. This
        // pins down the documented push order: PC_HIGH first (0x00, ends up deepest on the
        // stack at 0xbfff / RAM-relative 0x3fff), then PC_LOW (0x06, on top at 0xbffe /
        // RAM-relative 0x3ffe) — independent of the little-endian ADDRESS16 operand encoding
        // used for JMP/CALL's own instruction bytes.
        assertEquals(LogicVector.fromUnsignedLong(0x00, 8),
                simulation.memoryPage(probe.ramId(), 0x3fff, 1).orElseThrow().wordAt(0x3fff),
                "CALL must push the return address's high byte first (deepest on the stack)");
        assertEquals(LogicVector.fromUnsignedLong(0x06, 8),
                simulation.memoryPage(probe.ramId(), 0x3ffe, 1).orElseThrow().wordAt(0x3ffe),
                "CALL must push the return address's low byte second (on top of the stack)");
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

    @Test
    void interruptEnableStartsDisabledAndEiDiToggleARealCircuitRegister() {
        int ieId;
        {
            CircuitProject bootProject = Lf8ComputerFactory.create(opcode(Lf8Isa.HLT));
            CompilationResult bootCompiled = compileAndRoundTrip(bootProject);
            Simulation bootSimulation = new Simulation(bootCompiled.circuit());
            CompiledProbe bootProbe = probeOf(bootProject.mainCircuit(), bootCompiled);
            bootSimulation.setInput(bootProbe.resetId(), LogicState.ONE);
            bootSimulation.setInput(bootProbe.resetId(), LogicState.ZERO);
            ieId = bootCompiled.componentByLabel("IE_REGISTER").orElseThrow();
            assertEquals(LogicVector.ZERO, bootSimulation.readOutput(ieId, 0),
                    "interrupts must be disabled at power-on until EI runs");
        }

        int[] program = {
                opcode(Lf8Isa.EI),
                opcode(Lf8Isa.DI),
                opcode(Lf8Isa.EI),
                opcode(Lf8Isa.HLT),
        };
        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);
        ieId = compiled.componentByLabel("IE_REGISTER").orElseThrow();

        simulation.setInput(probe.resetId(), LogicState.ONE);
        simulation.setInput(probe.resetId(), LogicState.ZERO);
        assertEquals(LogicVector.ZERO, simulation.readOutput(ieId, 0));

        // Every instruction occupies a full MICROSTEPS-wide slot regardless of how many of
        // its steps do real work (MICROSTEP free-runs through the idle remainder before
        // wrapping back to step 0 for the next fetch), so each instruction boundary is
        // exactly Lf8Microcode.MICROSTEPS clock edges away.
        int microsteps = dev.logicforge.processor.lf8.Lf8Microcode.MICROSTEPS;
        clockEdges(simulation, probe, microsteps); // RESET vector fetch
        assertEquals(LogicVector.ZERO, simulation.readOutput(ieId, 0));
        clockEdges(simulation, probe, microsteps); // EI
        assertEquals(LogicVector.ONE, simulation.readOutput(ieId, 0), "EI must set IE");

        clockEdges(simulation, probe, microsteps); // DI
        assertEquals(LogicVector.ZERO, simulation.readOutput(ieId, 0), "DI must clear IE");

        clockEdges(simulation, probe, microsteps); // EI
        assertEquals(LogicVector.ONE, simulation.readOutput(ieId, 0), "EI must set IE again");
    }

    @Test
    void irqEntrySequenceSavesStateJumpsToHandlerAndIretRestoresItExactly() {
        int[] program = {
                opcode(Lf8Isa.EI),                                   // 0
                opcode(Lf8Isa.JMP), 0x12, 0x00,                      // 1..3  -> body (0x12)
                opcode(Lf8Isa.NOP),                                  // 4
                opcode(Lf8Isa.NOP),                                  // 5
                opcode(Lf8Isa.NOP),                                  // 6
                opcode(Lf8Isa.NOP),                                  // 7
                // handler, fixed at Lf8CircuitFactory.IRQ_HANDLER_ADDRESS (0x08)
                opcode(Lf8Isa.LDI), 1, 1,                            // 8..10   R1 = 1
                opcode(Lf8Isa.INC), 1,                               // 11..12  R1 = 2; clobbers flags
                opcode(Lf8Isa.STORE), 1, 0x00, 0x80,                 // 13..16  RAM[0] = 2
                opcode(Lf8Isa.IRET),                                 // 17
                // body (0x12 = 18)
                opcode(Lf8Isa.LDI), 4, 0xff,                         // 18..20  R4 = 0xff
                opcode(Lf8Isa.LDI), 5, 0x01,                         // 21..23  R5 = 0x01
                opcode(Lf8Isa.ADD), 4, 5,                            // 24..26  R4 = 0; flags Z,C set
                opcode(Lf8Isa.LDI), 2, 5,                            // 27..29  R2 = 5 (loop counter)
                // loopBody (0x1e = 30)
                opcode(Lf8Isa.DEC), 2,                               // 30..31
                opcode(Lf8Isa.JNZ), 0x1e, 0x00,                      // 32..34  -> loopBody
                opcode(Lf8Isa.LDI), 3, 0x77,                         // 35..37  R3 = 0x77
                opcode(Lf8Isa.STORE), 3, 0x01, 0x80,                 // 38..41  RAM[1] = 0x77
                opcode(Lf8Isa.HLT),                                  // 42
        };
        CircuitProject project = Lf8ComputerFactory.createWithVectors(program, 0x0008, 0, 0);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        int irqId = compiled.componentByLabel("IRQ").orElseThrow();
        int ieId = compiled.componentByLabel("IE_REGISTER").orElseThrow();
        int spId = compiled.componentByLabel("SP").orElseThrow();
        int flagsId = compiled.componentByLabel("FLAGS_REGISTER").orElseThrow();

        simulation.setInput(probe.resetId(), LogicState.ONE);
        simulation.setInput(probe.resetId(), LogicState.ZERO);
        simulation.setInput(irqId, LogicState.ZERO);

        int microsteps = dev.logicforge.processor.lf8.Lf8Microcode.MICROSTEPS;
        clockEdges(simulation, probe, microsteps); // RESET vector fetch

        // EI, JMP, LDI R4, LDI R5, ADD, LDI R2 - six full instruction slots land exactly on
        // the fetch boundary for the loop's first DEC R2 (address 0x1d), with the caller's
        // flags already pinned to a known non-zero value (Z, C) by the ADD above.
        clockEdges(simulation, probe, microsteps * 6);
        assertEquals(LogicVector.ONE, simulation.readOutput(ieId, 0), "EI must have taken effect");
        assertEquals(LogicVector.fromUnsignedLong(0b0011, 4), simulation.readOutput(flagsId, 0),
                "0xff + 0x01 must set Z and C before the interrupt fires");

        // Assert IRQ right at that boundary: the handler must run instead of DEC R2, and the
        // pushed return address must be this exact instruction's address, not a garbage
        // mid-instruction PC. A single pulse is enough - IRQ_TAKEN latches and holds through
        // the whole entry sequence and handler regardless of what the line does afterwards.
        simulation.setInput(irqId, LogicState.ONE);
        clockEdges(simulation, probe, microsteps);
        simulation.setInput(irqId, LogicState.ZERO);

        int edges = 0;
        int maxEdges = 400;
        while (simulation.readNet(probe.haltedNet()).singleBit() != LogicState.ONE && edges < maxEdges) {
            simulation.setInput(probe.clkId(), LogicState.ZERO);
            simulation.setInput(probe.clkId(), LogicState.ONE);
            edges++;
        }
        assertTrue(edges < maxEdges, "CPU did not halt within " + maxEdges + " clock edges after IRQ");

        assertEquals(LogicVector.fromUnsignedLong(2, 8),
                simulation.memoryPage(probe.ramId(), 0, 1).orElseThrow().wordAt(0),
                "the handler must have actually run: LDI R1,1 then INC must store R1 = 2");
        assertEquals(LogicVector.fromUnsignedLong(0x77, 8),
                simulation.memoryPage(probe.ramId(), 1, 1).orElseThrow().wordAt(1),
                "main must resume exactly where it was interrupted and complete its loop "
                        + "correctly (a wrong return address would corrupt the loop count or "
                        + "never reach HLT)");
        assertEquals(LogicVector.fromUnsignedLong(0xbfff, 16), simulation.readOutput(spId, 0),
                "the entry sequence's three pushes and IRET's three pops must leave SP balanced");
        assertEquals(LogicVector.fromUnsignedLong(0b0011, 4), simulation.readOutput(flagsId, 0),
                "IRET must restore the caller's flags (Z, C), not the handler's clobbered value "
                        + "left by INC R1");
        assertEquals(LogicVector.ONE, simulation.readOutput(ieId, 0),
                "IRET must restore the saved STATUS byte's IE bit");
    }

    @Test
    void resetFetchesItsLittleEndianVectorFromExternalRomBeforeExecuting() {
        int[] program = {
                opcode(Lf8Isa.HLT), opcode(Lf8Isa.HLT), opcode(Lf8Isa.HLT),
                opcode(Lf8Isa.HLT),
                opcode(Lf8Isa.LDI), 0, 0x5a,
                opcode(Lf8Isa.STORE), 0, 0x00, 0x80,
                opcode(Lf8Isa.HLT),
        };
        CircuitProject project = Lf8ComputerFactory.createWithVectors(program, 0, 0, 0x0004);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);

        runToHalt(simulation, probe);

        assertEquals(LogicVector.fromUnsignedLong(0x5a, 8),
                simulation.memoryPage(probe.ramId(), 0, 1).orElseThrow().wordAt(0),
                "execution must begin at the PC loaded from RESET_VECTOR, not implicit PC=0");
        int vectorRomId = compiled.componentByLabel("VECTOR_ROM").orElseThrow();
        assertEquals(LogicVector.fromUnsignedLong(0x04, 8),
                simulation.memoryPage(vectorRomId, 4, 2).orElseThrow().wordAt(4));
        assertEquals(LogicVector.fromUnsignedLong(0x00, 8),
                simulation.memoryPage(vectorRomId, 4, 2).orElseThrow().wordAt(5));
    }

    @Test
    void nmiIgnoresIeUsesItsOwnVectorAndIretRestoresDisabledState() {
        int[] program = {
                opcode(Lf8Isa.JMP), 0x10, 0x00,
                opcode(Lf8Isa.NOP), opcode(Lf8Isa.NOP), opcode(Lf8Isa.NOP),
                opcode(Lf8Isa.NOP), opcode(Lf8Isa.NOP),
                opcode(Lf8Isa.LDI), 1, 0x33,
                opcode(Lf8Isa.STORE), 1, 0x00, 0x80,
                opcode(Lf8Isa.IRET),
                opcode(Lf8Isa.LDI), 0, 0x55,
                opcode(Lf8Isa.STORE), 0, 0x01, 0x80,
                opcode(Lf8Isa.HLT),
        };
        CircuitProject project = Lf8ComputerFactory.createWithVectors(program, 0, 0x0008, 0);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);
        int nmiId = compiled.componentByLabel("NMI").orElseThrow();
        int ieId = compiled.componentByLabel("IE_REGISTER").orElseThrow();
        int spId = compiled.componentByLabel("SP").orElseThrow();

        simulation.setInput(probe.resetId(), LogicState.ONE);
        simulation.setInput(probe.resetId(), LogicState.ZERO);
        simulation.setInput(nmiId, LogicState.ZERO);
        int microsteps = dev.logicforge.processor.lf8.Lf8Microcode.MICROSTEPS;
        clockEdges(simulation, probe, microsteps * 2); // reset sequence, then JMP main
        assertEquals(LogicVector.ZERO, simulation.readOutput(ieId, 0));

        simulation.setInput(nmiId, LogicState.ONE);
        clockEdges(simulation, probe, microsteps); // finish LDI, latch NMI at its boundary
        simulation.setInput(nmiId, LogicState.ZERO);

        int edges = 0;
        while (simulation.readNet(probe.haltedNet()).singleBit() != LogicState.ONE && edges < 300) {
            simulation.setInput(probe.clkId(), LogicState.ZERO);
            simulation.setInput(probe.clkId(), LogicState.ONE);
            edges++;
        }
        assertTrue(edges < 300, "NMI handler did not return to the interrupted program");
        assertEquals(LogicVector.fromUnsignedLong(0x33, 8),
                simulation.memoryPage(probe.ramId(), 0, 2).orElseThrow().wordAt(0));
        assertEquals(LogicVector.fromUnsignedLong(0x55, 8),
                simulation.memoryPage(probe.ramId(), 0, 2).orElseThrow().wordAt(1));
        assertEquals(LogicVector.ZERO, simulation.readOutput(ieId, 0),
                "IRET must restore IE=0 from the NMI's saved STATUS byte");
        assertEquals(LogicVector.fromUnsignedLong(0xbfff, 16), simulation.readOutput(spId, 0));
    }

    @Test
    void loadAndStoreReachGenericMemoryMappedInputAndOutputPorts() {
        int[] program = {
                opcode(Lf8Isa.LDI), 0, 0x42,
                opcode(Lf8Isa.STORE), 0,
                Lf8MemoryMap.OUTPUT_PORT & 0xff, Lf8MemoryMap.OUTPUT_PORT >>> 8,
                opcode(Lf8Isa.LOAD), 1,
                Lf8MemoryMap.INPUT_PORT & 0xff, Lf8MemoryMap.INPUT_PORT >>> 8,
                opcode(Lf8Isa.HLT),
        };
        CircuitProject project = Lf8ComputerFactory.create(program);
        CompilationResult compiled = compileAndRoundTrip(project);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(project.mainCircuit(), compiled);
        int inputPortId = compiled.componentByLabel("INPUT_PORT").orElseThrow();
        int outputPortId = compiled.componentByLabel("OUTPUT_PORT").orElseThrow();
        int registerFileId = compiled.componentByLabel("REGISTER_FILE").orElseThrow();

        simulation.setInput(inputPortId, LogicVector.fromUnsignedLong(0xa5, 8));
        runToHalt(simulation, probe);

        assertEquals(LogicVector.fromUnsignedLong(0x42, 8),
                simulation.readOutput(outputPortId, 0));
        assertEquals(LogicVector.fromUnsignedLong(0xa5, 8),
                simulation.debugSnapshot(registerFileId).registers().get(1),
                "LOAD through C001 must place the injected input-port value in R1");
    }

    private void clockEdges(Simulation simulation, CompiledProbe probe, int edges) {
        for (int i = 0; i < edges; i++) {
            simulation.setInput(probe.clkId(), LogicState.ZERO);
            simulation.setInput(probe.clkId(), LogicState.ONE);
        }
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
            try {
                simulation.setInput(probe.clkId(), LogicState.ONE);
            } catch (dev.logicforge.simulation.SimulationOscillationException failure) {
                System.err.println("LF8 oscillation at edge " + edges);
                throw failure;
            }
            edges++;
        }
        assertTrue(edges < maxEdges, "CPU did not halt within " + maxEdges + " clock edges");
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
