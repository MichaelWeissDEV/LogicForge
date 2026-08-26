package dev.logicforge.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitMetadata;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.compiler.CircuitCompiler;
import dev.logicforge.compiler.CompilationResult;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.LibraryParameters;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.Simulation;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * LF-8 v0: the first real, executing slice of the planned LF-8 CPU, built entirely from
 * ordinary LogicForge components wired together in code — no {@code LF8Behavior}, no
 * instruction execution in Java. It is deliberately minimal, not the full architecture
 * described in the project roadmap:
 *
 * <ul>
 *   <li>ISA is just {@code LDI Rn,imm8}, {@code ADD R0,R1}, {@code STORE R0,[addr16]} and
 *       {@code HLT} — enough to run the roadmap's own example program end to end. Widening
 *       the ISA (MOV/LOAD/SUB/logic/shift/jumps/flags) is follow-up work: each is one more
 *       microcode table row plus, where needed, an ALU op — the fetch/decode/execute loop
 *       proven here does not change.
 *   <li>Only two architectural registers (R0, R1), selected by dedicating a whole opcode to
 *       each destination (e.g. {@code LDI_R0} vs {@code LDI_R1}) rather than decoding a
 *       generic register-select field through a register file. Widening to N registers needs
 *       a real REG_SELECT field and a register file/mux in place of R0/R1's fixed wiring.
 *   <li>Harvard-simplified: program (ROM) and data (RAM) are on separate buses, so there is
 *       no shared external DATA bus, no MEM_READ/MEM_WRITE arbitration and no tri-state bus
 *       contention to manage yet. Unifying onto one von Neumann bus with real bus contention
 *       is follow-up work, not needed to prove the microcode loop itself.
 *   <li>Flat: every component lives in one {@code main} circuit rather than nested
 *       Datapath/ControlUnit subcircuits. Still "ordinary components wired together," just
 *       not yet organized into the roadmap's hierarchy — a mechanical follow-up once the
 *       logic is proven, via {@code openSubcircuit}-style extraction.
 * </ul>
 *
 * <p>The microcode ROM is the only decode logic: its address is {@code {IR, microstep}} and
 * every control line is one bit of its output word — there is no opcode comparator anywhere
 * in the circuit. Step 0 of every one of the 256 possible opcodes is programmed identically
 * to "fetch" (PC_INC + IR_LOAD), so the fetch cycle is uniform and the decode table only
 * needs real content for the steps of opcodes this v0 actually implements.
 */
class Lf8CpuV0Test {

    @TempDir
    Path directory;

    // Control word bit positions — the single source of truth for both the microcode table
    // below and the wiring that fans MICROCODE_ROM.DATA out to each control line.
    private static final int PC_INC = 0;
    private static final int IR_LOAD = 1;
    private static final int R0_LOAD_IMM = 2;
    private static final int R1_LOAD = 3;
    private static final int MAR_LO_LOAD = 4;
    private static final int MAR_HI_LOAD = 5;
    private static final int ALU_LOAD = 6;
    private static final int WE = 7;
    private static final int HALT_BIT = 8;
    private static final int CONTROL_WORD_WIDTH = 9;

    private static final int OP_LDI_R0 = 0x01;
    private static final int OP_LDI_R1 = 0x02;
    private static final int OP_ADD_R0_R1 = 0x03;
    private static final int OP_STORE_R0 = 0x04;
    private static final int OP_HLT = 0xFF;

    private static final int MICROSTEPS = 4;
    private static final int MICROCODE_WORDS = 256 * MICROSTEPS;

    @Test
    void ldiAddStoreHaltRunsToCompletionAndStoresTheExpectedResult() {
        int[] program = {
                OP_LDI_R0, 5,
                OP_LDI_R1, 3,
                OP_ADD_R0_R1,
                OP_STORE_R0, 0x00, 0x20, // little-endian: addr_lo, addr_hi -> 0x2000
                OP_HLT,
        };

        CircuitDocument main = buildCircuit(program);
        CircuitProject project = CircuitProject.of("lf8-v0", main);

        // Prove this survives shipping as a file, not just as an in-memory document — the
        // same lesson as the earlier nested/bit-mode/ROM-contents round-trip test.
        Path file = directory.resolve("lf8-v0." + ProjectFormat.EXTENSION);
        ProjectFormat.save(project, file);
        CircuitProject loaded = ProjectFormat.load(file);

        CompilationResult compiled = new CircuitCompiler(ComponentRegistry.standard()).compile(loaded, "main");
        Simulation simulation = new Simulation(compiled.circuit());

        int clkId = compiled.componentByLabel("CLK").orElseThrow();
        int resetId = compiled.componentByLabel("RESET").orElseThrow();
        int halted = compiled.sourceMap().netOf(new PortReference(
                main.components().stream().filter(c -> "HALT_LATCH".equals(c.label())).findFirst()
                        .orElseThrow().id(), "Q")).orElseThrow();
        int ramId = compiled.componentByLabel("RAM").orElseThrow();

        // Pulse RESET once so PC/microstep/HALT_LATCH all start from a known state.
        simulation.setInput(resetId, LogicState.ONE);
        simulation.setInput(resetId, LogicState.ZERO);

        int edges = 0;
        int maxEdges = 200;
        while (simulation.readNet(halted).singleBit() != LogicState.ONE && edges < maxEdges) {
            simulation.setInput(clkId, LogicState.ZERO);
            simulation.setInput(clkId, LogicState.ONE);
            edges++;
        }

        assertTrue(edges < maxEdges, "CPU did not halt within " + maxEdges + " clock edges");
        assertEquals(LogicState.ONE, simulation.readNet(halted).singleBit());
        assertEquals(LogicVector.fromUnsignedLong(0x08, 8),
                simulation.memoryPage(ramId, 0x2000, 1).orElseThrow().wordAt(0x2000),
                "RAM[0x2000] must hold 5 + 3 = 8");
    }

    // ------------------------------------------------------------------
    // Circuit construction
    // ------------------------------------------------------------------

    private CircuitDocument buildCircuit(int[] program) {
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", "LF-8 v0"));
        ComponentRegistry registry = ComponentRegistry.standard();

        ComponentInstance clk = add(main, registry, "source.toggle", 0, 0, ParameterValues.empty(), "CLK");
        ComponentInstance reset = add(main, registry, "source.toggle", 0, 40, ParameterValues.empty(), "RESET");

        ComponentInstance pc = add(main, registry, "sequential.loadable_counter", 100, 0,
                registry.require("sequential.loadable_counter").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 16),
                "PC");
        ComponentInstance pcDataTie = constant(main, registry, 100, 40, 16, 0, "PC_DATA_TIE");
        ComponentInstance pcLoadTie = constant(main, registry, 100, 80, 1, 0, "PC_LOAD_TIE");

        ComponentInstance progRom = add(main, registry, "memory.rom", 200, 0,
                registry.require("memory.rom").definition().defaultParameters()
                        .with(LibraryParameters.ADDRESS_WIDTH, 16)
                        .with(LibraryParameters.WIDTH, 8)
                        .with(LibraryParameters.ROM_CONTENTS, programContents(program)),
                "PROG_ROM");
        ComponentInstance progRomEn = constant(main, registry, 200, 40, 1, 1, "PROG_ROM_EN");

        ComponentInstance ir = add(main, registry, "sequential.register", 300, 0,
                registry.require("sequential.register").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8),
                "IR");
        ComponentInstance r0 = add(main, registry, "sequential.register", 300, 60,
                registry.require("sequential.register").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8),
                "R0");
        ComponentInstance r1 = add(main, registry, "sequential.register", 300, 120,
                registry.require("sequential.register").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8),
                "R1");
        ComponentInstance marLo = add(main, registry, "sequential.register", 300, 180,
                registry.require("sequential.register").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8),
                "MAR_LO");
        ComponentInstance marHi = add(main, registry, "sequential.register", 300, 240,
                registry.require("sequential.register").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8),
                "MAR_HI");

        ComponentInstance alu = add(main, registry, "arithmetic.adder", 400, 60,
                registry.require("arithmetic.adder").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8),
                "ALU");
        ComponentInstance aluCinTie = constant(main, registry, 400, 100, 1, 0, "ALU_CIN_TIE");

        ComponentInstance r0Mux = add(main, registry, "routing.mux2", 500, 60,
                registry.require("routing.mux2").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8),
                "R0_MUX");
        ComponentInstance r0LoadOr = add(main, registry, "logic.or", 500, 100, ParameterValues.empty(), "R0_LOAD_OR");

        ComponentInstance microstep = add(main, registry, "sequential.modulo_counter", 300, 300,
                registry.require("sequential.modulo_counter").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 2)
                        .with(LibraryParameters.MODULUS, MICROSTEPS),
                "MICROSTEP");

        ComponentInstance haltLatch = add(main, registry, "sequential.register_reset", 600, 0,
                registry.require("sequential.register_reset").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 1),
                "HALT_LATCH");
        ComponentInstance haltOr = add(main, registry, "logic.or", 600, 40, ParameterValues.empty(), "HALT_OR");
        ComponentInstance haltLoadTie = constant(main, registry, 600, 80, 1, 1, "HALT_LOAD_TIE");
        ComponentInstance haltNot = add(main, registry, "logic.not", 600, 120, ParameterValues.empty(), "HALT_NOT");

        ComponentInstance microcodeRom = add(main, registry, "memory.rom", 500, 300,
                registry.require("memory.rom").definition().defaultParameters()
                        .with(LibraryParameters.ADDRESS_WIDTH, 10)
                        .with(LibraryParameters.WIDTH, CONTROL_WORD_WIDTH)
                        .with(LibraryParameters.ROM_CONTENTS, microcodeContents()),
                "MICROCODE_ROM");
        ComponentInstance microcodeRomEn = constant(main, registry, 500, 340, 1, 1, "MICROCODE_ROM_EN");

        ComponentInstance ram = add(main, registry, "memory.ram", 700, 0,
                registry.require("memory.ram").definition().defaultParameters()
                        .with(LibraryParameters.ADDRESS_WIDTH, 16)
                        .with(LibraryParameters.WIDTH, 8),
                "RAM");
        ComponentInstance ramCsTie = constant(main, registry, 700, 40, 1, 1, "RAM_CS_TIE");
        ComponentInstance ramOeTie = constant(main, registry, 700, 80, 1, 0, "RAM_OE_TIE");

        // Clock and reset fan-out.
        for (ComponentInstance target : List.of(pc, ir, r0, r1, marLo, marHi, microstep, haltLatch)) {
            wire(main, clk, "OUT", target, "CLK");
        }
        wire(main, reset, "OUT", pc, "RESET");
        wire(main, reset, "OUT", microstep, "RESET");
        wire(main, reset, "OUT", haltLatch, "RESET");

        // Program counter / fetch.
        wire(main, pc, "COUNT", progRom, "ADDRESS");
        wire(main, pcDataTie, "OUT", pc, "DATA");
        wire(main, pcLoadTie, "OUT", pc, "LOAD");
        wire(main, progRomEn, "OUT", progRom, "ENABLE");
        wire(main, progRom, "DATA", ir, "DATA");
        wire(main, progRom, "DATA", r1, "DATA");
        wire(main, progRom, "DATA", marLo, "DATA");
        wire(main, progRom, "DATA", marHi, "DATA");
        wire(main, progRom, "DATA", r0Mux, "IN0");

        // ALU / R0 write-back.
        wire(main, r0, "Q", alu, "A");
        wire(main, r1, "Q", alu, "B");
        wire(main, aluCinTie, "OUT", alu, "CIN");
        wire(main, alu, "SUM", r0Mux, "IN1");
        wire(main, r0Mux, "OUT", r0, "DATA");
        wire(main, r0LoadOr, "OUT", r0, "LOAD");

        // Microcode addressing: {IR, microstep}. The source side must be sliced too (even
        // though the slice covers its whole width) — a genuinely multi-bit WHOLE endpoint
        // cannot pair with a RANGE endpoint on the other side; the compiler's per-offset
        // atom correspondence only lines up when both sides are bit/range-sliced.
        main.addConnection(Connection.create(PortEndpoint.range(new PortReference(ir.id(), "Q"), 7, 0),
                PortEndpoint.range(new PortReference(microcodeRom.id(), "ADDRESS"), 9, 2)));
        main.addConnection(Connection.create(PortEndpoint.range(new PortReference(microstep.id(), "COUNT"), 1, 0),
                PortEndpoint.range(new PortReference(microcodeRom.id(), "ADDRESS"), 1, 0)));
        wire(main, microcodeRomEn, "OUT", microcodeRom, "ENABLE");

        // Microcode control-line fan-out — the only decode logic in the whole circuit.
        bitWire(main, microcodeRom, "DATA", PC_INC, pc, "ENABLE");
        bitWire(main, microcodeRom, "DATA", IR_LOAD, ir, "LOAD");
        bitWire(main, microcodeRom, "DATA", R0_LOAD_IMM, r0LoadOr, "IN0");
        bitWire(main, microcodeRom, "DATA", R1_LOAD, r1, "LOAD");
        bitWire(main, microcodeRom, "DATA", MAR_LO_LOAD, marLo, "LOAD");
        bitWire(main, microcodeRom, "DATA", MAR_HI_LOAD, marHi, "LOAD");
        bitWire(main, microcodeRom, "DATA", ALU_LOAD, r0Mux, "SEL");
        bitWire(main, microcodeRom, "DATA", ALU_LOAD, r0LoadOr, "IN1");
        bitWire(main, microcodeRom, "DATA", WE, ram, "WE");
        bitWire(main, microcodeRom, "DATA", HALT_BIT, haltOr, "IN0");

        // Sticky HALT latch: once set, freezes the microstep counter, which freezes
        // everything else since the whole datapath is driven by that now-static microcode row.
        wire(main, haltOr, "OUT", haltLatch, "DATA");
        wire(main, haltLatch, "Q", haltOr, "IN1");
        wire(main, haltLoadTie, "OUT", haltLatch, "LOAD");
        wire(main, haltLatch, "Q", haltNot, "A");
        wire(main, haltNot, "Y", microstep, "ENABLE");

        // Data memory: address assembled from MAR_HI:MAR_LO, R0 permanently drives DATA (no
        // LOAD instruction yet in v0, so RAM never needs to drive DATA back — see class doc).
        main.addConnection(Connection.create(PortEndpoint.range(new PortReference(marLo.id(), "Q"), 7, 0),
                PortEndpoint.range(new PortReference(ram.id(), "ADDRESS"), 7, 0)));
        main.addConnection(Connection.create(PortEndpoint.range(new PortReference(marHi.id(), "Q"), 7, 0),
                PortEndpoint.range(new PortReference(ram.id(), "ADDRESS"), 15, 8)));
        wire(main, r0, "Q", ram, "DATA");
        wire(main, ramCsTie, "OUT", ram, "CS");
        wire(main, ramOeTie, "OUT", ram, "OE");

        return main;
    }

    /** {@code microcode[opcode * MICROSTEPS + microstep]}: the sole decode logic. */
    private static String microcodeContents() {
        int[] microcode = new int[MICROCODE_WORDS];
        int fetch = bit(PC_INC) | bit(IR_LOAD);
        for (int opcode = 0; opcode < 256; opcode++) {
            microcode[opcode * MICROSTEPS] = fetch;
        }
        microcode[OP_LDI_R0 * MICROSTEPS + 1] = bit(PC_INC) | bit(R0_LOAD_IMM);
        microcode[OP_LDI_R1 * MICROSTEPS + 1] = bit(PC_INC) | bit(R1_LOAD);
        microcode[OP_ADD_R0_R1 * MICROSTEPS + 1] = bit(ALU_LOAD);
        microcode[OP_STORE_R0 * MICROSTEPS + 1] = bit(PC_INC) | bit(MAR_LO_LOAD);
        microcode[OP_STORE_R0 * MICROSTEPS + 2] = bit(PC_INC) | bit(MAR_HI_LOAD);
        microcode[OP_STORE_R0 * MICROSTEPS + 3] = bit(WE);
        microcode[OP_HLT * MICROSTEPS + 1] = bit(HALT_BIT);
        microcode[OP_HLT * MICROSTEPS + 2] = bit(HALT_BIT);
        microcode[OP_HLT * MICROSTEPS + 3] = bit(HALT_BIT);

        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < microcode.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(Integer.toHexString(microcode[i]));
        }
        return csv.toString();
    }

    private static int bit(int index) {
        return 1 << index;
    }

    private static String programContents(int[] program) {
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < program.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(Integer.toHexString(program[i]));
        }
        return csv.toString();
    }

    // ------------------------------------------------------------------
    // Wiring helpers
    // ------------------------------------------------------------------

    private ComponentInstance add(CircuitDocument document, ComponentRegistry registry, String definitionId,
                                  double x, double y, ParameterValues parameters, String label) {
        ComponentInstance instance = ComponentInstance.create(definitionId, new CircuitPoint(x, y), parameters)
                .withLabel(label);
        document.addComponent(instance);
        return instance;
    }

    private ComponentInstance constant(CircuitDocument document, ComponentRegistry registry,
                                       double x, double y, int width, long value, String label) {
        ParameterValues parameters = registry.require("routing.bus_constant").definition().defaultParameters()
                .with(LibraryParameters.WIDTH, width)
                .with(LibraryParameters.BUS_CONSTANT_VALUE, Long.toHexString(value));
        return add(document, registry, "routing.bus_constant", x, y, parameters, label);
    }

    private void wire(CircuitDocument document, ComponentInstance from, String fromPort,
                      ComponentInstance to, String toPort) {
        document.addConnection(Connection.create(new PortReference(from.id(), fromPort),
                new PortReference(to.id(), toPort)));
    }

    private void bitWire(CircuitDocument document, ComponentInstance from, String fromPort, int fromBit,
                         ComponentInstance to, String toPort) {
        document.addConnection(Connection.create(
                PortEndpoint.bit(new PortReference(from.id(), fromPort), fromBit),
                PortEndpoint.whole(new PortReference(to.id(), toPort))));
    }
}
