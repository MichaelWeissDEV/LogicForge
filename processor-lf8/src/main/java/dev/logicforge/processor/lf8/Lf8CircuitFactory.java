package dev.logicforge.processor.lf8;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitMetadata;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PortEndpoint;
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
            Lf8ControlSignal.ALU_SUBTRACT,
            Lf8ControlSignal.PC_LOAD,
            Lf8ControlSignal.ADDRESS_FROM_MAR);

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
        ComponentInstance alu = add(document, registry, "arithmetic.add_sub", 570, 110,
                defaults(registry, "arithmetic.add_sub").with(LibraryParameters.WIDTH, 8), "ALU");

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
        ComponentInstance writeDriver = add(document, registry, "routing.tristate_n", 850, 150,
                defaults(registry, "routing.tristate_n").with(LibraryParameters.WIDTH, 8),
                "DATA_WRITE_DRIVER");
        ComponentInstance flags = constant(document, registry, 850, 300, 4, 0, "FLAGS_TIE");

        for (ComponentInstance target : List.of(pc, ir, destination, source, marLow, marHigh, registers)) {
            wire(document, clk, "OUT", target, "CLK");
        }
        wire(document, reset, "OUT", pc, "RESET");

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
        wire(document, registers, "RD_DATA_B", alu, "B");
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
        wire(document, addressSource, "OUT", address, "IN");
        wire(document, registers, "RD_DATA_B", writeDriver, "A");
        wire(document, writeDriver, "Y", data, "BUS");
        wire(document, ir, "Q", opcode, "IN");
        wire(document, flags, "OUT", flagsOut, "IN");

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
        controlWire(document, controls, Lf8ControlSignal.ALU_SUBTRACT, alu, "SUB");
        controlWire(document, controls, Lf8ControlSignal.MEMORY_WRITE, writeDriver, "ENABLE");
        controlWire(document, controls, Lf8ControlSignal.PC_LOAD, pc, "LOAD");
        controlWire(document, controls, Lf8ControlSignal.ADDRESS_FROM_MAR, addressSource, "SEL");
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
                        .with(LibraryParameters.ADDRESS_WIDTH, 15)
                        .with(LibraryParameters.WIDTH, Lf8ControlSignal.wordWidth())
                        .with(LibraryParameters.ROM_CONTENTS, Lf8Microcode.contents()), "MICROCODE_ROM");
        ComponentInstance microcodeEnable = constant(document, registry, 480, 160, 1, 1,
                "MICROCODE_ROM_EN");
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

        wire(document, clk, "OUT", microstep, "CLK");
        wire(document, clk, "OUT", haltLatch, "CLK");
        wire(document, reset, "OUT", microstep, "RESET");
        wire(document, reset, "OUT", haltLatch, "RESET");
        wire(document, irq, "OUT", irqProbe, "IN");
        rangeWire(document, opcode, "OUT", microcode, "ADDRESS", 14, 7);
        rangeWire(document, flags, "OUT", microcode, "ADDRESS", 6, 3);
        rangeWire(document, microstep, "COUNT", microcode, "ADDRESS", 2, 0);
        wire(document, microcodeEnable, "OUT", microcode, "ENABLE");
        control(document, microcode, Lf8ControlSignal.HALT, haltOr, "IN0");
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
                control(document, microcode, signal, signalOut, "IN");
            }
            outputY += 35;
        }
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
        ComponentInstance irq = add(document, registry, "source.zero", 0, 80,
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

    private static void rangeWire(CircuitDocument document, ComponentInstance from, String fromPort,
                                  ComponentInstance to, String toPort, int msb, int lsb) {
        document.addConnection(Connection.create(
                PortEndpoint.whole(new PortReference(from.id(), fromPort)),
                PortEndpoint.range(new PortReference(to.id(), toPort), msb, lsb)));
    }

    private static void control(CircuitDocument document, ComponentInstance rom,
                                Lf8ControlSignal signal, ComponentInstance target, String targetPort) {
        document.addConnection(Connection.create(
                PortEndpoint.bit(new PortReference(rom.id(), "DATA"), signal.bit()),
                PortEndpoint.whole(new PortReference(target.id(), targetPort))));
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
