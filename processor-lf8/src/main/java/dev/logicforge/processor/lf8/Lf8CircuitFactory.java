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
import dev.logicforge.structures.StructuralCircuitFactory;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Builds the structural LF-8 computer from ordinary LogicForge components. */
public final class Lf8CircuitFactory {

    public static final String CPU_CIRCUIT = "LF8_CPU";
    public static final String DATAPATH_CIRCUIT = "LF8_DATAPATH";
    public static final String CONTROL_CIRCUIT = "LF8_CONTROL";
    public static final String STRUCTURAL_ALU_ADAPTER_CIRCUIT = "LF8_STRUCTURAL_ALU";

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
            Lf8ControlSignal.IE_LOAD,
            Lf8ControlSignal.IE_DATA,
            Lf8ControlSignal.STATUS_TO_DATA,
            Lf8ControlSignal.STATUS_FROM_DATA,
            Lf8ControlSignal.ADDRESS_FROM_VECTOR,
            Lf8ControlSignal.VECTOR_HIGH_ADDRESS,
            Lf8ControlSignal.RESET_VECTOR_SELECT);

    private Lf8CircuitFactory() {
    }

    /** Creates the complete four-document LF-8 computer project. */
    public static CircuitProject createProject(int... program) {
        return createProject(program, 0, 0, 0, Lf8ImplementationMode.FAST);
    }

    public static CircuitProject createProject(Lf8ImplementationMode mode, int... program) {
        return createProject(program, 0, 0, 0, mode);
    }

    /** Creates an LF-8 project whose interrupt and startup targets live in external ROM. */
    public static CircuitProject createProject(int[] program, int irqVector, int nmiVector,
                                               int resetVector) {
        return createProject(program, irqVector, nmiVector, resetVector,
                Lf8ImplementationMode.FAST);
    }

    public static CircuitProject createProject(int[] program, int irqVector, int nmiVector,
                                               int resetVector, Lf8ImplementationMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("LF-8 implementation mode cannot be null");
        }
        requireAddress(irqVector, "IRQ vector");
        requireAddress(nmiVector, "NMI vector");
        requireAddress(resetVector, "RESET vector");
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument datapath = createDatapath(registry, mode);
        CircuitDocument control = createControl(registry, mode);
        CircuitDocument cpu = createCpu(registry);
        CircuitDocument main = createMain(registry, program, irqVector, nmiVector, resetVector);
        CircuitProject project = CircuitProject.of("lf8", main);
        project.putCircuit(cpu);
        project.putCircuit(datapath);
        project.putCircuit(control);
        if (mode != Lf8ImplementationMode.FAST) {
            var structuralAlu = StructuralCircuitFactory.alu8Project();
            structuralAlu.circuits().stream()
                    .filter(circuit -> !CircuitProject.MAIN_CIRCUIT.equals(circuit.metadata().name()))
                    .forEach(project::putCircuit);
            var structuralRegisterFile = StructuralCircuitFactory.registerFile8x8Project();
            structuralRegisterFile.circuits().stream()
                    .filter(circuit -> !CircuitProject.MAIN_CIRCUIT.equals(circuit.metadata().name()))
                    .forEach(project::putCircuit);
            var resettableRegisterFile = StructuralCircuitFactory.resettableRegisterFile8x8Project();
            resettableRegisterFile.circuits().stream()
                    .filter(circuit -> !CircuitProject.MAIN_CIRCUIT.equals(circuit.metadata().name()))
                    .forEach(project::putCircuit);
            project.putCircuit(createStructuralAluAdapter(registry));
        }
        if (mode == Lf8ImplementationMode.GATE_LEVEL) {
            addGateLevelStructures(project);
        }
        return project;
    }

    /** Retained for callers that only need the top-level computer schematic. */
    public static CircuitDocument createComputerCircuit(int... program) {
        return createProject(program).mainCircuit();
    }

    private static CircuitDocument createDatapath(ComponentRegistry registry,
                                                  Lf8ImplementationMode mode) {
        CircuitDocument document = document(DATAPATH_CIRCUIT, "LF-8 datapath");
        ComponentInstance clk = input(document, registry, 0, 0, "CLK", 1);
        ComponentInstance reset = input(document, registry, 0, 40, "RESET", 1);
        ComponentInstance data = inout(document, registry, 1000, 100, "DATA", 8);
        ComponentInstance address = output(document, registry, 1000, 180, "ADDRESS", 16);
        ComponentInstance opcode = output(document, registry, 1000, 240, "OPCODE", 8);
        ComponentInstance flagsOut = output(document, registry, 1000, 300, "FLAGS", 4);
        ComponentInstance ieOut = output(document, registry, 1000, 340, "IE", 1);
        ComponentInstance nmiVectorSelect = input(document, registry, 0, 640,
                "NMI_VECTOR_SELECT", 1);
        ComponentInstance aluOp = input(document, registry, 0, 600, "ALU_OP", 4);

        Map<Lf8ControlSignal, ComponentInstance> controls = new EnumMap<>(Lf8ControlSignal.class);
        int controlY = 80;
        for (Lf8ControlSignal signal : DATAPATH_CONTROLS) {
            controls.put(signal, input(document, registry, 0, controlY, signal.name(), 1));
            controlY += 35;
        }

        ComponentInstance pc = counter16(document, registry, mode, 180, 0, 0, "PC");
        ComponentInstance ir = register(document, registry, mode, 260, 60, 8, "IR");
        ComponentInstance destination = register(document, registry, mode, 260, 120, 3, "DESTINATION");
        ComponentInstance source = register(document, registry, mode, 260, 180, 3, "SOURCE");
        ComponentInstance operandSlice = add(document, registry, "routing.bus_slice", 180, 150,
                defaults(registry, "routing.bus_slice")
                        .with(LibraryParameters.INPUT_WIDTH, 8)
                        .with(LibraryParameters.OUTPUT_WIDTH, 3)
                        .with(LibraryParameters.SLICE_LSB, 0), "OPERAND_SLICE");
        ComponentInstance marLow = register(document, registry, mode, 260, 240, 8, "MAR_LO");
        ComponentInstance marHigh = register(document, registry, mode, 260, 300, 8, "MAR_HI");
        ComponentInstance registers = mode == Lf8ImplementationMode.FAST
                ? add(document, registry, "memory.register_file", 430, 110,
                        defaults(registry, "memory.register_file")
                                .with(LibraryParameters.WIDTH, 8)
                                .with(LibraryParameters.REGISTER_COUNT, 8), "REGISTER_FILE")
                : subcircuit(document, StructuralCircuitFactory.REGISTER_FILE_8X8_RESET,
                        430, 110, "REGISTER_FILE");
        ComponentInstance alu = mode == Lf8ImplementationMode.FAST
                ? add(document, registry, "arithmetic.alu", 570, 110,
                        defaults(registry, "arithmetic.alu").with(LibraryParameters.WIDTH, 8), "ALU")
                : subcircuit(document, STRUCTURAL_ALU_ADAPTER_CIRCUIT, 570, 110, "ALU");
        ComponentInstance aluB = mux(document, registry, mode, 520, 170, 8, "ALU_B_SOURCE");
        ComponentInstance one = constant(document, registry, 430, 210, 8, 1, "ALU_ONE");

        ComponentInstance immediateOrAlu = mux(document, registry, mode, 680, 60, 8,
                "WRITE_IMMEDIATE_ALU");
        ComponentInstance memoryOrMove = mux(document, registry, mode, 680, 130, 8,
                "WRITE_MEMORY_MOVE");
        ComponentInstance writeSource = mux(document, registry, mode, 790, 95, 8, "WRITE_SOURCE");
        ComponentInstance addressConcat = add(document, registry, "routing.bus_concat", 480, 270,
                defaults(registry, "routing.bus_concat")
                        .with(LibraryParameters.LOW_WIDTH, 8)
                        .with(LibraryParameters.HIGH_WIDTH, 8), "MAR_ADDRESS");
        ComponentInstance addressSource = mux(document, registry, mode, 690, 270, 16, "ADDRESS_SOURCE");
        ComponentInstance sp = counter16(document, registry, mode, 480, 340, 0xBFFF, "SP");
        ComponentInstance spDecrementer = mode == Lf8ImplementationMode.GATE_LEVEL
                ? subcircuit(document, StructuralCircuitFactory.DECREMENTER16,
                        400, 340, "SP_DECREMENTER")
                : add(document, registry, "arithmetic.decrementer", 400, 340,
                        defaults(registry, "arithmetic.decrementer")
                                .with(LibraryParameters.WIDTH, 16), "SP_DECREMENTER");
        ComponentInstance addressWithSp = mux(document, registry, mode, 780, 300, 16, "ADDRESS_WITH_SP");
        ComponentInstance irqVectorLow = constant(document, registry, 570, 470, 16,
                Lf8MemoryMap.IRQ_VECTOR, "IRQ_VECTOR_LOW_ADDRESS");
        ComponentInstance irqVectorHigh = constant(document, registry, 570, 510, 16,
                Lf8MemoryMap.IRQ_VECTOR + 1, "IRQ_VECTOR_HIGH_ADDRESS");
        ComponentInstance resetVectorLow = constant(document, registry, 570, 550, 16,
                Lf8MemoryMap.RESET_VECTOR, "RESET_VECTOR_LOW_ADDRESS");
        ComponentInstance resetVectorHigh = constant(document, registry, 570, 590, 16,
                Lf8MemoryMap.RESET_VECTOR + 1, "RESET_VECTOR_HIGH_ADDRESS");
        ComponentInstance nmiVectorLow = constant(document, registry, 460, 550, 16,
                Lf8MemoryMap.NMI_VECTOR, "NMI_VECTOR_LOW_ADDRESS");
        ComponentInstance nmiVectorHigh = constant(document, registry, 460, 590, 16,
                Lf8MemoryMap.NMI_VECTOR + 1, "NMI_VECTOR_HIGH_ADDRESS");
        ComponentInstance irqVectorAddress = mux(document, registry, mode, 680, 490, 16,
                "IRQ_VECTOR_BYTE_ADDRESS");
        ComponentInstance nmiVectorAddress = mux(document, registry, mode, 570, 650, 16,
                "NMI_VECTOR_BYTE_ADDRESS");
        ComponentInstance interruptVectorAddress = mux(document, registry, mode, 680, 650, 16,
                "INTERRUPT_VECTOR_ADDRESS");
        ComponentInstance resetVectorAddress = mux(document, registry, mode, 680, 570, 16,
                "RESET_VECTOR_BYTE_ADDRESS");
        ComponentInstance selectedVectorAddress = mux(document, registry, mode, 790, 530, 16,
                "SELECTED_VECTOR_ADDRESS");
        ComponentInstance finalAddress = mux(document, registry, mode, 900, 360, 16,
                "FINAL_ADDRESS_SOURCE");
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
        ComponentInstance flags = resetRegister(document, registry, mode, 820, 330, 4,
                "FLAGS_REGISTER");
        ComponentInstance ie = resetRegister(document, registry, mode, 820, 420, 1,
                "IE_REGISTER");
        role(document, pc, Lf8ComponentRoles.DATAPATH_PC);
        role(document, sp, Lf8ComponentRoles.DATAPATH_SP);
        role(document, ir, Lf8ComponentRoles.DATAPATH_IR);
        role(document, registers, Lf8ComponentRoles.DATAPATH_REGISTER_FILE);
        role(document, flags, Lf8ComponentRoles.DATAPATH_FLAGS);
        role(document, ie, Lf8ComponentRoles.DATAPATH_IE);
        ComponentInstance storedCarry = bitSlice(document, registry, 650, 350, 4, 1,
                "STORED_CARRY");
        ComponentInstance storedOverflow = bitSlice(document, registry, 650, 400, 4, 3,
                "STORED_OVERFLOW");
        ComponentInstance carryInput = mux(document, registry, mode, 730, 350, 1, "CARRY_INPUT");
        ComponentInstance overflowInput = mux(document, registry, mode, 730, 400, 1, "OVERFLOW_INPUT");

        // FLAGS_REGISTER's next value is either the live ALU-derived nibble (as before) or,
        // for IRET, a popped stack byte's low nibble. Z/C/N/V are joined into one 4-bit bus,
        // muxed against the popped byte, then split back out to the register's four separate
        // 1-bit inputs.
        ComponentInstance liveFlagsJoiner = add(document, registry, "routing.joiner", 760, 350,
                defaults(registry, "routing.joiner").with(LibraryParameters.WIDTH, 4),
                "LIVE_FLAGS_JOINER");
        ComponentInstance poppedFlags = slice(document, registry, 760, 450, 8, 4, "POPPED_FLAGS");
        ComponentInstance flagsSource = mux(document, registry, mode, 790, 400, 4, "FLAGS_SOURCE");
        ComponentInstance poppedIe = bitSlice(document, registry, 760, 490, 8, 4, "POPPED_IE");
        ComponentInstance ieSource = mux(document, registry, mode, 790, 460, 1, "IE_SOURCE");

        // STATUS is an explicit architectural byte: Z/C/N/V in bits 0..3, IE in bit 4,
        // and three reserved zero bits. Interrupt entry pushes it and IRET restores it.
        ComponentInstance statusLow = add(document, registry, "routing.bus_concat", 850, 500,
                defaults(registry, "routing.bus_concat")
                        .with(LibraryParameters.LOW_WIDTH, 4)
                        .with(LibraryParameters.HIGH_WIDTH, 1), "STATUS_LOW_BITS");
        ComponentInstance statusZeroPad = constant(document, registry, 850, 540, 3, 0,
                "STATUS_RESERVED_ZERO");
        ComponentInstance statusByte = add(document, registry, "routing.bus_concat", 900, 520,
                defaults(registry, "routing.bus_concat")
                        .with(LibraryParameters.LOW_WIDTH, 5)
                        .with(LibraryParameters.HIGH_WIDTH, 3), "STATUS_BYTE");
        ComponentInstance statusDriver = add(document, registry, "routing.tristate_n", 950, 520,
                defaults(registry, "routing.tristate_n").with(LibraryParameters.WIDTH, 8),
                "STATUS_DRIVER");

        for (ComponentInstance target : List.of(pc, ir, destination, source, marLow, marHigh, registers, sp)) {
            wire(document, clk, "OUT", target, "CLK");
        }
        wire(document, clk, "OUT", flags, "CLK");
        wire(document, clk, "OUT", ie, "CLK");
        wire(document, reset, "OUT", pc, "RESET");
        wire(document, reset, "OUT", sp, "RESET");
        wire(document, reset, "OUT", flags, "RESET");
        wire(document, reset, "OUT", ie, "RESET");
        if (mode == Lf8ImplementationMode.GATE_LEVEL) {
            for (ComponentInstance target : List.of(ir, destination, source, marLow, marHigh,
                    registers)) {
                wire(document, reset, "OUT", target, "RESET");
            }
        } else if (mode == Lf8ImplementationMode.STRUCTURAL) {
            wire(document, reset, "OUT", registers, "RESET");
        }

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
        wire(document, irqVectorLow, "OUT", irqVectorAddress, "IN0");
        wire(document, irqVectorHigh, "OUT", irqVectorAddress, "IN1");
        wire(document, nmiVectorLow, "OUT", nmiVectorAddress, "IN0");
        wire(document, nmiVectorHigh, "OUT", nmiVectorAddress, "IN1");
        wire(document, resetVectorLow, "OUT", resetVectorAddress, "IN0");
        wire(document, resetVectorHigh, "OUT", resetVectorAddress, "IN1");
        wire(document, irqVectorAddress, "OUT", interruptVectorAddress, "IN0");
        wire(document, nmiVectorAddress, "OUT", interruptVectorAddress, "IN1");
        wire(document, nmiVectorSelect, "OUT", interruptVectorAddress, "SEL");
        wire(document, interruptVectorAddress, "OUT", selectedVectorAddress, "IN0");
        wire(document, resetVectorAddress, "OUT", selectedVectorAddress, "IN1");
        wire(document, addressWithSp, "OUT", finalAddress, "IN0");
        wire(document, selectedVectorAddress, "OUT", finalAddress, "IN1");
        wire(document, finalAddress, "OUT", address, "IN");
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
        wire(document, flags, "Q", storedCarry, "IN");
        wire(document, flags, "Q", storedOverflow, "IN");
        wire(document, storedCarry, "OUT", carryInput, "IN1");
        wire(document, storedOverflow, "OUT", overflowInput, "IN1");
        wire(document, flags, "Q", flagsOut, "IN");
        wire(document, ie, "Q", ieOut, "IN");

        wire(document, alu, "ZERO", liveFlagsJoiner, "BIT0");
        wire(document, carryInput, "OUT", liveFlagsJoiner, "BIT1");
        wire(document, alu, "NEGATIVE", liveFlagsJoiner, "BIT2");
        wire(document, overflowInput, "OUT", liveFlagsJoiner, "BIT3");
        wire(document, data, "BUS", poppedFlags, "IN");
        wire(document, liveFlagsJoiner, "BUS", flagsSource, "IN0");
        wire(document, poppedFlags, "OUT", flagsSource, "IN1");
        wire(document, flagsSource, "OUT", flags, "DATA");
        wire(document, data, "BUS", poppedIe, "IN");
        wire(document, controls.get(Lf8ControlSignal.IE_DATA), "OUT", ieSource, "IN0");
        wire(document, poppedIe, "OUT", ieSource, "IN1");
        wire(document, ieSource, "OUT", ie, "DATA");

        wire(document, flags, "Q", statusLow, "LOW");
        wire(document, ie, "Q", statusLow, "HIGH");
        wire(document, statusLow, "OUT", statusByte, "LOW");
        wire(document, statusZeroPad, "OUT", statusByte, "HIGH");
        wire(document, statusByte, "OUT", statusDriver, "A");
        wire(document, statusDriver, "Y", data, "BUS");

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
        controlWire(document, controls, Lf8ControlSignal.VECTOR_HIGH_ADDRESS,
                irqVectorAddress, "SEL");
        controlWire(document, controls, Lf8ControlSignal.VECTOR_HIGH_ADDRESS,
                nmiVectorAddress, "SEL");
        controlWire(document, controls, Lf8ControlSignal.VECTOR_HIGH_ADDRESS,
                resetVectorAddress, "SEL");
        controlWire(document, controls, Lf8ControlSignal.RESET_VECTOR_SELECT,
                selectedVectorAddress, "SEL");
        controlWire(document, controls, Lf8ControlSignal.ADDRESS_FROM_VECTOR,
                finalAddress, "SEL");
        controlWire(document, controls, Lf8ControlSignal.SP_INCREMENT, sp, "ENABLE");
        controlWire(document, controls, Lf8ControlSignal.SP_DECREMENT, sp, "LOAD");
        controlWire(document, controls, Lf8ControlSignal.PC_LOW_TO_DATA, pcLowDriver, "ENABLE");
        controlWire(document, controls, Lf8ControlSignal.PC_HIGH_TO_DATA, pcHighDriver, "ENABLE");
        controlWire(document, controls, Lf8ControlSignal.STATUS_TO_DATA, statusDriver, "ENABLE");
        controlWire(document, controls, Lf8ControlSignal.STATUS_FROM_DATA, flagsSource, "SEL");
        controlWire(document, controls, Lf8ControlSignal.STATUS_FROM_DATA, ieSource, "SEL");
        controlWire(document, controls, Lf8ControlSignal.IE_LOAD, ie, "LOAD");
        return document;
    }

    private static CircuitDocument createStructuralAluAdapter(ComponentRegistry registry) {
        CircuitDocument document = document(STRUCTURAL_ALU_ADAPTER_CIRCUIT,
                "LF-8 port adapter around the canonical gate-level ALU8");
        ComponentInstance a = input(document, registry, 0, 0, "A", 8);
        ComponentInstance b = input(document, registry, 0, 60, "B", 8);
        ComponentInstance op = input(document, registry, 0, 120, "OP", 4);
        ComponentInstance cin = input(document, registry, 0, 180, "CIN", 1);
        ComponentInstance result = output(document, registry, 500, 0, "RESULT", 8);
        ComponentInstance zero = output(document, registry, 500, 50, "ZERO", 1);
        ComponentInstance carry = output(document, registry, 500, 100, "CARRY", 1);
        ComponentInstance overflow = output(document, registry, 500, 150, "OVERFLOW", 1);
        ComponentInstance negative = output(document, registry, 500, 200, "NEGATIVE", 1);
        ComponentInstance opLow = slice(document, registry, 130, 120, 4, 3, "OP_LOW_3");
        ComponentInstance cinProbe = add(document, registry, "output.probe", 130, 180,
                ParameterValues.empty(), "CIN_PROBE");
        ComponentInstance alu = subcircuit(document, StructuralCircuitFactory.ALU8,
                280, 80, "ALU8");
        wire(document, a, "OUT", alu, "A");
        wire(document, b, "OUT", alu, "B");
        wire(document, op, "OUT", opLow, "IN");
        wire(document, opLow, "OUT", alu, "OP");
        wire(document, cin, "OUT", cinProbe, "IN");
        wire(document, alu, "RESULT", result, "IN");
        wire(document, alu, "Z", zero, "IN");
        wire(document, alu, "C", carry, "IN");
        wire(document, alu, "V", overflow, "IN");
        wire(document, alu, "N", negative, "IN");
        return document;
    }

    private static CircuitDocument createControl(ComponentRegistry registry,
                                                 Lf8ImplementationMode mode) {
        CircuitDocument document = document(CONTROL_CIRCUIT, "LF-8 microcoded control unit");
        ComponentInstance clk = input(document, registry, 0, 0, "CLK", 1);
        ComponentInstance reset = input(document, registry, 0, 40, "RESET", 1);
        ComponentInstance irq = input(document, registry, 0, 80, "IRQ", 1);
        ComponentInstance nmi = input(document, registry, 0, 120, "NMI", 1);
        ComponentInstance opcode = input(document, registry, 0, 140, "OPCODE", 8);
        ComponentInstance flags = input(document, registry, 0, 200, "FLAGS", 4);
        ComponentInstance ie = input(document, registry, 0, 240, "IE", 1);

        ComponentInstance microstep = mode == Lf8ImplementationMode.GATE_LEVEL
                ? subcircuit(document, StructuralCircuitFactory.MODULO_COUNTER3,
                        250, 0, "MICROSTEP")
                : add(document, registry, "sequential.modulo_counter", 250, 0,
                        defaults(registry, "sequential.modulo_counter")
                                .with(LibraryParameters.WIDTH, 3)
                                .with(LibraryParameters.MODULUS, Lf8Microcode.MICROSTEPS),
                        "MICROSTEP");
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
        ComponentInstance haltLatch = resetRegister(document, registry, mode, 690, 20, 1,
                "HALT_LATCH");
        ComponentInstance haltOr = add(document, registry, "logic.or", 620, 20,
                ParameterValues.empty(), "HALT_OR");
        ComponentInstance haltLoad = constant(document, registry, 690, 70, 1, 1, "HALT_LOAD_TIE");
        ComponentInstance haltNot = add(document, registry, "logic.not", 790, 20,
                ParameterValues.empty(), "HALT_NOT");
        ComponentInstance irqProbe = add(document, registry, "output.probe", 150, 80,
                ParameterValues.empty(), "IRQ_PROBE");
        role(document, irqProbe, Lf8ComponentRoles.CONTROL_IRQ_INPUT);

        // RESET_COMPLETE asynchronously clears to zero whenever RESET is asserted. Its
        // inverse therefore remains high after RESET is released until the reset-vector
        // microsequence explicitly acknowledges completion on a clock edge.
        ComponentInstance resetComplete = resetRegister(document, registry, mode, 60, 260, 1,
                "RESET_COMPLETE");
        ComponentInstance resetCompleteOne = constant(document, registry, 0, 300, 1, 1,
                "RESET_COMPLETE_ONE");
        ComponentInstance resetPending = add(document, registry, "logic.not", 130, 260,
                ParameterValues.empty(), "RESET_PENDING");
        ComponentInstance resetOpcode = constant(document, registry, 300, 120, 8,
                Lf8Microcode.RESET_PSEUDO_OPCODE, "RESET_PSEUDO_OPCODE");
        ComponentInstance selectedOpcode = mux(document, registry, mode, 390, 120, 8,
                "MICROCODE_OPCODE");

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
        ComponentInstance nmiNot = add(document, registry, "logic.not", 0, 320,
                ParameterValues.empty(), "NMI_NOT");
        ComponentInstance irqLiveSample = add(document, registry, "logic.and", 60, 320,
                defaults(registry, "logic.and").with(LibraryParameters.INPUT_COUNT, 3),
                "IRQ_LIVE_SAMPLE");
        ComponentInstance irqTakenNot = add(document, registry, "logic.not", 60, 360,
                ParameterValues.empty(), "IRQ_TAKEN_NOT");
        ComponentInstance irqAutoLoad = add(document, registry, "logic.and", 130, 360,
                ParameterValues.empty(), "IRQ_AUTO_LOAD");
        ComponentInstance irqLatchLoad = add(document, registry, "logic.or", 190, 360,
                ParameterValues.empty(), "IRQ_LATCH_LOAD");
        ComponentInstance irqAckClearZero = constant(document, registry, 60, 400, 1, 0,
                "IRQ_ACK_CLEAR_ZERO");
        ComponentInstance irqLatchData = mux(document, registry, mode, 130, 400, 1, "IRQ_LATCH_DATA");
        ComponentInstance irqTakenLatch = resetRegister(document, registry, mode, 190, 400, 1,
                "IRQ_TAKEN_LATCH");
        ComponentInstance nmiTakenNot = add(document, registry, "logic.not", 250, 360,
                ParameterValues.empty(), "NMI_TAKEN_NOT");
        ComponentInstance nmiAutoLoad = add(document, registry, "logic.and", 310, 360,
                ParameterValues.empty(), "NMI_AUTO_LOAD");
        ComponentInstance nmiLatchLoad = add(document, registry, "logic.or", 370, 360,
                ParameterValues.empty(), "NMI_LATCH_LOAD");
        ComponentInstance nmiLatchData = mux(document, registry, mode, 310, 400, 1, "NMI_LATCH_DATA");
        ComponentInstance nmiTakenLatch = resetRegister(document, registry, mode, 370, 400, 1,
                "NMI_TAKEN_LATCH");
        role(document, microstep, Lf8ComponentRoles.CONTROL_MICROSTEP);
        role(document, microcode, Lf8ComponentRoles.CONTROL_MICROCODE);
        role(document, haltLatch, Lf8ComponentRoles.CONTROL_HALT);
        role(document, irqTakenLatch, Lf8ComponentRoles.CONTROL_IRQ_TAKEN);
        role(document, nmiTakenLatch, Lf8ComponentRoles.CONTROL_NMI_TAKEN);
        ComponentInstance exceptionTaken = add(document, registry, "logic.or", 250, 440,
                ParameterValues.empty(), "EXCEPTION_TAKEN");

        wire(document, clk, "OUT", microstep, "CLK");
        wire(document, clk, "OUT", haltLatch, "CLK");
        wire(document, clk, "OUT", resetComplete, "CLK");
        wire(document, clk, "OUT", irqTakenLatch, "CLK");
        wire(document, clk, "OUT", nmiTakenLatch, "CLK");
        wire(document, reset, "OUT", microstep, "RESET");
        wire(document, reset, "OUT", haltLatch, "RESET");
        wire(document, reset, "OUT", resetComplete, "RESET");
        wire(document, reset, "OUT", irqTakenLatch, "RESET");
        wire(document, reset, "OUT", nmiTakenLatch, "RESET");
        wire(document, irq, "OUT", irqProbe, "IN");

        wire(document, resetCompleteOne, "OUT", resetComplete, "DATA");
        control(document, microcodeBits, Lf8ControlSignal.RESET_ACK, resetComplete, "LOAD");
        wire(document, resetComplete, "Q", resetPending, "A");
        wire(document, opcode, "OUT", selectedOpcode, "IN0");
        wire(document, resetOpcode, "OUT", selectedOpcode, "IN1");
        wire(document, resetPending, "Y", selectedOpcode, "SEL");

        wire(document, microstep, "COUNT", microstepBits, "BUS");
        wire(document, microstepBits, "BIT0", isFetchStep, "IN0");
        wire(document, microstepBits, "BIT1", isFetchStep, "IN1");
        wire(document, microstepBits, "BIT2", isFetchStep, "IN2");
        wire(document, irq, "OUT", irqLiveSample, "IN0");
        wire(document, ie, "OUT", irqLiveSample, "IN1");
        wire(document, nmi, "OUT", nmiNot, "A");
        wire(document, nmiNot, "Y", irqLiveSample, "IN2");
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
        wire(document, nmiTakenLatch, "Q", nmiTakenNot, "A");
        wire(document, isFetchStep, "OUT", nmiAutoLoad, "IN0");
        wire(document, nmiTakenNot, "Y", nmiAutoLoad, "IN1");
        wire(document, nmiAutoLoad, "OUT", nmiLatchLoad, "IN0");
        control(document, microcodeBits, Lf8ControlSignal.IRQ_ACK, nmiLatchLoad, "IN1");
        wire(document, nmi, "OUT", nmiLatchData, "IN0");
        wire(document, irqAckClearZero, "OUT", nmiLatchData, "IN1");
        control(document, microcodeBits, Lf8ControlSignal.IRQ_ACK, nmiLatchData, "SEL");
        wire(document, nmiLatchData, "OUT", nmiTakenLatch, "DATA");
        wire(document, nmiLatchLoad, "OUT", nmiTakenLatch, "LOAD");
        wire(document, irqTakenLatch, "Q", exceptionTaken, "IN0");
        wire(document, nmiTakenLatch, "Q", exceptionTaken, "IN1");
        wire(document, microstep, "COUNT", stepAndFlags, "LOW");
        wire(document, flags, "OUT", flagsWithIrq, "LOW");
        wire(document, exceptionTaken, "OUT", flagsWithIrq, "HIGH");
        wire(document, flagsWithIrq, "OUT", stepAndFlags, "HIGH");
        wire(document, stepAndFlags, "OUT", microcodeAddress, "LOW");
        wire(document, selectedOpcode, "OUT", microcodeAddress, "HIGH");
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
        ComponentInstance nmiTakenOut = output(document, registry, 900, outputY,
                "NMI_TAKEN", 1);
        wire(document, nmiTakenLatch, "Q", nmiTakenOut, "IN");
        outputY += 35;
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
        ComponentInstance nmi = input(document, registry, 0, 120, "NMI", 1);
        ComponentInstance data = inout(document, registry, 800, 80, "DATA", 8);
        ComponentInstance address = output(document, registry, 800, 160, "ADDRESS", 16);
        ComponentInstance memoryRead = output(document, registry, 800, 220, "MEMORY_READ", 1);
        ComponentInstance memoryWrite = output(document, registry, 800, 260, "MEMORY_WRITE", 1);
        ComponentInstance halt = output(document, registry, 800, 300, "HALT", 1);

        ComponentInstance datapath = subcircuit(document, DATAPATH_CIRCUIT, 250, 80, "DATAPATH");
        ComponentInstance control = subcircuit(document, CONTROL_CIRCUIT, 530, 80, "CONTROL");
        role(document, datapath, Lf8ComponentRoles.CPU_DATAPATH);
        role(document, control, Lf8ComponentRoles.CPU_CONTROL);
        wire(document, clk, "OUT", datapath, "CLK");
        wire(document, clk, "OUT", control, "CLK");
        wire(document, reset, "OUT", datapath, "RESET");
        wire(document, reset, "OUT", control, "RESET");
        wire(document, irq, "OUT", control, "IRQ");
        wire(document, nmi, "OUT", control, "NMI");
        wire(document, data, "BUS", datapath, "DATA");
        wire(document, datapath, "ADDRESS", address, "IN");
        wire(document, datapath, "OPCODE", control, "OPCODE");
        wire(document, datapath, "FLAGS", control, "FLAGS");
        wire(document, datapath, "IE", control, "IE");
        wire(document, control, "NMI_TAKEN", datapath, "NMI_VECTOR_SELECT");
        for (Lf8ControlSignal signal : DATAPATH_CONTROLS) {
            wire(document, control, signal.name(), datapath, signal.name());
        }
        wire(document, control, "ALU_OP", datapath, "ALU_OP");
        wire(document, control, Lf8ControlSignal.MEMORY_READ.name(), memoryRead, "IN");
        wire(document, control, Lf8ControlSignal.MEMORY_WRITE.name(), memoryWrite, "IN");
        wire(document, control, Lf8ControlSignal.HALT.name(), halt, "IN");
        return document;
    }

    private static CircuitDocument createMain(ComponentRegistry registry, int[] program,
                                              int irqVector, int nmiVector, int resetVector) {
        CircuitDocument document = document(CircuitProject.MAIN_CIRCUIT, "LF-8 computer");
        ComponentInstance clk = add(document, registry, "source.toggle", 0, 0,
                ParameterValues.empty(), "CLK");
        ComponentInstance reset = add(document, registry, "source.toggle", 0, 40,
                ParameterValues.empty(), "RESET");
        ComponentInstance irq = add(document, registry, "source.toggle", 0, 80,
                ParameterValues.empty(), "IRQ");
        ComponentInstance nmi = add(document, registry, "source.toggle", 0, 120,
                ParameterValues.empty(), "NMI");
        ComponentInstance cpu = subcircuit(document, CPU_CIRCUIT, 220, 60, "CPU");
        role(document, cpu, Lf8ComponentRoles.CPU);
        ComponentInstance rom = add(document, registry, "memory.rom", 680, 0,
                defaults(registry, "memory.rom")
                        .with(LibraryParameters.ADDRESS_WIDTH, 15)
                        .with(LibraryParameters.WIDTH, 8)
                        .with(LibraryParameters.ROM_CONTENTS, programContents(program)), "PROG_ROM");
        ComponentInstance ram = add(document, registry, "memory.ram", 680, 160,
                defaults(registry, "memory.ram")
                        .with(LibraryParameters.ADDRESS_WIDTH, 14)
                        .with(LibraryParameters.WIDTH, 8), "RAM");
        ComponentInstance vectorRom = add(document, registry, "memory.rom", 680, 300,
                defaults(registry, "memory.rom")
                        .with(LibraryParameters.ADDRESS_WIDTH, 3)
                        .with(LibraryParameters.WIDTH, 8)
                        .with(LibraryParameters.ROM_CONTENTS,
                                vectorContents(irqVector, nmiVector, resetVector)), "VECTOR_ROM");
        ComponentInstance outputPort = add(document, registry, "system.output_port", 680, 440,
                defaults(registry, "system.output_port").with(LibraryParameters.WIDTH, 8),
                "OUTPUT_PORT");
        ComponentInstance characterOutput = add(document, registry, "system.character_output",
                880, 440, defaults(registry, "system.character_output"), "CHARACTER_OUTPUT");
        ComponentInstance inputPort = add(document, registry, "system.input_port", 680, 540,
                defaults(registry, "system.input_port").with(LibraryParameters.WIDTH, 8),
                "INPUT_PORT");
        ComponentInstance timer = add(document, registry, "system.timer", 680, 660,
                defaults(registry, "system.timer"), "TIMER");
        ComponentInstance romDecoder = decoder(document, registry, 420, 0,
                hex(Lf8MemoryMap.ROM_START), "8000",
                "ROM_SELECT");
        ComponentInstance ramDecoder = decoder(document, registry, 420, 160,
                hex(Lf8MemoryMap.RAM_START), "c000",
                "RAM_SELECT");
        ComponentInstance vectorDecoder = decoder(document, registry, 420, 300,
                hex(Lf8MemoryMap.IRQ_VECTOR), "fff8", "VECTOR_SELECT");
        ComponentInstance outputPortDecoder = decoder(document, registry, 420, 440,
                hex(Lf8MemoryMap.OUTPUT_PORT), "ffff", "OUTPUT_PORT_SELECT");
        ComponentInstance inputPortDecoder = decoder(document, registry, 420, 540,
                hex(Lf8MemoryMap.INPUT_PORT), "ffff", "INPUT_PORT_SELECT");
        ComponentInstance timerDecoder = decoder(document, registry, 420, 660,
                hex(Lf8MemoryMap.TIMER_RELOAD_LOW), "fffc", "TIMER_SELECT");
        ComponentInstance timerRegister = slice(document, registry, 520, 680, 16, 2,
                "TIMER_REGISTER_SELECT");
        ComponentInstance irqRequests = add(document, registry, "logic.or", 120, 100,
                ParameterValues.empty(), "IRQ_REQUESTS");
        ComponentInstance romAddress = slice(document, registry, 520, 20, 16, 15, "ROM_ADDRESS");
        ComponentInstance ramAddress = slice(document, registry, 520, 180, 16, 14, "RAM_ADDRESS");
        ComponentInstance vectorAddress = slice(document, registry, 520, 320, 16, 3,
                "VECTOR_ADDRESS");
        ComponentInstance romEnable = add(document, registry, "logic.and", 580, 70,
                ParameterValues.empty(), "ROM_ENABLE");
        ComponentInstance vectorEnable = add(document, registry, "logic.and", 580, 350,
                ParameterValues.empty(), "VECTOR_ENABLE");
        ComponentInstance haltProbe = add(document, registry, "output.probe", 420, 300,
                ParameterValues.empty(), "HALT_PROBE");
        // The asynchronous SRAM write window is the low clock phase. This guarantees that
        // ripple-built PC/SP/address paths have settled before WE is asserted and closes
        // the write window before the active edge changes architectural state.
        ComponentInstance clockNot = add(document, registry, "logic.not", 260, 360,
                ParameterValues.empty(), "RAM_WRITE_PHASE_NOT");
        ComponentInstance ramWriteStrobe = add(document, registry, "logic.and", 340, 360,
                ParameterValues.empty(), "RAM_WRITE_STROBE");

        wire(document, clk, "OUT", cpu, "CLK");
        wire(document, clk, "OUT", outputPort, "CLK");
        wire(document, clk, "OUT", characterOutput, "CLK");
        wire(document, clk, "OUT", timer, "CLK");
        wire(document, reset, "OUT", cpu, "RESET");
        wire(document, reset, "OUT", outputPort, "RESET");
        wire(document, reset, "OUT", characterOutput, "RESET");
        wire(document, reset, "OUT", timer, "RESET");
        wire(document, irq, "OUT", irqRequests, "IN0");
        wire(document, timer, "IRQ", irqRequests, "IN1");
        wire(document, irqRequests, "OUT", cpu, "IRQ");
        wire(document, nmi, "OUT", cpu, "NMI");
        wire(document, cpu, "ADDRESS", romDecoder, "ADDRESS");
        wire(document, cpu, "ADDRESS", ramDecoder, "ADDRESS");
        wire(document, cpu, "ADDRESS", vectorDecoder, "ADDRESS");
        wire(document, cpu, "ADDRESS", outputPortDecoder, "ADDRESS");
        wire(document, cpu, "ADDRESS", inputPortDecoder, "ADDRESS");
        wire(document, cpu, "ADDRESS", timerDecoder, "ADDRESS");
        wire(document, cpu, "ADDRESS", timerRegister, "IN");
        wire(document, cpu, "ADDRESS", romAddress, "IN");
        wire(document, cpu, "ADDRESS", ramAddress, "IN");
        wire(document, cpu, "ADDRESS", vectorAddress, "IN");
        wire(document, romAddress, "OUT", rom, "ADDRESS");
        wire(document, ramAddress, "OUT", ram, "ADDRESS");
        wire(document, vectorAddress, "OUT", vectorRom, "ADDRESS");
        wire(document, romDecoder, "SELECT", romEnable, "IN0");
        wire(document, cpu, "MEMORY_READ", romEnable, "IN1");
        wire(document, romEnable, "OUT", rom, "ENABLE");
        wire(document, vectorDecoder, "SELECT", vectorEnable, "IN0");
        wire(document, cpu, "MEMORY_READ", vectorEnable, "IN1");
        wire(document, vectorEnable, "OUT", vectorRom, "ENABLE");
        wire(document, ramDecoder, "SELECT", ram, "CS");
        wire(document, cpu, "MEMORY_READ", ram, "OE");
        wire(document, clk, "OUT", clockNot, "A");
        wire(document, cpu, "MEMORY_WRITE", ramWriteStrobe, "IN0");
        wire(document, clockNot, "Y", ramWriteStrobe, "IN1");
        wire(document, ramWriteStrobe, "OUT", ram, "WE");
        wire(document, cpu, "DATA", outputPort, "DATA");
        wire(document, outputPortDecoder, "SELECT", outputPort, "SELECT");
        wire(document, cpu, "MEMORY_WRITE", outputPort, "WRITE");
        wire(document, cpu, "DATA", characterOutput, "DATA");
        wire(document, outputPortDecoder, "SELECT", characterOutput, "SELECT");
        wire(document, cpu, "MEMORY_WRITE", characterOutput, "WRITE");
        wire(document, inputPortDecoder, "SELECT", inputPort, "SELECT");
        wire(document, cpu, "MEMORY_READ", inputPort, "READ");
        wire(document, timerDecoder, "SELECT", timer, "SELECT");
        wire(document, timerRegister, "OUT", timer, "REGISTER_SELECT");
        wire(document, cpu, "MEMORY_READ", timer, "READ");
        wire(document, cpu, "MEMORY_WRITE", timer, "WRITE");
        wire(document, cpu, "DATA", rom, "DATA");
        wire(document, cpu, "DATA", ram, "DATA");
        wire(document, cpu, "DATA", vectorRom, "DATA");
        wire(document, cpu, "DATA", inputPort, "DATA");
        wire(document, cpu, "DATA", timer, "DATA");
        wire(document, cpu, "HALT", haltProbe, "IN");
        return document;
    }

    private static CircuitDocument document(String name, String description) {
        return new CircuitDocument(new CircuitMetadata(name, description));
    }

    private static ComponentInstance register(CircuitDocument document, ComponentRegistry registry,
                                              Lf8ImplementationMode mode, double x, double y,
                                              int width, String label) {
        return mode == Lf8ImplementationMode.GATE_LEVEL
                ? subcircuit(document,
                        StructuralCircuitFactory.structuralRegisterName(width, true, 0),
                        x, y, label)
                : add(document, registry, "sequential.register", x, y,
                        defaults(registry, "sequential.register")
                                .with(LibraryParameters.WIDTH, width), label);
    }

    private static ComponentInstance resetRegister(CircuitDocument document,
            ComponentRegistry registry, Lf8ImplementationMode mode, double x, double y,
            int width, String label) {
        return mode == Lf8ImplementationMode.GATE_LEVEL
                ? subcircuit(document,
                        StructuralCircuitFactory.structuralRegisterName(width, true, 0),
                        x, y, label)
                : add(document, registry, "sequential.register_reset", x, y,
                        defaults(registry, "sequential.register_reset")
                                .with(LibraryParameters.WIDTH, width), label);
    }

    private static ComponentInstance counter16(CircuitDocument document,
            ComponentRegistry registry, Lf8ImplementationMode mode, double x, double y,
            int resetValue, String label) {
        return mode == Lf8ImplementationMode.GATE_LEVEL
                ? subcircuit(document, StructuralCircuitFactory.loadableCounter16Name(resetValue),
                        x, y, label)
                : add(document, registry, "sequential.loadable_counter", x, y,
                        defaults(registry, "sequential.loadable_counter")
                                .with(LibraryParameters.WIDTH, 16)
                                .with(LibraryParameters.RESET_VALUE,
                                        Integer.toHexString(resetValue)), label);
    }

    private static void addGateLevelStructures(CircuitProject project) {
        List<CircuitProject> families = List.of(
                StructuralCircuitFactory.structuralRegister(1, true, 0),
                StructuralCircuitFactory.structuralRegister(3, true, 0),
                StructuralCircuitFactory.structuralRegister(4, true, 0),
                StructuralCircuitFactory.structuralRegister(8, true, 0),
                StructuralCircuitFactory.resettableRegisterFile8x8Project(),
                StructuralCircuitFactory.loadableCounter16Project(0),
                StructuralCircuitFactory.loadableCounter16Project(0xBFFF),
                StructuralCircuitFactory.moduloCounter3Project(),
                StructuralCircuitFactory.structuralBusMuxProject(1),
                StructuralCircuitFactory.structuralBusMuxProject(3),
                StructuralCircuitFactory.structuralBusMuxProject(4),
                StructuralCircuitFactory.structuralBusMuxProject(8),
                StructuralCircuitFactory.structuralBusMuxProject(16),
                StructuralCircuitFactory.decrementer16Project());
        for (CircuitProject family : families) {
            family.circuits().stream()
                    .filter(circuit -> !CircuitProject.MAIN_CIRCUIT.equals(
                            circuit.metadata().name()))
                    .forEach(project::putCircuit);
        }
    }

    private static ComponentInstance mux(CircuitDocument document, ComponentRegistry registry,
                                         double x, double y, int width, String label) {
        return add(document, registry, "routing.mux2", x, y,
                defaults(registry, "routing.mux2").with(LibraryParameters.WIDTH, width), label);
    }

    private static ComponentInstance mux(CircuitDocument document, ComponentRegistry registry,
                                         Lf8ImplementationMode mode, double x, double y,
                                         int width, String label) {
        return mode == Lf8ImplementationMode.GATE_LEVEL
                ? subcircuit(document, StructuralCircuitFactory.structuralBusMuxName(width),
                        x, y, label)
                : mux(document, registry, x, y, width, label);
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

    private static ComponentInstance role(CircuitDocument document, ComponentInstance instance,
                                          String semanticRole) {
        ComponentInstance updated = instance.withSemanticRole(semanticRole);
        document.replaceComponent(updated);
        return updated;
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
        if (program.length > Lf8MemoryMap.ROM_END - Lf8MemoryMap.ROM_START + 1) {
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

    private static String vectorContents(int irqVector, int nmiVector, int resetVector) {
        return String.join(",",
                hexByte(irqVector), hexByte(irqVector >>> 8),
                hexByte(nmiVector), hexByte(nmiVector >>> 8),
                hexByte(resetVector), hexByte(resetVector >>> 8),
                "0", "0");
    }

    private static String hexByte(int value) {
        return Integer.toHexString(value & 0xff);
    }

    private static String hex(int value) {
        return Integer.toHexString(value & 0xffff);
    }

    private static void requireAddress(int address, String label) {
        if (address < 0 || address > 0xffff) {
            throw new IllegalArgumentException(label + " is outside 0x0000..0xffff: " + address);
        }
    }
}
