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

/** Builds the proven structural LF-8 prototype from ordinary LogicForge components. */
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
        ComponentInstance progRom = add(main, registry, "memory.rom", 200, 0,
                defaults(registry, "memory.rom")
                        .with(LibraryParameters.ADDRESS_WIDTH, 16)
                        .with(LibraryParameters.WIDTH, 8)
                        .with(LibraryParameters.ROM_CONTENTS, programContents(program)),
                "PROG_ROM");
        ComponentInstance progRomEn = constant(main, registry, 200, 40, 1, 1, "PROG_ROM_EN");

        ComponentInstance ir = register(main, registry, 300, 0, 8, "IR");
        ComponentInstance r0 = register(main, registry, 300, 60, 8, "R0");
        ComponentInstance r1 = register(main, registry, 300, 120, 8, "R1");
        ComponentInstance marLo = register(main, registry, 300, 180, 8, "MAR_LO");
        ComponentInstance marHi = register(main, registry, 300, 240, 8, "MAR_HI");

        ComponentInstance alu = add(main, registry, "arithmetic.add_sub", 400, 60,
                defaults(registry, "arithmetic.add_sub").with(LibraryParameters.WIDTH, 8), "ALU");
        ComponentInstance r0MuxImmAlu = mux(main, registry, 500, 40, "R0_MUX_IMM_ALU");
        ComponentInstance r0MuxRamR1 = mux(main, registry, 500, 80, "R0_MUX_RAM_R1");
        ComponentInstance r0MuxOuter = mux(main, registry, 500, 120, "R0_MUX_OUTER");
        ComponentInstance r0OuterSel = add(main, registry, "logic.or", 500, 160,
                ParameterValues.empty(), "R0_OUTER_SEL");
        ComponentInstance r0LoadOr = add(main, registry, "logic.or", 500, 200,
                defaults(registry, "logic.or").with(LibraryParameters.INPUT_COUNT, 4), "R0_LOAD_OR");
        ComponentInstance r1Mux = mux(main, registry, 600, 160, "R1_MUX");
        ComponentInstance r1LoadOr = add(main, registry, "logic.or", 600, 200,
                ParameterValues.empty(), "R1_LOAD_OR");

        ComponentInstance microstep = add(main, registry, "sequential.modulo_counter", 300, 300,
                defaults(registry, "sequential.modulo_counter")
                        .with(LibraryParameters.WIDTH, 2)
                        .with(LibraryParameters.MODULUS, Lf8Microcode.MICROSTEPS), "MICROSTEP");
        ComponentInstance haltLatch = add(main, registry, "sequential.register_reset", 700, 200,
                defaults(registry, "sequential.register_reset").with(LibraryParameters.WIDTH, 1),
                "HALT_LATCH");
        ComponentInstance haltOr = add(main, registry, "logic.or", 700, 240,
                ParameterValues.empty(), "HALT_OR");
        ComponentInstance haltLoadTie = constant(main, registry, 700, 280, 1, 1,
                "HALT_LOAD_TIE");
        ComponentInstance haltNot = add(main, registry, "logic.not", 700, 320,
                ParameterValues.empty(), "HALT_NOT");

        ComponentInstance microcodeRom = add(main, registry, "memory.rom", 500, 340,
                defaults(registry, "memory.rom")
                        .with(LibraryParameters.ADDRESS_WIDTH, 12)
                        .with(LibraryParameters.WIDTH, Lf8ControlSignal.wordWidth())
                        .with(LibraryParameters.ROM_CONTENTS, Lf8Microcode.contents()),
                "MICROCODE_ROM");
        ComponentInstance microcodeRomEn = constant(main, registry, 500, 380, 1, 1,
                "MICROCODE_ROM_EN");
        ComponentInstance flagsTie = constant(main, registry, 500, 420, 2, 0, "FLAGS_TIE");

        ComponentInstance ram = add(main, registry, "memory.ram", 800, 0,
                defaults(registry, "memory.ram")
                        .with(LibraryParameters.ADDRESS_WIDTH, 16)
                        .with(LibraryParameters.WIDTH, 8), "RAM");
        ComponentInstance ramCsTie = constant(main, registry, 800, 40, 1, 1, "RAM_CS_TIE");
        ComponentInstance r0ToRamDriver = add(main, registry, "routing.tristate_n", 800, 80,
                defaults(registry, "routing.tristate_n").with(LibraryParameters.WIDTH, 8),
                "R0_TO_RAM_DRIVER");

        for (ComponentInstance target : List.of(pc, ir, r0, r1, marLo, marHi, microstep, haltLatch)) {
            wire(main, clk, "OUT", target, "CLK");
        }
        wire(main, reset, "OUT", pc, "RESET");
        wire(main, reset, "OUT", microstep, "RESET");
        wire(main, reset, "OUT", haltLatch, "RESET");

        wire(main, pc, "COUNT", progRom, "ADDRESS");
        rangeWire(main, marLo, "Q", pc, "DATA", 7, 0);
        rangeWire(main, marHi, "Q", pc, "DATA", 15, 8);
        wire(main, progRomEn, "OUT", progRom, "ENABLE");
        for (ComponentInstance target : List.of(ir, r1Mux, marLo, marHi, r0MuxImmAlu)) {
            String port = target == r1Mux || target == r0MuxImmAlu ? "IN0" : "DATA";
            wire(main, progRom, "DATA", target, port);
        }

        wire(main, r0, "Q", alu, "A");
        wire(main, r1, "Q", alu, "B");
        wire(main, alu, "RESULT", r0MuxImmAlu, "IN1");
        wire(main, ram, "DATA", r0MuxRamR1, "IN0");
        wire(main, r1, "Q", r0MuxRamR1, "IN1");
        wire(main, r0MuxImmAlu, "OUT", r0MuxOuter, "IN0");
        wire(main, r0MuxRamR1, "OUT", r0MuxOuter, "IN1");
        wire(main, r0MuxOuter, "OUT", r0, "DATA");
        wire(main, r0LoadOr, "OUT", r0, "LOAD");
        wire(main, r1Mux, "OUT", r1, "DATA");
        wire(main, r0, "Q", r1Mux, "IN1");
        wire(main, r1LoadOr, "OUT", r1, "LOAD");

        rangeWire(main, ir, "Q", microcodeRom, "ADDRESS", 11, 4);
        rangeWire(main, flagsTie, "OUT", microcodeRom, "ADDRESS", 3, 2);
        rangeWire(main, microstep, "COUNT", microcodeRom, "ADDRESS", 1, 0);
        wire(main, microcodeRomEn, "OUT", microcodeRom, "ENABLE");

        control(main, microcodeRom, Lf8ControlSignal.PC_INCREMENT, pc, "ENABLE");
        control(main, microcodeRom, Lf8ControlSignal.IR_LOAD, ir, "LOAD");
        control(main, microcodeRom, Lf8ControlSignal.R0_LOAD_IMMEDIATE, r0LoadOr, "IN0");
        control(main, microcodeRom, Lf8ControlSignal.R1_LOAD_IMMEDIATE, r1LoadOr, "IN0");
        control(main, microcodeRom, Lf8ControlSignal.MAR_LOW_LOAD, marLo, "LOAD");
        control(main, microcodeRom, Lf8ControlSignal.MAR_HIGH_LOAD, marHi, "LOAD");
        control(main, microcodeRom, Lf8ControlSignal.ALU_LOAD, r0MuxImmAlu, "SEL");
        control(main, microcodeRom, Lf8ControlSignal.ALU_LOAD, r0LoadOr, "IN1");
        control(main, microcodeRom, Lf8ControlSignal.ALU_SUBTRACT, alu, "SUB");
        control(main, microcodeRom, Lf8ControlSignal.MEMORY_WRITE, ram, "WE");
        control(main, microcodeRom, Lf8ControlSignal.MEMORY_WRITE, r0ToRamDriver, "ENABLE");
        control(main, microcodeRom, Lf8ControlSignal.MOVE_R0_FROM_R1, r0MuxRamR1, "SEL");
        control(main, microcodeRom, Lf8ControlSignal.MOVE_R0_FROM_R1, r0OuterSel, "IN0");
        control(main, microcodeRom, Lf8ControlSignal.MOVE_R0_FROM_R1, r0LoadOr, "IN2");
        control(main, microcodeRom, Lf8ControlSignal.LOAD_R0_FROM_MEMORY, r0OuterSel, "IN1");
        control(main, microcodeRom, Lf8ControlSignal.LOAD_R0_FROM_MEMORY, r0LoadOr, "IN3");
        control(main, microcodeRom, Lf8ControlSignal.LOAD_R0_FROM_MEMORY, ram, "OE");
        wire(main, r0OuterSel, "OUT", r0MuxOuter, "SEL");
        control(main, microcodeRom, Lf8ControlSignal.MOVE_R1_FROM_R0, r1Mux, "SEL");
        control(main, microcodeRom, Lf8ControlSignal.MOVE_R1_FROM_R0, r1LoadOr, "IN1");
        control(main, microcodeRom, Lf8ControlSignal.PC_LOAD, pc, "LOAD");
        control(main, microcodeRom, Lf8ControlSignal.HALT, haltOr, "IN0");

        wire(main, haltOr, "OUT", haltLatch, "DATA");
        wire(main, haltLatch, "Q", haltOr, "IN1");
        wire(main, haltLoadTie, "OUT", haltLatch, "LOAD");
        wire(main, haltLatch, "Q", haltNot, "A");
        wire(main, haltNot, "Y", microstep, "ENABLE");

        rangeWire(main, marLo, "Q", ram, "ADDRESS", 7, 0);
        rangeWire(main, marHi, "Q", ram, "ADDRESS", 15, 8);
        wire(main, r0, "Q", r0ToRamDriver, "A");
        wire(main, r0ToRamDriver, "Y", ram, "DATA");
        wire(main, ramCsTie, "OUT", ram, "CS");
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
                                              double x, double y, int width, long value,
                                              String label) {
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
                                Lf8ControlSignal signal, ComponentInstance target,
                                String targetPort) {
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
