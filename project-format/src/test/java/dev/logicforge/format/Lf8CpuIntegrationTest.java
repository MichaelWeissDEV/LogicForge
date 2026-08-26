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
 * The LF-8 CPU: an 8-bit microcoded processor with 16-bit addressing, built entirely from
 * ordinary LogicForge components wired together in code — no {@code LF8Behavior}, no
 * instruction execution in Java. It is deliberately minimal relative to the full
 * architecture described in the project roadmap, and not yet organized as the roadmap's own
 * {@code examples/lf8/lf8-computer.logic} — this class builds and verifies the same circuit
 * headlessly, but nothing yet writes it out to that path (a lf8-tools module and an
 * assembler are still future work; see the project roadmap):
 *
 * <ul>
 *   <li>Two architectural registers (R0, R1), selected by dedicating a whole opcode to each
 *       destination (e.g. {@code LDI_R0} vs {@code LDI_R1}) rather than decoding a generic
 *       register-select field through a register file. Widening to N registers needs a real
 *       REG_SELECT field and a register file/mux in place of R0/R1's fixed wiring.
 *   <li>Harvard-simplified: program (ROM) and data (RAM) are on separate buses, so there is
 *       no shared external DATA bus and no MEM_READ/MEM_WRITE arbitration to manage yet.
 *       {@code LOAD} does exercise RAM's own bidirectional DATA pin and a real tri-state
 *       gate (see below) — what is deferred is unifying program and data onto one shared
 *       external bus, not bidirectional behavior itself.
 *   <li>Flat: every component lives in one {@code main} circuit rather than nested
 *       Datapath/ControlUnit subcircuits. Still "ordinary components wired together," just
 *       not yet organized into the roadmap's hierarchy — a mechanical follow-up once the
 *       logic is proven, via {@code openSubcircuit}-style extraction.
 *   <li>No conditional branches yet (JZ/JNZ/JC/JNC): those need the microcode ROM's address
 *       to also depend on flags, which a control word alone cannot express (a control word
 *       is a function of {@code {opcode, microstep}}, not of runtime ALU results). The
 *       address already reserves 2 flag bits — tied to 0 for now — for exactly this, so
 *       adding real flags later widens what drives those 2 bits without reshaping the ROM
 *       or renumbering any existing address.
 * </ul>
 *
 * <p>The microcode ROM is the only decode logic: its address is
 * {@code {opcode[7:0], flags[1:0], microstep[1:0]}} (flags currently tied to 0) and every
 * control line is one bit of its output word — there is no opcode comparator anywhere in
 * the circuit. Step 0 of every one of the 256 possible opcodes is programmed identically to
 * "fetch" (PC_INC + IR_LOAD), so the fetch cycle is uniform and the decode table only needs
 * real content for the microsteps of the opcodes this v0 actually implements.
 *
 * <p>ISA: {@code LDI Rn,imm8}, {@code ADD/SUB R0,R1} (result in R0), {@code MOV R0,R1} /
 * {@code MOV R1,R0}, {@code STORE R0,[addr16]}, {@code LOAD R0,[addr16]}, {@code JMP addr16}
 * and {@code HLT}. Each new instruction after the original LDI/ADD/STORE/HLT slice landed as
 * a microcode table row plus a small, targeted datapath addition — R0's write-back mux grew
 * from 2 to effectively 4 sources (immediate, ALU result, R1, RAM), R1 gained a 2-way
 * write-back mux, and PC gained a real LOAD path fed by the same MAR_LO/MAR_HI registers
 * STORE and LOAD already use for their address — confirming the fetch/decode/execute loop
 * itself did not need to change to add instructions.
 *
 * <p>Wide buses are assembled with natural whole endpoints on the narrow source and a range
 * on the wide destination. The compiler plans both sides as LSB-relative bit atoms for that
 * connection, so the circuit document does not need artificial self-full-range slices.
 */
class Lf8CpuIntegrationTest {

    @TempDir
    Path directory;

    // Control word bit positions — the single source of truth for both the microcode table
    // below and the wiring that fans MICROCODE_ROM.DATA out to each control line.
    private static final int PC_INC = 0;
    private static final int IR_LOAD = 1;
    private static final int R0_LOAD_IMM = 2;
    private static final int R1_LOAD_IMM = 3;
    private static final int MAR_LO_LOAD = 4;
    private static final int MAR_HI_LOAD = 5;
    private static final int ALU_LOAD = 6;
    private static final int WE = 7;
    private static final int HALT_BIT = 8;
    private static final int ALU_SUB = 9;
    private static final int MOV_R0_FROM_R1 = 10;
    private static final int MOV_R1_FROM_R0 = 11;
    private static final int LOAD_R0_FROM_RAM = 12;
    private static final int PC_LOAD = 13;
    private static final int CONTROL_WORD_WIDTH = 14;

    private static final int OP_LDI_R0 = 0x01;
    private static final int OP_LDI_R1 = 0x02;
    private static final int OP_ADD_R0_R1 = 0x03;
    private static final int OP_SUB_R0_R1 = 0x04;
    private static final int OP_MOV_R0_R1 = 0x05;
    private static final int OP_MOV_R1_R0 = 0x06;
    private static final int OP_STORE_R0 = 0x07;
    private static final int OP_LOAD_R0 = 0x08;
    private static final int OP_JMP = 0x09;
    private static final int OP_HLT = 0xFF;

    private static final int MICROSTEPS = 4;
    private static final int FLAG_SLOTS = 4; // 2 reserved flag bits, tied to 0 today
    private static final int MICROCODE_WORDS = 256 * FLAG_SLOTS * MICROSTEPS;

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
        CompilationResult compiled = compileAndRoundTrip(main);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(main, compiled);

        runToHalt(simulation, probe);

        assertEquals(LogicVector.fromUnsignedLong(0x08, 8),
                simulation.memoryPage(probe.ramId, 0x2000, 1).orElseThrow().wordAt(0x2000),
                "RAM[0x2000] must hold 5 + 3 = 8");
    }

    /**
     * Exercises every instruction added after the original v0 slice: SUB, both directions
     * of MOV, STORE followed by LDI-then-LOAD (so LOAD is only correct if it actually reads
     * RAM rather than retaining R0's previous value), and JMP over a trap HLT.
     */
    @Test
    void widenedIsaExecutesSubMovLoadAndJmpCorrectly() {
        int[] program = {
                /*  0 */ OP_LDI_R0, 10,
                /*  2 */ OP_LDI_R1, 4,
                /*  4 */ OP_SUB_R0_R1,       // R0 = 10 - 4 = 6
                /*  5 */ OP_MOV_R1_R0,       // R1 = R0 = 6
                /*  6 */ OP_ADD_R0_R1,       // R0 = 6 + 6 = 12
                /*  7 */ OP_MOV_R0_R1,       // R0 = R1 = 6
                /*  8 */ OP_STORE_R0, 0x00, 0x30,  // RAM[0x3000] = 6
                /* 11 */ OP_LDI_R0, 99,      // R0 = 99 (so LOAD is only correct if it truly reads RAM)
                /* 13 */ OP_LOAD_R0, 0x00, 0x30,   // R0 = RAM[0x3000] = 6
                /* 16 */ OP_JMP, 0x14, 0x00,  // jump to address 20, skipping the trap HLT at 19
                /* 19 */ OP_HLT,             // trap: must never execute
                /* 20 */ OP_LDI_R1, 77,      // proves the jump landed here
                /* 22 */ OP_HLT,
        };

        CircuitDocument main = buildCircuit(program);
        CompilationResult compiled = compileAndRoundTrip(main);
        Simulation simulation = new Simulation(compiled.circuit());
        CompiledProbe probe = probeOf(main, compiled);

        runToHalt(simulation, probe);

        assertEquals(LogicVector.fromUnsignedLong(6, 8),
                simulation.memoryPage(probe.ramId, 0x3000, 1).orElseThrow().wordAt(0x3000),
                "RAM[0x3000] must hold the SUB/MOV result, 6");
        assertEquals(LogicVector.fromUnsignedLong(6, 8), simulation.readNet(probe.r0Net),
                "R0 must hold the value LOAD read back from RAM, not the LDI 99 that preceded it");
        assertEquals(LogicVector.fromUnsignedLong(77, 8), simulation.readNet(probe.r1Net),
                "R1 == 77 only if JMP actually skipped the trap HLT at address 19");
    }

    /**
     * Saves and reloads through the real project file format before compiling — the same
     * lesson as the earlier nested/bit-mode/ROM-contents round-trip test: this must survive
     * shipping as a file, not just as an in-memory document.
     */
    private CompilationResult compileAndRoundTrip(CircuitDocument main) {
        CircuitProject project = CircuitProject.of("lf8", main);
        Path file = directory.resolve(java.util.UUID.randomUUID() + "." + ProjectFormat.EXTENSION);
        ProjectFormat.save(project, file);
        CircuitProject loaded = ProjectFormat.load(file);
        return new CircuitCompiler(ComponentRegistry.standard()).compile(loaded, "main");
    }

    /** The handful of runtime handles every test needs, resolved once after compilation. */
    private record CompiledProbe(int clkId, int resetId, int halted, int ramId, int r0Net, int r1Net) {
    }

    /**
     * Root-level component UUIDs survive both the save/load round trip and flattening
     * unchanged, so {@code main}'s own component ids are valid keys into {@code compiled}.
     */
    private CompiledProbe probeOf(CircuitDocument main, CompilationResult compiled) {
        int clkId = compiled.componentByLabel("CLK").orElseThrow();
        int resetId = compiled.componentByLabel("RESET").orElseThrow();
        int ramId = compiled.componentByLabel("RAM").orElseThrow();
        int halted = compiled.sourceMap().netOf(new PortReference(componentId(main, "HALT_LATCH"), "Q")).orElseThrow();
        int r0Net = compiled.sourceMap().netOf(new PortReference(componentId(main, "R0"), "Q")).orElseThrow();
        int r1Net = compiled.sourceMap().netOf(new PortReference(componentId(main, "R1"), "Q")).orElseThrow();
        return new CompiledProbe(clkId, resetId, halted, ramId, r0Net, r1Net);
    }

    private java.util.UUID componentId(CircuitDocument document, String label) {
        return document.components().stream().filter(c -> label.equals(c.label())).findFirst()
                .orElseThrow(() -> new IllegalStateException("No component labeled " + label)).id();
    }

    private void runToHalt(Simulation simulation, CompiledProbe probe) {
        simulation.setInput(probe.resetId, LogicState.ONE);
        simulation.setInput(probe.resetId, LogicState.ZERO);

        int edges = 0;
        int maxEdges = 400;
        while (simulation.readNet(probe.halted).singleBit() != LogicState.ONE && edges < maxEdges) {
            simulation.setInput(probe.clkId, LogicState.ZERO);
            simulation.setInput(probe.clkId, LogicState.ONE);
            edges++;
        }

        assertTrue(edges < maxEdges, "CPU did not halt within " + maxEdges + " clock edges");
        assertEquals(LogicState.ONE, simulation.readNet(probe.halted).singleBit());
    }

    // ------------------------------------------------------------------
    // Circuit construction
    // ------------------------------------------------------------------

    private CircuitDocument buildCircuit(int[] program) {
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", "LF-8"));
        ComponentRegistry registry = ComponentRegistry.standard();

        ComponentInstance clk = add(main, registry, "source.toggle", 0, 0, ParameterValues.empty(), "CLK");
        ComponentInstance reset = add(main, registry, "source.toggle", 0, 40, ParameterValues.empty(), "RESET");

        ComponentInstance pc = add(main, registry, "sequential.loadable_counter", 100, 0,
                registry.require("sequential.loadable_counter").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 16),
                "PC");

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

        ComponentInstance alu = add(main, registry, "arithmetic.add_sub", 400, 60,
                registry.require("arithmetic.add_sub").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8),
                "ALU");

        // R0 write-back: (imm or ALU result) or (RAM data or R1), chosen by whether this is
        // an immediate/arithmetic op or a MOV-from-R1/LOAD op.
        ComponentInstance r0MuxImmAlu = add(main, registry, "routing.mux2", 500, 40,
                registry.require("routing.mux2").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8),
                "R0_MUX_IMM_ALU");
        ComponentInstance r0MuxRamR1 = add(main, registry, "routing.mux2", 500, 80,
                registry.require("routing.mux2").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8),
                "R0_MUX_RAM_R1");
        ComponentInstance r0MuxOuter = add(main, registry, "routing.mux2", 500, 120,
                registry.require("routing.mux2").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8),
                "R0_MUX_OUTER");
        ComponentInstance r0OuterSel = add(main, registry, "logic.or", 500, 160, ParameterValues.empty(), "R0_OUTER_SEL");
        ComponentInstance r0LoadOr = add(main, registry, "logic.or", 500, 200,
                registry.require("logic.or").definition().defaultParameters()
                        .with(LibraryParameters.INPUT_COUNT, 4),
                "R0_LOAD_OR");

        // R1 write-back: immediate, or R0 (for MOV R1,R0).
        ComponentInstance r1Mux = add(main, registry, "routing.mux2", 600, 160,
                registry.require("routing.mux2").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8),
                "R1_MUX");
        ComponentInstance r1LoadOr = add(main, registry, "logic.or", 600, 200, ParameterValues.empty(), "R1_LOAD_OR");

        ComponentInstance microstep = add(main, registry, "sequential.modulo_counter", 300, 300,
                registry.require("sequential.modulo_counter").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 2)
                        .with(LibraryParameters.MODULUS, MICROSTEPS),
                "MICROSTEP");

        ComponentInstance haltLatch = add(main, registry, "sequential.register_reset", 700, 200,
                registry.require("sequential.register_reset").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 1),
                "HALT_LATCH");
        ComponentInstance haltOr = add(main, registry, "logic.or", 700, 240, ParameterValues.empty(), "HALT_OR");
        ComponentInstance haltLoadTie = constant(main, registry, 700, 280, 1, 1, "HALT_LOAD_TIE");
        ComponentInstance haltNot = add(main, registry, "logic.not", 700, 320, ParameterValues.empty(), "HALT_NOT");

        ComponentInstance microcodeRom = add(main, registry, "memory.rom", 500, 340,
                registry.require("memory.rom").definition().defaultParameters()
                        .with(LibraryParameters.ADDRESS_WIDTH, 12)
                        .with(LibraryParameters.WIDTH, CONTROL_WORD_WIDTH)
                        .with(LibraryParameters.ROM_CONTENTS, microcodeContents()),
                "MICROCODE_ROM");
        ComponentInstance microcodeRomEn = constant(main, registry, 500, 380, 1, 1, "MICROCODE_ROM_EN");
        ComponentInstance flagsTie = constant(main, registry, 500, 420, 2, 0, "FLAGS_TIE");

        ComponentInstance ram = add(main, registry, "memory.ram", 800, 0,
                registry.require("memory.ram").definition().defaultParameters()
                        .with(LibraryParameters.ADDRESS_WIDTH, 16)
                        .with(LibraryParameters.WIDTH, 8),
                "RAM");
        ComponentInstance ramCsTie = constant(main, registry, 800, 40, 1, 1, "RAM_CS_TIE");
        ComponentInstance r0ToRamDriver = add(main, registry, "routing.tristate_n", 800, 80,
                registry.require("routing.tristate_n").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8),
                "R0_TO_RAM_DRIVER");

        // Clock and reset fan-out.
        for (ComponentInstance target : List.of(pc, ir, r0, r1, marLo, marHi, microstep, haltLatch)) {
            wire(main, clk, "OUT", target, "CLK");
        }
        wire(main, reset, "OUT", pc, "RESET");
        wire(main, reset, "OUT", microstep, "RESET");
        wire(main, reset, "OUT", haltLatch, "RESET");

        // Program counter / fetch. PC.DATA is permanently fed by MAR_HI:MAR_LO — the same
        // registers STORE/LOAD already fetch an address into — and only actually taken when
        // PC_LOAD (JMP) asserts LOAD; the rest of the time PC.LOAD is 0 and DATA is ignored.
        wire(main, pc, "COUNT", progRom, "ADDRESS");
        main.addConnection(Connection.create(PortEndpoint.whole(new PortReference(marLo.id(), "Q")),
                PortEndpoint.range(new PortReference(pc.id(), "DATA"), 7, 0)));
        main.addConnection(Connection.create(PortEndpoint.whole(new PortReference(marHi.id(), "Q")),
                PortEndpoint.range(new PortReference(pc.id(), "DATA"), 15, 8)));
        wire(main, progRomEn, "OUT", progRom, "ENABLE");
        wire(main, progRom, "DATA", ir, "DATA");
        wire(main, progRom, "DATA", r1Mux, "IN0");
        wire(main, progRom, "DATA", marLo, "DATA");
        wire(main, progRom, "DATA", marHi, "DATA");
        wire(main, progRom, "DATA", r0MuxImmAlu, "IN0");

        // ALU (add/sub) feeding R0's write-back mux tree.
        wire(main, r0, "Q", alu, "A");
        wire(main, r1, "Q", alu, "B");
        wire(main, alu, "RESULT", r0MuxImmAlu, "IN1");
        wire(main, ram, "DATA", r0MuxRamR1, "IN0");
        wire(main, r1, "Q", r0MuxRamR1, "IN1");
        wire(main, r0MuxImmAlu, "OUT", r0MuxOuter, "IN0");
        wire(main, r0MuxRamR1, "OUT", r0MuxOuter, "IN1");
        wire(main, r0MuxOuter, "OUT", r0, "DATA");
        wire(main, r0LoadOr, "OUT", r0, "LOAD");

        // R1 write-back: immediate, or R0 for MOV R1,R0.
        wire(main, r1Mux, "OUT", r1, "DATA");
        wire(main, r0, "Q", r1Mux, "IN1");
        // (r1Mux.IN0 already wired above from progRom.DATA)
        wire(main, r1LoadOr, "OUT", r1, "LOAD");

        // Microcode addressing: {opcode, flags (tied 0 for now), microstep}. Every
        // multi-bit source maps naturally, LSB-relative, into its destination range.
        main.addConnection(Connection.create(PortEndpoint.whole(new PortReference(ir.id(), "Q")),
                PortEndpoint.range(new PortReference(microcodeRom.id(), "ADDRESS"), 11, 4)));
        main.addConnection(Connection.create(PortEndpoint.whole(new PortReference(flagsTie.id(), "OUT")),
                PortEndpoint.range(new PortReference(microcodeRom.id(), "ADDRESS"), 3, 2)));
        main.addConnection(Connection.create(PortEndpoint.whole(new PortReference(microstep.id(), "COUNT")),
                PortEndpoint.range(new PortReference(microcodeRom.id(), "ADDRESS"), 1, 0)));
        wire(main, microcodeRomEn, "OUT", microcodeRom, "ENABLE");

        // Microcode control-line fan-out — the only decode logic in the whole circuit.
        bitWire(main, microcodeRom, "DATA", PC_INC, pc, "ENABLE");
        bitWire(main, microcodeRom, "DATA", IR_LOAD, ir, "LOAD");
        bitWire(main, microcodeRom, "DATA", R0_LOAD_IMM, r0LoadOr, "IN0");
        bitWire(main, microcodeRom, "DATA", R1_LOAD_IMM, r1LoadOr, "IN0");
        bitWire(main, microcodeRom, "DATA", MAR_LO_LOAD, marLo, "LOAD");
        bitWire(main, microcodeRom, "DATA", MAR_HI_LOAD, marHi, "LOAD");
        bitWire(main, microcodeRom, "DATA", ALU_LOAD, r0MuxImmAlu, "SEL");
        bitWire(main, microcodeRom, "DATA", ALU_LOAD, r0LoadOr, "IN1");
        bitWire(main, microcodeRom, "DATA", ALU_SUB, alu, "SUB");
        bitWire(main, microcodeRom, "DATA", WE, ram, "WE");
        bitWire(main, microcodeRom, "DATA", WE, r0ToRamDriver, "ENABLE");
        bitWire(main, microcodeRom, "DATA", MOV_R0_FROM_R1, r0MuxRamR1, "SEL");
        bitWire(main, microcodeRom, "DATA", MOV_R0_FROM_R1, r0OuterSel, "IN0");
        bitWire(main, microcodeRom, "DATA", MOV_R0_FROM_R1, r0LoadOr, "IN2");
        bitWire(main, microcodeRom, "DATA", LOAD_R0_FROM_RAM, r0OuterSel, "IN1");
        bitWire(main, microcodeRom, "DATA", LOAD_R0_FROM_RAM, r0LoadOr, "IN3");
        bitWire(main, microcodeRom, "DATA", LOAD_R0_FROM_RAM, ram, "OE");
        wire(main, r0OuterSel, "OUT", r0MuxOuter, "SEL");
        bitWire(main, microcodeRom, "DATA", MOV_R1_FROM_R0, r1Mux, "SEL");
        bitWire(main, microcodeRom, "DATA", MOV_R1_FROM_R0, r1LoadOr, "IN1");
        bitWire(main, microcodeRom, "DATA", PC_LOAD, pc, "LOAD");
        bitWire(main, microcodeRom, "DATA", HALT_BIT, haltOr, "IN0");

        // Sticky HALT latch: once set, freezes the microstep counter, which freezes
        // everything else since the whole datapath is driven by that now-static microcode row.
        wire(main, haltOr, "OUT", haltLatch, "DATA");
        wire(main, haltLatch, "Q", haltOr, "IN1");
        wire(main, haltLoadTie, "OUT", haltLatch, "LOAD");
        wire(main, haltLatch, "Q", haltNot, "A");
        wire(main, haltNot, "Y", microstep, "ENABLE");

        // Data memory: address assembled from MAR_HI:MAR_LO. R0 drives DATA only while WE
        // is active (a real tri-state gate, since RAM itself now also drives DATA during a
        // LOAD's OE-active read) — R0.Q never contends with RAM's own output.
        main.addConnection(Connection.create(PortEndpoint.whole(new PortReference(marLo.id(), "Q")),
                PortEndpoint.range(new PortReference(ram.id(), "ADDRESS"), 7, 0)));
        main.addConnection(Connection.create(PortEndpoint.whole(new PortReference(marHi.id(), "Q")),
                PortEndpoint.range(new PortReference(ram.id(), "ADDRESS"), 15, 8)));
        wire(main, r0, "Q", r0ToRamDriver, "A");
        wire(main, r0ToRamDriver, "Y", ram, "DATA");
        wire(main, ramCsTie, "OUT", ram, "CS");

        return main;
    }

    /** {@code microcode[{opcode, flags=0, microstep}]}: the sole decode logic. */
    private static String microcodeContents() {
        int[] microcode = new int[MICROCODE_WORDS];
        int fetch = bit(PC_INC) | bit(IR_LOAD);
        for (int opcode = 0; opcode < 256; opcode++) {
            microcode[address(opcode, 0)] = fetch;
        }
        microcode[address(OP_LDI_R0, 1)] = bit(PC_INC) | bit(R0_LOAD_IMM);
        microcode[address(OP_LDI_R1, 1)] = bit(PC_INC) | bit(R1_LOAD_IMM);
        microcode[address(OP_ADD_R0_R1, 1)] = bit(ALU_LOAD);
        microcode[address(OP_SUB_R0_R1, 1)] = bit(ALU_LOAD) | bit(ALU_SUB);
        microcode[address(OP_MOV_R0_R1, 1)] = bit(MOV_R0_FROM_R1);
        microcode[address(OP_MOV_R1_R0, 1)] = bit(MOV_R1_FROM_R0);
        microcode[address(OP_STORE_R0, 1)] = bit(PC_INC) | bit(MAR_LO_LOAD);
        microcode[address(OP_STORE_R0, 2)] = bit(PC_INC) | bit(MAR_HI_LOAD);
        microcode[address(OP_STORE_R0, 3)] = bit(WE);
        microcode[address(OP_LOAD_R0, 1)] = bit(PC_INC) | bit(MAR_LO_LOAD);
        microcode[address(OP_LOAD_R0, 2)] = bit(PC_INC) | bit(MAR_HI_LOAD);
        microcode[address(OP_LOAD_R0, 3)] = bit(LOAD_R0_FROM_RAM);
        microcode[address(OP_JMP, 1)] = bit(PC_INC) | bit(MAR_LO_LOAD);
        microcode[address(OP_JMP, 2)] = bit(PC_INC) | bit(MAR_HI_LOAD);
        microcode[address(OP_JMP, 3)] = bit(PC_LOAD);
        microcode[address(OP_HLT, 1)] = bit(HALT_BIT);
        microcode[address(OP_HLT, 2)] = bit(HALT_BIT);
        microcode[address(OP_HLT, 3)] = bit(HALT_BIT);

        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < microcode.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(Integer.toHexString(microcode[i]));
        }
        return csv.toString();
    }

    /** Flags are tied to 0 today; this is the one place their eventual field lives. */
    private static int address(int opcode, int microstep) {
        return (opcode * FLAG_SLOTS + 0) * MICROSTEPS + microstep;
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
