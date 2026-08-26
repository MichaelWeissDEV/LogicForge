package dev.logicforge.processor.lf8;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitMetadata;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.SubcircuitSupport;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.LibraryParameters;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Builds the structural LF-8 computer from ordinary LogicForge components. */
public final class Lf8CircuitFactory {

    public static final String CPU_CIRCUIT = "LF8_CPU";
    public static final String DATAPATH_CIRCUIT = "LF8_DATAPATH";
    public static final String CONTROL_CIRCUIT = "LF8_CONTROL";

    private static final List<Lf8ControlSignal> DATAPATH_CONTROLS = List.of(
            Lf8ControlSignal.PC_INCREMENT,
            Lf8ControlSignal.IR_LOAD,
            Lf8ControlSignal.DESTINATION_REGISTER_LOAD,
            Lf8ControlSignal.SOURCE_REGISTER_LOAD,
            Lf8ControlSignal.MAR_LOW_LOAD,
            Lf8ControlSignal.MAR_HIGH_LOAD,
            Lf8ControlSignal.REGISTER_FILE_WRITE,
            Lf8ControlSignal.ALU_SOURCE,
            Lf8ControlSignal.MOV_SOURCE,
            Lf8ControlSignal.ALTERNATE_SOURCE,
            Lf8ControlSignal.MEMORY_WRITE,
            Lf8ControlSignal.PC_LOAD,
            Lf8ControlSignal.ADDRESS_FROM_MAR,
            Lf8ControlSignal.FLAGS_LOAD,
            Lf8ControlSignal.FLAGS_PRESERVE_CARRY,
            Lf8ControlSignal.FLAGS_PRESERVE_OVERFLOW,
            Lf8ControlSignal.ALU_CARRY_IN,
            Lf8ControlSignal.ALU_B_ONE,
            Lf8ControlSignal.ADDRESS_FROM_SP,
            Lf8ControlSignal.SP_INCREMENT,
            Lf8ControlSignal.SP_DECREMENT,
            Lf8ControlSignal.PC_LOW_TO_DATA,
            Lf8ControlSignal.PC_HIGH_TO_DATA,
            Lf8ControlSignal.SOURCE_TO_DATA,
            Lf8ControlSignal.FLAGS_TO_DATA,
            Lf8ControlSignal.FLAGS_FROM_DATA,
            Lf8ControlSignal.VECTOR_LOW_TO_DATA,
            Lf8ControlSignal.VECTOR_HIGH_TO_DATA);

    /**
     * Temporary fixed interrupt handler entry point, until a real vector table (P1.3) reads
     * it from memory instead. An assembly program using interrupts must place its ISR (or a
     * {@code JMP} to it) at this address.
     */
    public static final int IRQ_HANDLER_ADDRESS = 0x0008;

    private Lf8CircuitFactory() {
    }

    /** Creates the complete four-document LF-8 computer project. */
    public static CircuitProject createProject(int... program) {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument datapath = createDatapath(registry);
        CircuitDocument control = createControl(registry);
        CircuitDocument cpu = createCpu(registry);
        CircuitDocument main = createMain(registry, program);
        CircuitProject project = CircuitProject.of("lf8", main);
        project.putCircuit(cpu);
        project.putCircuit(datapath);
        project.putCircuit(control);
        return project;
    }

    /** Retained for callers that only need the top-level computer schematic. */
    public static CircuitDocument createComputerCircuit(int... program) {
        return createProject(program).mainCircuit();
    }

    private static CircuitDocument createDatapath(ComponentRegistry registry) {
        CircuitDocument document = document(DATAPATH_CIRCUIT, "LF-8 datapath");
        ComponentInstance clk = input(document, registry, 0, 0, "CLK", 1);
        ComponentInstance reset = input(document, registry, 0, 40, "RESET", 1);
        ComponentInstance data = inout(document, registry, 1000, 100, "DATA", 8);
        ComponentInstance address = output(document, registry, 1000, 180, "ADDRESS", 16);
        ComponentInstance opcode = output(document, registry, 1000, 240, "OPCODE", 8);
        ComponentInstance flagsOut = output(document, registry, 1000, 300, "FLAGS", 4);
        ComponentInstance aluOp = input(document, registry, 0, 600, "ALU_OP", 4);

        Map<Lf8ControlSignal, ComponentInstance> controls = new EnumMap<>(Lf8ControlSignal.class);
        int controlY = 80;
        for (Lf8ControlSignal signal : DATAPATH_CONTROLS) {
            controls.put(signal, input(document, registry, 0, controlY, signal.name(), 1));
            controlY += 35;
        }

        ComponentInstance pc = add(document, registry, "sequential.loadable_counter", 180, 0,
                defaults(registry, "sequential.loadable_counter")
                        .with(LibraryParameters.WIDTH, 16), "PC");
        ComponentInstance ir = register(document, registry, 260, 60, 8, "IR");
        ComponentInstance destination = register(document, registry, 260, 120, 3, "DESTINATION");
        ComponentInstance source = register(document, registry, 260, 180, 3, "SOURCE");
        ComponentInstance operandSlice = add(document, registry, "routing.bus_slice", 180, 150,
                defaults(registry, "routing.bus_slice")
                        .with(LibraryParameters.INPUT_WIDTH, 8)
                        .with(LibraryParameters.OUTPUT_WIDTH, 3)
                        .with(LibraryParameters.SLICE_LSB, 0), "OPERAND_SLICE");
        ComponentInstance marLow = register(document, registry, 260, 240, 8, "MAR_LO");
        ComponentInstance marHigh = register(document, registry, 260, 300, 8, "MAR_HI");
        ComponentInstance registers = add(document, registry, "memory.register_file", 430, 110,
                defaults(registry, "memory.register_file")
                        .with(LibraryParameters.WIDTH, 8)
                        .with(LibraryParameters.REGISTER_COUNT, 8), "REGISTER_FILE");
        ComponentInstance alu = add(document, registry, "arithmetic.alu", 570, 110,
                defaults(registry, "arithmetic.alu").with(LibraryParameters.WIDTH, 8), "ALU");
        ComponentInstance aluB = mux(document, registry, 520, 170, 8, "ALU_B_SOURCE");
        ComponentInstance one = constant(document, registry, 430, 210, 8, 1, "ALU_ONE");

        ComponentInstance immediateOrAlu = mux(document, registry, 680, 60, 8,
                "WRITE_IMMEDIATE_ALU");
        ComponentInstance memoryOrMove = mux(document, registry, 680, 130, 8,
                "WRITE_MEMORY_MOVE");
        ComponentInstance writeSource = mux(document, registry, 790, 95, 8, "WRITE_SOURCE");
        ComponentInstance addressConcat = add(document, registry, "routing.bus_concat", 480, 270,
                defaults(registry, "routing.bus_concat")
                        .with(LibraryParameters.LOW_WIDTH, 8)
                        .with(LibraryParameters.HIGH_WIDTH, 8), "MAR_ADDRESS");
        ComponentInstance addressSource = mux(document, registry, 690, 270, 16, "ADDRESS_SOURCE");
        ComponentInstance sp = add(document, registry, "sequential.loadable_counter", 480, 340,
                defaults(registry, "sequential.loadable_counter")
                        .with(LibraryParameters.WIDTH, 16)
                        .with(LibraryParameters.RESET_VALUE, "bfff"), "SP");
        ComponentInstance spDecrementer = add(document, registry, "arithmetic.decrementer", 400, 340,
                defaults(registry, "arithmetic.decrementer").with(LibraryParameters.WIDTH, 16),
                "SP_DECREMENTER");
        ComponentInstance addressWithSp = mux(document, registry, 800, 300, 16, "ADDRESS_WITH_SP");
        ComponentInstance pcLowSlice = slice(document, registry, 180, 340, 16, 8, "PC_LOW_SLICE");
        ComponentInstance pcHighSlice = add(document, registry, "routing.bus_slice", 180, 380,
                defaults(registry, "routing.bus_slice")
                        .with(LibraryParameters.INPUT_WIDTH, 16)
                        .with(LibraryParameters.OUTPUT_WIDTH, 8)
                        .with(LibraryParameters.SLICE_LSB, 8), "PC_HIGH_SLICE");
        ComponentInstance pcLowDriver = add(document, registry, "routing.tristate_n", 850, 200,
                defaults(registry, "routing.tristate_n").with(LibraryParameters.WIDTH, 8),
                "PC_LOW_DRIVER");
        ComponentInstance pcHighDriver = add(document, registry, "routing.tristate_n", 850, 240,
                defaults(registry, "routing.tristate_n").with(LibraryParameters.WIDTH, 8),
                "PC_HIGH_DRIVER");
        ComponentInstance writeDriver = add(document, registry, "routing.tristate_n", 850, 150,
                defaults(registry, "routing.tristate_n").with(LibraryParameters.WIDTH, 8),
                "DATA_WRITE_DRIVER");
        ComponentInstance flags = add(document, registry, "sequential.flags_register", 820, 330,
                defaults(registry, "sequential.flags_register"), "FLAGS_REGISTER");
        ComponentInstance storedCarry = bitSlice(document, registry, 650, 350, 4, 1,
                "STORED_CARRY");
        ComponentInstance storedOverflow = bitSlice(document, registry, 650, 400, 4, 3,
                "STORED_OVERFLOW");
        ComponentInstance carryInput = mux(document, registry, 730, 350, 1, "CARRY_INPUT");
        ComponentInstance overflowInput = mux(document, registry, 730, 400, 1, "OVERFLOW_INPUT");

        // FLAGS_REGISTER's next value is either the live ALU-derived nibble (as before) or,
        // for IRET, a popped stack byte's low nibble. Z/C/N/V are joined into one 4-bit bus,
        // muxed against the popped byte, then split back out to the register's four separate
        // 1-bit inputs.
        ComponentInstance liveFlagsJoiner = add(document, registry, "routing.joiner", 760, 350,
                defaults(registry, "routing.joiner").with(LibraryParameters.WIDTH, 4),
                "LIVE_FLAGS_JOINER");
        ComponentInstance poppedFlags = slice(document, registry, 760, 450, 8, 4, "POPPED_FLAGS");
        ComponentInstance flagsSource = mux(document, registry, 790, 400, 4, "FLAGS_SOURCE");
        ComponentInstance flagsSourceSplitter = add(document, registry, "routing.splitter", 800, 330,
                defaults(registry, "routing.splitter").with(LibraryParameters.WIDTH, 4),
                "FLAGS_SOURCE_SPLITTER");

        // Pushing FLAGS onto the stack drives the live nibble, zero-extended to a byte, onto
        // DATA - independent of the restore mux above, which only affects the register's own
        // next-value input.
        ComponentInstance flagsZeroPad = constant(document, registry, 850, 470, 4, 0,
                "FLAGS_ZERO_PAD");
        ComponentInstance flagsByte = add(document, registry, "routing.bus_concat", 850, 500,
                defaults(registry, "routing.bus_concat")
                        .with(LibraryParameters.LOW_WIDTH, 4)
                        .with(LibraryParameters.HIGH_WIDTH, 4), "FLAGS_BYTE");
        ComponentInstance flagsDriver = add(document, registry, "routing.tristate_n", 900, 500,
                defaults(registry, "routing.tristate_n").with(LibraryParameters.WIDTH, 8),
                "FLAGS_DRIVER");

        // Temporary fixed IRQ handler address (IRQ_HANDLER_ADDRESS), driven onto DATA and
        // staged through MAR exactly like a JMP/CALL target, until vectors-in-memory (P1.3)
        // replace these constants with a real memory read.
        ComponentInstance handlerLowConst = constant(document, registry, 60, 500, 8,
                IRQ_HANDLER_ADDRESS & 0xff, "IRQ_HANDLER_LOW_CONST");
        ComponentInstance handlerHighConst = constant(document, registry, 60, 540, 8,
                (IRQ_HANDLER_ADDRESS >>> 8) & 0xff, "IRQ_HANDLER_HIGH_CONST");
        ComponentInstance handlerLowDriver = add(document, registry, "routing.tristate_n", 130, 500,
                defaults(registry, "routing.tristate_n").with(LibraryParameters.WIDTH, 8),
                "IRQ_HANDLER_LOW_DRIVER");
        ComponentInstance handlerHighDriver = add(document, registry, "routing.tristate_n", 130, 540,
                defaults(registry, "routing.tristate_n").with(LibraryParameters.WIDTH, 8),
                "IRQ_HANDLER_HIGH_DRIVER");

        for (ComponentInstance target : List.of(pc, ir, destination, source, marLow, marHigh, registers, sp)) {
            wire(document, clk, "OUT", target, "CLK");
        }
        wire(document, clk, "OUT", flags, "CLK");
        wire(document, reset, "OUT", pc, "RESET");
        wire(document, reset, "OUT", sp, "RESET");

        wire(document, data, "BUS", ir, "DATA");
        wire(document, data, "BUS", operandSlice, "IN");
        wire(document, data, "BUS", marLow, "DATA");
        wire(document, data, "BUS", marHigh, "DATA");
        wire(document, data, "BUS", immediateOrAlu, "IN0");
        wire(document, data, "BUS", memoryOrMove, "IN0");
        wire(document, operandSlice, "OUT", destination, "DATA");
        wire(document, operandSlice, "OUT", source, "DATA");

        wire(document, destination, "Q", registers, "RD_ADDR_A");
        wire(document, destination, "Q", registers, "WR_ADDR");
        wire(document, source, "Q", registers, "RD_ADDR_B");
        wire(document, registers, "RD_DATA_A", alu, "A");
        wire(document, registers, "RD_DATA_B", aluB, "IN0");
        wire(document, one, "OUT", aluB, "IN1");
        wire(document, aluB, "OUT", alu, "B");
        wire(document, aluOp, "OUT", alu, "OP");
        wire(document, alu, "RESULT", immediateOrAlu, "IN1");
        wire(document, registers, "RD_DATA_B", memoryOrMove, "IN1");
        wire(document, immediateOrAlu, "OUT", writeSource, "IN0");
        wire(document, memoryOrMove, "OUT", writeSource, "IN1");
        wire(document, writeSource, "OUT", registers, "WR_DATA");

        wire(document, marLow, "Q", addressConcat, "LOW");
        wire(document, marHigh, "Q", addressConcat, "HIGH");
        wire(document, addressConcat, "OUT", pc, "DATA");
        wire(document, pc, "COUNT", addressSource, "IN0");
        wire(document, addressConcat, "OUT", addressSource, "IN1");
        wire(document, sp, "COUNT", spDecrementer, "A");
        wire(document, spDecrementer, "OUT", sp, "DATA");
        wire(document, addressSource, "OUT", addressWithSp, "IN0");
        wire(document, sp, "COUNT", addressWithSp, "IN1");
        wire(document, addressWithSp, "OUT", address, "IN");
        wire(document, pc, "COUNT", pcLowSlice, "IN");
        wire(document, pc, "COUNT", pcHighSlice, "IN");
        wire(document, pcLowSlice, "OUT", pcLowDriver, "A");
        wire(document, pcHighSlice, "OUT", pcHighDriver, "A");
        wire(document, pcLowDriver, "Y", data, "BUS");
        wire(document, pcHighDriver, "Y", data, "BUS");
        wire(document, registers, "RD_DATA_B", writeDriver, "A");
        wire(document, writeDriver, "Y", data, "BUS");
        wire(document, ir, "Q", opcode, "IN");
        wire(document, alu, "CARRY", carryInput, "IN0");
        wire(document, alu, "OVERFLOW", overflowInput, "IN0");
        wire(document, flags, "FLAGS", storedCarry, "IN");
        wire(document, flags, "FLAGS", storedOverflow, "IN");
        wire(document, storedCarry, "OUT", carryInput, "IN1");
        wire(document, storedOverflow, "OUT", overflowInput, "IN1");
        wire(document, flags, "FLAGS", flagsOut, "IN");

        wire(document, alu, "ZERO", liveFlagsJoiner, "BIT0");
        wire(document, carryInput, "OUT", liveFlagsJoiner, "BIT1");
        wire(document, alu, "NEGATIVE", liveFlagsJoiner, "BIT2");
        wire(document, overflowInput, "OUT", liveFlagsJoiner, "BIT3");
        wire(document, data, "BUS", poppedFlags, "IN");
        wire(document, liveFlagsJoiner, "BUS", flagsSource, "IN0");
        wire(document, poppedFlags, "OUT", flagsSource, "IN1");
        wire(document, flagsSource, "OUT", flagsSourceSplitter, "BUS");
        wire(document, flagsSourceSplitter, "BIT0", flags, "Z");
        wire(document, flagsSourceSplitter, "BIT1", flags, "C");
        wire(document, flagsSourceSplitter, "BIT2", flags, "N");
        wire(document, flagsSourceSplitter, "BIT3", flags, "V");

        wire(document, flags, "FLAGS", flagsByte, "LOW");
        wire(document, flagsZeroPad, "OUT", flagsByte, "HIGH");
        wire(document, flagsByte, "OUT", flagsDriver, "A");
        wire(document, flagsDriver, "Y", data, "BUS");

        wire(document, handlerLowConst, "OUT", handlerLowDriver, "A");
        wire(document, handlerHighConst, "OUT", handlerHighDriver, "A");
        wire(document, handlerLowDriver, "Y", data, "BUS");
        wire(document, handlerHighDriver, "Y", data, "BUS");

        controlWire(document, controls, Lf8ControlSignal.PC_INCREMENT, pc, "ENABLE");
        controlWire(document, controls, Lf8ControlSignal.IR_LOAD, ir, "LOAD");
        controlWire(document, controls, Lf8ControlSignal.DESTINATION_REGISTER_LOAD,
                destination, "LOAD");
        controlWire(document, controls, Lf8ControlSignal.SOURCE_REGISTER_LOAD, source, "LOAD");
        controlWire(document, controls, Lf8ControlSignal.MAR_LOW_LOAD, marLow, "LOAD");
        controlWire(document, controls, Lf8ControlSignal.MAR_HIGH_LOAD, marHigh, "LOAD");
        controlWire(document, controls, Lf8ControlSignal.REGISTER_FILE_WRITE, registers, "WR_EN");
        controlWire(document, controls, Lf8ControlSignal.ALU_SOURCE, immediateOrAlu, "SEL");
        controlWire(document, controls, Lf8ControlSignal.MOV_SOURCE, memoryOrMove, "SEL");
        controlWire(document, controls, Lf8ControlSignal.ALTERNATE_SOURCE, writeSource, "SEL");
        controlWire(document, controls, Lf8ControlSignal.ALU_CARRY_IN, alu, "CIN");
        controlWire(document, controls, Lf8ControlSignal.ALU_B_ONE, aluB, "SEL");
        controlWire(document, controls, Lf8ControlSignal.SOURCE_TO_DATA, writeDriver, "ENABLE");
        controlWire(document, controls, Lf8ControlSignal.PC_LOAD, pc, "LOAD");
        controlWire(document, controls, Lf8ControlSignal.ADDRESS_FROM_MAR, addressSource, "SEL");
        controlWire(document, controls, Lf8ControlSignal.FLAGS_LOAD, flags, "LOAD");
        controlWire(document, controls, Lf8ControlSignal.FLAGS_PRESERVE_CARRY,
                carryInput, "SEL");
        controlWire(document, controls, Lf8ControlSignal.FLAGS_PRESERVE_OVERFLOW,
                overflowInput, "SEL");
        controlWire(document, controls, Lf8ControlSignal.ADDRESS_FROM_SP, addressWithSp, "SEL");
        controlWire(document, controls, Lf8ControlSignal.SP_INCREMENT, sp, "ENABLE");
        controlWire(document, controls, Lf8ControlSignal.SP_DECREMENT, sp, "LOAD");
        controlWire(document, controls, Lf8ControlSignal.PC_LOW_TO_DATA, pcLowDriver, "ENABLE");
        controlWire(document, controls, Lf8ControlSignal.PC_HIGH_TO_DATA, pcHighDriver, "ENABLE");
        controlWire(document, controls, Lf8ControlSignal.FLAGS_TO_DATA, flagsDriver, "ENABLE");
        controlWire(document, controls, Lf8ControlSignal.FLAGS_FROM_DATA, flagsSource, "SEL");
        controlWire(document, controls, Lf8ControlSignal.VECTOR_LOW_TO_DATA, handlerLowDriver, "ENABLE");
        controlWire(document, controls, Lf8ControlSignal.VECTOR_HIGH_TO_DATA, handlerHighDriver, "ENABLE");
        return document;
    }

    private static CircuitDocument createControl(ComponentRegistry registry) {
        CircuitDocument document = document(CONTROL_CIRCUIT, "LF-8 microcoded control unit");
        ComponentInstance clk = input(document, registry, 0, 0, "CLK", 1);
        ComponentInstance reset = input(document, registry, 0, 40, "RESET", 1);
        ComponentInstance irq = input(document, registry, 0, 80, "IRQ", 1);
        ComponentInstance opcode = input(document, registry, 0, 140, "OPCODE", 8);
        ComponentInstance flags = input(document, registry, 0, 200, "FLAGS", 4);

        ComponentInstance microstep = add(document, registry, "sequential.modulo_counter", 250, 0,
                defaults(registry, "sequential.modulo_counter")
                        .with(LibraryParameters.WIDTH, 3)
                        .with(LibraryParameters.MODULUS, Lf8Microcode.MICROSTEPS), "MICROSTEP");
        ComponentInstance microcode = add(document, registry, "memory.rom", 480, 100,
                defaults(registry, "memory.rom")
                        .with(LibraryParameters.ADDRESS_WIDTH, 16)
                        .with(LibraryParameters.WIDTH, Lf8ControlSignal.wordWidth())
                        .with(LibraryParameters.ROM_CONTENTS, Lf8Microcode.contents()), "MICROCODE_ROM");
        ComponentInstance microcodeEnable = constant(document, registry, 480, 160, 1, 1,
                "MICROCODE_ROM_EN");
        ComponentInstance microcodeBits = add(document, registry, "routing.splitter", 590, 140,
                defaults(registry, "routing.splitter")
                        .with(LibraryParameters.WIDTH, Lf8ControlSignal.wordWidth()),
                "MICROCODE_BITS");
        ComponentInstance flagsWithIrq = add(document, registry, "routing.bus_concat", 300, 220,
                defaults(registry, "routing.bus_concat")
                        .with(LibraryParameters.LOW_WIDTH, 4)
                        .with(LibraryParameters.HIGH_WIDTH, 1), "FLAGS_WITH_IRQ");
        ComponentInstance stepAndFlags = add(document, registry, "routing.bus_concat", 350, 190,
                defaults(registry, "routing.bus_concat")
                        .with(LibraryParameters.LOW_WIDTH, 3)
                        .with(LibraryParameters.HIGH_WIDTH, 5), "STEP_AND_FLAGS");
        ComponentInstance microcodeAddress = add(document, registry, "routing.bus_concat", 400, 240,
                defaults(registry, "routing.bus_concat")
                        .with(LibraryParameters.LOW_WIDTH, 8)
                        .with(LibraryParameters.HIGH_WIDTH, 8), "MICROCODE_ADDRESS");
        ComponentInstance haltLatch = add(document, registry, "sequential.register_reset", 690, 20,
                defaults(registry, "sequential.register_reset").with(LibraryParameters.WIDTH, 1),
                "HALT_LATCH");
        ComponentInstance haltOr = add(document, registry, "logic.or", 620, 20,
                ParameterValues.empty(), "HALT_OR");
        ComponentInstance haltLoad = constant(document, registry, 690, 70, 1, 1, "HALT_LOAD_TIE");
        ComponentInstance haltNot = add(document, registry, "logic.not", 790, 20,
                ParameterValues.empty(), "HALT_NOT");
        ComponentInstance irqProbe = add(document, registry, "output.probe", 150, 80,
                ParameterValues.empty(), "IRQ_PROBE");

        // Interrupt-enable: a real circuit register, not Java state. Resets to 0 (disabled)
        // at power-on; set/cleared by EI/DI now, and later also by the interrupt entry
        // sequence (clears it) and IRET (unconditionally re-enables it).
        ComponentInstance ieRegister = add(document, registry, "sequential.register_reset",
                60, 260, defaults(registry, "sequential.register_reset")
                        .with(LibraryParameters.WIDTH, 1), "IE_REGISTER");

        // IRQ_TAKEN latches "IRQ is asserted and interrupts are enabled" once per
        // instruction boundary and holds that value for the whole instruction/entry
        // sequence regardless of what IE or IRQ do mid-sequence - otherwise IE being
        // cleared partway through the entry sequence would make this combinational AND
        // flip mid-sequence and corrupt its own ROM addressing. It is explicitly
        // force-cleared by IRQ_ACK, asserted at the end of the entry sequence once the
        // handler's own fetch is about to begin.
        //
        // The sample point is deliberately the LAST microstep of an instruction (count ==
        // MICROSTEPS - 1), not the first. IRQ_TAKEN and MICROSTEP are both clocked by the
        // same CLK edge; a flip-flop's next state depends only on its D input as settled
        // *before* that edge, so if IS_LAST_STEP were instead "count == 0", the counter and
        // the latch would wrap 7->0 on the very same edge that IS_LAST_STEP is trying to
        // observe count == 0, and the latch would always see the pre-edge (non-zero, non-
        // fetch) count - i.e. it could only ever fire one full instruction late, walking
        // into the entry sequence's ROM row for microstep 1 instead of microstep 0 and
        // silently skipping the PC_HIGH push. Sampling on count == MICROSTEPS - 1 instead
        // means IS_LAST_STEP is already settled to 1 for that entire microstep's window, so
        // IRQ_TAKEN and MICROSTEP wrap together on the same edge: MICROSTEP becomes 0 at
        // exactly the edge IRQ_TAKEN becomes 1, landing squarely on the entry sequence's own
        // step 0 - the fetch that would have happened is the one being replaced, and the
        // pushed PC is the interrupted instruction's own address.
        ComponentInstance microstepBits = add(document, registry, "routing.splitter", 250, 40,
                defaults(registry, "routing.splitter").with(LibraryParameters.WIDTH, 3),
                "MICROSTEP_BITS");
        ComponentInstance isFetchStep = add(document, registry, "logic.and", 320, 40,
                defaults(registry, "logic.and").with(LibraryParameters.INPUT_COUNT, 3),
                "IS_LAST_STEP");
        ComponentInstance irqLiveSample = add(document, registry, "logic.and", 60, 320,
                ParameterValues.empty(), "IRQ_LIVE_SAMPLE");
        ComponentInstance irqTakenNot = add(document, registry, "logic.not", 60, 360,
                ParameterValues.empty(), "IRQ_TAKEN_NOT");
        ComponentInstance irqAutoLoad = add(document, registry, "logic.and", 130, 360,
                ParameterValues.empty(), "IRQ_AUTO_LOAD");
        ComponentInstance irqLatchLoad = add(document, registry, "logic.or", 190, 360,
                ParameterValues.empty(), "IRQ_LATCH_LOAD");
        ComponentInstance irqAckClearZero = constant(document, registry, 60, 400, 1, 0,
                "IRQ_ACK_CLEAR_ZERO");
        ComponentInstance irqLatchData = mux(document, registry, 130, 400, 1, "IRQ_LATCH_DATA");
        ComponentInstance irqTakenLatch = add(document, registry, "sequential.register_reset",
                190, 400, defaults(registry, "sequential.register_reset")
                        .with(LibraryParameters.WIDTH, 1), "IRQ_TAKEN_LATCH");

        wire(document, clk, "OUT", microstep, "CLK");
        wire(document, clk, "OUT", haltLatch, "CLK");
        wire(document, clk, "OUT", ieRegister, "CLK");
        wire(document, clk, "OUT", irqTakenLatch, "CLK");
        wire(document, reset, "OUT", microstep, "RESET");
        wire(document, reset, "OUT", haltLatch, "RESET");
        wire(document, reset, "OUT", ieRegister, "RESET");
        wire(document, reset, "OUT", irqTakenLatch, "RESET");
        wire(document, irq, "OUT", irqProbe, "IN");

        wire(document, microstep, "COUNT", microstepBits, "BUS");
        wire(document, microstepBits, "BIT0", isFetchStep, "IN0");
        wire(document, microstepBits, "BIT1", isFetchStep, "IN1");
        wire(document, microstepBits, "BIT2", isFetchStep, "IN2");
        wire(document, irq, "OUT", irqLiveSample, "IN0");
        wire(document, ieRegister, "Q", irqLiveSample, "IN1");
        wire(document, irqTakenLatch, "Q", irqTakenNot, "A");
        wire(document, isFetchStep, "OUT", irqAutoLoad, "IN0");
        wire(document, irqTakenNot, "Y", irqAutoLoad, "IN1");
        wire(document, irqAutoLoad, "OUT", irqLatchLoad, "IN0");
        control(document, microcodeBits, Lf8ControlSignal.IRQ_ACK, irqLatchLoad, "IN1");
        wire(document, irqLiveSample, "OUT", irqLatchData, "IN0");
        wire(document, irqAckClearZero, "OUT", irqLatchData, "IN1");
        control(document, microcodeBits, Lf8ControlSignal.IRQ_ACK, irqLatchData, "SEL");
        wire(document, irqLatchData, "OUT", irqTakenLatch, "DATA");
        wire(document, irqLatchLoad, "OUT", irqTakenLatch, "LOAD");
        control(document, microcodeBits, Lf8ControlSignal.IE_LOAD, ieRegister, "LOAD");
        control(document, microcodeBits, Lf8ControlSignal.IE_DATA, ieRegister, "DATA");

        wire(document, microstep, "COUNT", stepAndFlags, "LOW");
        wire(document, flags, "OUT", flagsWithIrq, "LOW");
        wire(document, irqTakenLatch, "Q", flagsWithIrq, "HIGH");
        wire(document, flagsWithIrq, "OUT", stepAndFlags, "HIGH");
        wire(document, stepAndFlags, "OUT", microcodeAddress, "LOW");
        wire(document, opcode, "OUT", microcodeAddress, "HIGH");
        wire(document, microcodeAddress, "OUT", microcode, "ADDRESS");
        wire(document, microcodeEnable, "OUT", microcode, "ENABLE");
        wire(document, microcode, "DATA", microcodeBits, "BUS");
        control(document, microcodeBits, Lf8ControlSignal.HALT, haltOr, "IN0");
        wire(document, haltLatch, "Q", haltOr, "IN1");
        wire(document, haltOr, "OUT", haltLatch, "DATA");
        wire(document, haltLoad, "OUT", haltLatch, "LOAD");
        wire(document, haltLatch, "Q", haltNot, "A");
        wire(document, haltNot, "Y", microstep, "ENABLE");

        int outputY = 100;
        for (Lf8ControlSignal signal : Lf8ControlSignal.values()) {
            ComponentInstance signalOut = output(document, registry, 900, outputY, signal.name(), 1);
            if (signal == Lf8ControlSignal.HALT) {
                wire(document, haltLatch, "Q", signalOut, "IN");
            } else {
                control(document, microcodeBits, signal, signalOut, "IN");
            }
            outputY += 35;
        }
        ComponentInstance aluOpOut = output(document, registry, 900, outputY, "ALU_OP", 4);
        ComponentInstance aluOpJoiner = add(document, registry, "routing.joiner", 760, outputY,
                defaults(registry, "routing.joiner")
                        .with(LibraryParameters.WIDTH, Lf8ControlField.ALU_OP.width()),
                "ALU_OP_JOINER");
        for (int bit = 0; bit < Lf8ControlField.ALU_OP.width(); bit++) {
            wire(document, microcodeBits,
                    "BIT" + (Lf8ControlField.ALU_OP.lsb() + bit), aluOpJoiner, "BIT" + bit);
        }
        wire(document, aluOpJoiner, "BUS", aluOpOut, "IN");
        return document;
    }

    private static CircuitDocument createCpu(ComponentRegistry registry) {
        CircuitDocument document = document(CPU_CIRCUIT, "LF-8 CPU");
        ComponentInstance clk = input(document, registry, 0, 0, "CLK", 1);
        ComponentInstance reset = input(document, registry, 0, 40, "RESET", 1);
        ComponentInstance irq = input(document, registry, 0, 80, "IRQ", 1);
        ComponentInstance data = inout(document, registry, 800, 80, "DATA", 8);
        ComponentInstance address = output(document, registry, 800, 160, "ADDRESS", 16);
        ComponentInstance memoryRead = output(document, registry, 800, 220, "MEMORY_READ", 1);
        ComponentInstance memoryWrite = output(document, registry, 800, 260, "MEMORY_WRITE", 1);
        ComponentInstance halt = output(document, registry, 800, 300, "HALT", 1);

        ComponentInstance datapath = subcircuit(document, DATAPATH_CIRCUIT, 250, 80, "DATAPATH");
        ComponentInstance control = subcircuit(document, CONTROL_CIRCUIT, 530, 80, "CONTROL");
        wire(document, clk, "OUT", datapath, "CLK");
        wire(document, clk, "OUT", control, "CLK");
        wire(document, reset, "OUT", datapath, "RESET");
        wire(document, reset, "OUT", control, "RESET");
        wire(document, irq, "OUT", control, "IRQ");
        wire(document, data, "BUS", datapath, "DATA");
        wire(document, datapath, "ADDRESS", address, "IN");
        wire(document, datapath, "OPCODE", control, "OPCODE");
        wire(document, datapath, "FLAGS", control, "FLAGS");
        for (Lf8ControlSignal signal : DATAPATH_CONTROLS) {
            wire(document, control, signal.name(), datapath, signal.name());
        }
        wire(document, control, "ALU_OP", datapath, "ALU_OP");
        wire(document, control, Lf8ControlSignal.MEMORY_READ.name(), memoryRead, "IN");
        wire(document, control, Lf8ControlSignal.MEMORY_WRITE.name(), memoryWrite, "IN");
        wire(document, control, Lf8ControlSignal.HALT.name(), halt, "IN");
        return document;
    }

    private static CircuitDocument createMain(ComponentRegistry registry, int[] program) {
        CircuitDocument document = document(CircuitProject.MAIN_CIRCUIT, "LF-8 computer");
        ComponentInstance clk = add(document, registry, "source.toggle", 0, 0,
                ParameterValues.empty(), "CLK");
        ComponentInstance reset = add(document, registry, "source.toggle", 0, 40,
                ParameterValues.empty(), "RESET");
        ComponentInstance irq = add(document, registry, "source.toggle", 0, 80,
                ParameterValues.empty(), "IRQ");
        ComponentInstance cpu = subcircuit(document, CPU_CIRCUIT, 220, 60, "CPU");
        ComponentInstance rom = add(document, registry, "memory.rom", 680, 0,
                defaults(registry, "memory.rom")
                        .with(LibraryParameters.ADDRESS_WIDTH, 15)
                        .with(LibraryParameters.WIDTH, 8)
                        .with(LibraryParameters.ROM_CONTENTS, programContents(program)), "PROG_ROM");
        ComponentInstance ram = add(document, registry, "memory.ram", 680, 160,
                defaults(registry, "memory.ram")
                        .with(LibraryParameters.ADDRESS_WIDTH, 14)
                        .with(LibraryParameters.WIDTH, 8), "RAM");
        ComponentInstance romDecoder = decoder(document, registry, 420, 0, "0000", "8000",
                "ROM_SELECT");
        ComponentInstance ramDecoder = decoder(document, registry, 420, 160, "8000", "c000",
                "RAM_SELECT");
        ComponentInstance romAddress = slice(document, registry, 520, 20, 16, 15, "ROM_ADDRESS");
        ComponentInstance ramAddress = slice(document, registry, 520, 180, 16, 14, "RAM_ADDRESS");
        ComponentInstance romEnable = add(document, registry, "logic.and", 580, 70,
                ParameterValues.empty(), "ROM_ENABLE");
        ComponentInstance haltProbe = add(document, registry, "output.probe", 420, 300,
                ParameterValues.empty(), "HALT_PROBE");

        wire(document, clk, "OUT", cpu, "CLK");
        wire(document, reset, "OUT", cpu, "RESET");
        wire(document, irq, "OUT", cpu, "IRQ");
        wire(document, cpu, "ADDRESS", romDecoder, "ADDRESS");
        wire(document, cpu, "ADDRESS", ramDecoder, "ADDRESS");
        wire(document, cpu, "ADDRESS", romAddress, "IN");
        wire(document, cpu, "ADDRESS", ramAddress, "IN");
        wire(document, romAddress, "OUT", rom, "ADDRESS");
        wire(document, ramAddress, "OUT", ram, "ADDRESS");
        wire(document, romDecoder, "SELECT", romEnable, "IN0");
        wire(document, cpu, "MEMORY_READ", romEnable, "IN1");
        wire(document, romEnable, "OUT", rom, "ENABLE");
        wire(document, ramDecoder, "SELECT", ram, "CS");
        wire(document, cpu, "MEMORY_READ", ram, "OE");
        wire(document, cpu, "MEMORY_WRITE", ram, "WE");
        wire(document, cpu, "DATA", rom, "DATA");
        wire(document, cpu, "DATA", ram, "DATA");
        wire(document, cpu, "HALT", haltProbe, "IN");
        return document;
    }

    private static CircuitDocument document(String name, String description) {
        return new CircuitDocument(new CircuitMetadata(name, description));
    }

    private static ComponentInstance register(CircuitDocument document, ComponentRegistry registry,
                                              double x, double y, int width, String label) {
        return add(document, registry, "sequential.register", x, y,
                defaults(registry, "sequential.register").with(LibraryParameters.WIDTH, width), label);
    }

    private static ComponentInstance mux(CircuitDocument document, ComponentRegistry registry,
                                         double x, double y, int width, String label) {
        return add(document, registry, "routing.mux2", x, y,
                defaults(registry, "routing.mux2").with(LibraryParameters.WIDTH, width), label);
    }

    private static ComponentInstance slice(CircuitDocument document, ComponentRegistry registry,
                                           double x, double y, int inputWidth, int outputWidth,
                                           String label) {
        return add(document, registry, "routing.bus_slice", x, y,
                defaults(registry, "routing.bus_slice")
                        .with(LibraryParameters.INPUT_WIDTH, inputWidth)
                        .with(LibraryParameters.OUTPUT_WIDTH, outputWidth)
                        .with(LibraryParameters.SLICE_LSB, 0), label);
    }

    private static ComponentInstance bitSlice(CircuitDocument document, ComponentRegistry registry,
                                              double x, double y, int inputWidth, int lsb,
                                              String label) {
        return add(document, registry, "routing.bus_slice", x, y,
                defaults(registry, "routing.bus_slice")
                        .with(LibraryParameters.INPUT_WIDTH, inputWidth)
                        .with(LibraryParameters.OUTPUT_WIDTH, 1)
                        .with(LibraryParameters.SLICE_LSB, lsb), label);
    }

    private static ComponentInstance decoder(CircuitDocument document, ComponentRegistry registry,
                                             double x, double y, String base, String mask,
                                             String label) {
        return add(document, registry, "routing.address_decoder", x, y,
                defaults(registry, "routing.address_decoder")
                        .with(LibraryParameters.ADDRESS_WIDTH, 16)
                        .with(LibraryParameters.ADDRESS_BASE, base)
                        .with(LibraryParameters.ADDRESS_MASK, mask), label);
    }

    private static ComponentInstance input(CircuitDocument document, ComponentRegistry registry,
                                           double x, double y, String name, int width) {
        return interfaceComponent(document, registry, SubcircuitSupport.INPUT_DEFINITION_ID,
                x, y, name, width);
    }

    private static ComponentInstance output(CircuitDocument document, ComponentRegistry registry,
                                            double x, double y, String name, int width) {
        return interfaceComponent(document, registry, SubcircuitSupport.OUTPUT_DEFINITION_ID,
                x, y, name, width);
    }

    private static ComponentInstance inout(CircuitDocument document, ComponentRegistry registry,
                                           double x, double y, String name, int width) {
        return interfaceComponent(document, registry, SubcircuitSupport.INOUT_DEFINITION_ID,
                x, y, name, width);
    }

    private static ComponentInstance interfaceComponent(CircuitDocument document,
                                                        ComponentRegistry registry,
                                                        String definitionId, double x, double y,
                                                        String name, int width) {
        ParameterValues parameters = ParameterValues.defaultsOf(List.of(
                        SubcircuitSupport.INTERFACE_NAME, SubcircuitSupport.INTERFACE_WIDTH))
                .with(SubcircuitSupport.INTERFACE_NAME, name)
                .with(SubcircuitSupport.INTERFACE_WIDTH, width);
        return add(document, registry, definitionId, x, y, parameters, name + "_PORT");
    }

    private static ComponentInstance subcircuit(CircuitDocument document, String circuitName,
                                                double x, double y, String label) {
        ComponentInstance instance = SubcircuitSupport.instantiate(circuitName, new CircuitPoint(x, y))
                .withLabel(label);
        document.addComponent(instance);
        return instance;
    }

    private static ParameterValues defaults(ComponentRegistry registry, String definitionId) {
        return registry.require(definitionId).definition().defaultParameters();
    }

    private static ComponentInstance add(CircuitDocument document, ComponentRegistry registry,
                                         String definitionId, double x, double y,
                                         ParameterValues parameters, String label) {
        registry.require(definitionId);
        ComponentInstance instance = ComponentInstance.create(
                definitionId, new CircuitPoint(x, y), parameters).withLabel(label);
        document.addComponent(instance);
        return instance;
    }

    private static ComponentInstance constant(CircuitDocument document, ComponentRegistry registry,
                                              double x, double y, int width, long value, String label) {
        return add(document, registry, "routing.bus_constant", x, y,
                defaults(registry, "routing.bus_constant")
                        .with(LibraryParameters.WIDTH, width)
                        .with(LibraryParameters.BUS_CONSTANT_VALUE, Long.toHexString(value)), label);
    }

    private static void wire(CircuitDocument document, ComponentInstance from, String fromPort,
                             ComponentInstance to, String toPort) {
        document.addConnection(Connection.create(new PortReference(from.id(), fromPort),
                new PortReference(to.id(), toPort)));
    }

    private static void control(CircuitDocument document, ComponentInstance splitter,
                                Lf8ControlSignal signal, ComponentInstance target, String targetPort) {
        wire(document, splitter, "BIT" + signal.bit(), target, targetPort);
    }

    private static void controlWire(CircuitDocument document,
                                    Map<Lf8ControlSignal, ComponentInstance> controls,
                                    Lf8ControlSignal signal,
                                    ComponentInstance target, String targetPort) {
        wire(document, controls.get(signal), "OUT", target, targetPort);
    }

    private static String programContents(int[] program) {
        if (program.length > (1 << 15)) {
            throw new IllegalArgumentException("Program exceeds the 32 KiB ROM region");
        }
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < program.length; i++) {
            if (program[i] < 0 || program[i] > 0xff) {
                throw new IllegalArgumentException("Program byte " + i + " is outside 0..255");
            }
            if (i > 0) {
                csv.append(',');
            }
            csv.append(Integer.toHexString(program[i]));
        }
        return csv.toString();
    }
}
