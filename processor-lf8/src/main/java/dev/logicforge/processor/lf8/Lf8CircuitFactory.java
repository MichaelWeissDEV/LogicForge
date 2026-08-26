package dev.logicforge.processor.lf8;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitMetadata;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.LibraryParameters;
import java.util.List;

/** Builds the structural LF-8 CPU prototype from ordinary LogicForge components. */
public final class Lf8CircuitFactory {

    private Lf8CircuitFactory() {
    }

    public static CircuitDocument createComputerCircuit(int... program) {
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", "LF-8"));
        ComponentRegistry registry = ComponentRegistry.standard();
        ComponentInstance clk = add(main, registry, "source.toggle", 0, 0,
                ParameterValues.empty(), "CLK");
        ComponentInstance reset = add(main, registry, "source.toggle", 0, 40,
                ParameterValues.empty(), "RESET");
        ComponentInstance pc = add(main, registry, "sequential.loadable_counter", 100, 0,
                defaults(registry, "sequential.loadable_counter")
                        .with(LibraryParameters.WIDTH, 16), "PC");
        ComponentInstance rom = add(main, registry, "memory.rom", 200, 0,
                defaults(registry, "memory.rom")
                        .with(LibraryParameters.ADDRESS_WIDTH, 16)
                        .with(LibraryParameters.WIDTH, 8)
                        .with(LibraryParameters.ROM_CONTENTS, programContents(program)), "PROG_ROM");
        ComponentInstance romEnable = constant(main, registry, 200, 40, 1, 1, "PROG_ROM_EN");

        ComponentInstance ir = register(main, registry, 300, 0, 8, "IR");
        ComponentInstance destination = register(main, registry, 300, 60, 3, "DESTINATION");
        ComponentInstance source = register(main, registry, 300, 120, 3, "SOURCE");
        ComponentInstance operandSlice = add(main, registry, "routing.bus_slice", 250, 100,
                defaults(registry, "routing.bus_slice")
                        .with(LibraryParameters.INPUT_WIDTH, 8)
                        .with(LibraryParameters.OUTPUT_WIDTH, 3)
                        .with(LibraryParameters.SLICE_LSB, 0), "OPERAND_SLICE");
        ComponentInstance marLow = register(main, registry, 300, 180, 8, "MAR_LO");
        ComponentInstance marHigh = register(main, registry, 300, 240, 8, "MAR_HI");
        ComponentInstance registers = add(main, registry, "memory.register_file", 430, 80,
                defaults(registry, "memory.register_file")
                        .with(LibraryParameters.WIDTH, 8)
                        .with(LibraryParameters.REGISTER_COUNT, 8), "REGISTER_FILE");
        ComponentInstance alu = add(main, registry, "arithmetic.add_sub", 560, 80,
                defaults(registry, "arithmetic.add_sub").with(LibraryParameters.WIDTH, 8), "ALU");

        ComponentInstance immediateOrAlu = mux(main, registry, 650, 40, "WRITE_IMMEDIATE_ALU");
        ComponentInstance memoryOrMove = mux(main, registry, 650, 100, "WRITE_MEMORY_MOVE");
        ComponentInstance writeSource = mux(main, registry, 760, 70, "WRITE_SOURCE");

        ComponentInstance addressConcat = add(main, registry, "routing.bus_concat", 500, 230,
                defaults(registry, "routing.bus_concat")
                        .with(LibraryParameters.LOW_WIDTH, 8)
                        .with(LibraryParameters.HIGH_WIDTH, 8), "ADDRESS_CONCAT");
        ComponentInstance ram = add(main, registry, "memory.ram", 900, 0,
                defaults(registry, "memory.ram")
                        .with(LibraryParameters.ADDRESS_WIDTH, 16)
                        .with(LibraryParameters.WIDTH, 8), "RAM");
        ComponentInstance ramSelect = constant(main, registry, 900, 40, 1, 1, "RAM_CS_TIE");
        ComponentInstance writeDriver = add(main, registry, "routing.tristate_n", 900, 80,
                defaults(registry, "routing.tristate_n").with(LibraryParameters.WIDTH, 8),
                "REGISTER_TO_RAM_DRIVER");

        ComponentInstance microstep = add(main, registry, "sequential.modulo_counter", 300, 320,
                defaults(registry, "sequential.modulo_counter")
                        .with(LibraryParameters.WIDTH, 3)
                        .with(LibraryParameters.MODULUS, Lf8Microcode.MICROSTEPS), "MICROSTEP");
        ComponentInstance microcode = add(main, registry, "memory.rom", 520, 360,
                defaults(registry, "memory.rom")
                        .with(LibraryParameters.ADDRESS_WIDTH, 13)
                        .with(LibraryParameters.WIDTH, Lf8ControlSignal.wordWidth())
                        .with(LibraryParameters.ROM_CONTENTS, Lf8Microcode.contents()), "MICROCODE_ROM");
        ComponentInstance microcodeEnable = constant(main, registry, 520, 410, 1, 1,
                "MICROCODE_ROM_EN");
        ComponentInstance flags = constant(main, registry, 520, 450, 2, 0, "FLAGS_TIE");
        ComponentInstance haltLatch = add(main, registry, "sequential.register_reset", 760, 320,
                defaults(registry, "sequential.register_reset").with(LibraryParameters.WIDTH, 1),
                "HALT_LATCH");
        ComponentInstance haltOr = add(main, registry, "logic.or", 830, 320,
                ParameterValues.empty(), "HALT_OR");
        ComponentInstance haltLoad = constant(main, registry, 760, 370, 1, 1, "HALT_LOAD_TIE");
        ComponentInstance haltNot = add(main, registry, "logic.not", 900, 320,
                ParameterValues.empty(), "HALT_NOT");

        for (ComponentInstance target : List.of(pc, ir, destination, source, marLow, marHigh,
                registers, microstep, haltLatch)) {
            wire(main, clk, "OUT", target, "CLK");
        }
        for (ComponentInstance target : List.of(pc, microstep, haltLatch)) {
            wire(main, reset, "OUT", target, "RESET");
        }

        wire(main, pc, "COUNT", rom, "ADDRESS");
        wire(main, romEnable, "OUT", rom, "ENABLE");
        wire(main, rom, "DATA", ir, "DATA");
        wire(main, rom, "DATA", operandSlice, "IN");
        wire(main, operandSlice, "OUT", destination, "DATA");
        wire(main, operandSlice, "OUT", source, "DATA");
        wire(main, rom, "DATA", marLow, "DATA");
        wire(main, rom, "DATA", marHigh, "DATA");
        wire(main, rom, "DATA", immediateOrAlu, "IN0");

        wire(main, destination, "Q", registers, "RD_ADDR_A");
        wire(main, destination, "Q", registers, "WR_ADDR");
        wire(main, source, "Q", registers, "RD_ADDR_B");
        wire(main, registers, "RD_DATA_A", alu, "A");
        wire(main, registers, "RD_DATA_B", alu, "B");
        wire(main, alu, "RESULT", immediateOrAlu, "IN1");
        wire(main, ram, "DATA", memoryOrMove, "IN0");
        wire(main, registers, "RD_DATA_B", memoryOrMove, "IN1");
        wire(main, immediateOrAlu, "OUT", writeSource, "IN0");
        wire(main, memoryOrMove, "OUT", writeSource, "IN1");
        wire(main, writeSource, "OUT", registers, "WR_DATA");

        wire(main, marLow, "Q", addressConcat, "LOW");
        wire(main, marHigh, "Q", addressConcat, "HIGH");
        wire(main, addressConcat, "OUT", pc, "DATA");
        wire(main, addressConcat, "OUT", ram, "ADDRESS");
        wire(main, registers, "RD_DATA_B", writeDriver, "A");
        wire(main, writeDriver, "Y", ram, "DATA");
        wire(main, ramSelect, "OUT", ram, "CS");

        rangeWire(main, ir, "Q", microcode, "ADDRESS", 12, 5);
        rangeWire(main, flags, "OUT", microcode, "ADDRESS", 4, 3);
        rangeWire(main, microstep, "COUNT", microcode, "ADDRESS", 2, 0);
        wire(main, microcodeEnable, "OUT", microcode, "ENABLE");
        control(main, microcode, Lf8ControlSignal.PC_INCREMENT, pc, "ENABLE");
        control(main, microcode, Lf8ControlSignal.IR_LOAD, ir, "LOAD");
        control(main, microcode, Lf8ControlSignal.DESTINATION_REGISTER_LOAD, destination, "LOAD");
        control(main, microcode, Lf8ControlSignal.SOURCE_REGISTER_LOAD, source, "LOAD");
        control(main, microcode, Lf8ControlSignal.MAR_LOW_LOAD, marLow, "LOAD");
        control(main, microcode, Lf8ControlSignal.MAR_HIGH_LOAD, marHigh, "LOAD");
        control(main, microcode, Lf8ControlSignal.REGISTER_FILE_WRITE, registers, "WR_EN");
        control(main, microcode, Lf8ControlSignal.ALU_SOURCE, immediateOrAlu, "SEL");
        control(main, microcode, Lf8ControlSignal.MOV_SOURCE, memoryOrMove, "SEL");
        control(main, microcode, Lf8ControlSignal.ALTERNATE_SOURCE, writeSource, "SEL");
        control(main, microcode, Lf8ControlSignal.ALU_SUBTRACT, alu, "SUB");
        control(main, microcode, Lf8ControlSignal.MEMORY_WRITE, ram, "WE");
        control(main, microcode, Lf8ControlSignal.MEMORY_WRITE, writeDriver, "ENABLE");
        control(main, microcode, Lf8ControlSignal.MEMORY_READ, ram, "OE");
        control(main, microcode, Lf8ControlSignal.PC_LOAD, pc, "LOAD");
        control(main, microcode, Lf8ControlSignal.HALT, haltOr, "IN0");

        wire(main, haltOr, "OUT", haltLatch, "DATA");
        wire(main, haltLatch, "Q", haltOr, "IN1");
        wire(main, haltLoad, "OUT", haltLatch, "LOAD");
        wire(main, haltLatch, "Q", haltNot, "A");
        wire(main, haltNot, "Y", microstep, "ENABLE");
        return main;
    }

    private static ComponentInstance register(CircuitDocument document, ComponentRegistry registry,
                                              double x, double y, int width, String label) {
        return add(document, registry, "sequential.register", x, y,
                defaults(registry, "sequential.register").with(LibraryParameters.WIDTH, width), label);
    }

    private static ComponentInstance mux(CircuitDocument document, ComponentRegistry registry,
                                         double x, double y, String label) {
        return add(document, registry, "routing.mux2", x, y,
                defaults(registry, "routing.mux2").with(LibraryParameters.WIDTH, 8), label);
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

    private static String programContents(int[] program) {
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
