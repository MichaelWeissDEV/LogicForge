package dev.logicforge.library;

import static dev.logicforge.circuit.component.ComponentCategory.ARITHMETIC;
import static dev.logicforge.circuit.component.ComponentCategory.LOGIC;
import static dev.logicforge.circuit.component.ComponentCategory.MEMORY;
import static dev.logicforge.circuit.component.ComponentCategory.OUTPUTS;
import static dev.logicforge.circuit.component.ComponentCategory.ROUTING;
import static dev.logicforge.circuit.component.ComponentCategory.SEQUENTIAL;
import static dev.logicforge.circuit.component.ComponentCategory.SOURCES;
import static dev.logicforge.circuit.component.ComponentCategory.HIERARCHY;
import static dev.logicforge.circuit.component.InputInteraction.MOMENTARY;
import static dev.logicforge.circuit.component.InputInteraction.TOGGLE;

import dev.logicforge.circuit.component.InputInteraction;
import dev.logicforge.circuit.document.SubcircuitSupport;

import dev.logicforge.circuit.component.ComponentCategory;
import dev.logicforge.circuit.component.ParameterSpec;
import dev.logicforge.library.behavior.AddSubBehavior;
import dev.logicforge.library.behavior.AdderBehavior;
import dev.logicforge.library.behavior.BitCounterBehavior;
import dev.logicforge.library.behavior.ClockBehavior;
import dev.logicforge.library.behavior.ClockDividerBehavior;
import dev.logicforge.library.behavior.ComparatorBehavior;
import dev.logicforge.library.behavior.ConstantBehavior;
import dev.logicforge.library.behavior.ConstantVectorBehavior;
import dev.logicforge.library.behavior.CounterBehavior;
import dev.logicforge.library.behavior.DFlipFlopBehavior;
import dev.logicforge.library.behavior.DLatchBehavior;
import dev.logicforge.library.behavior.DecoderBehavior;
import dev.logicforge.library.behavior.DemuxBehavior;
import dev.logicforge.library.behavior.EncoderBehavior;
import dev.logicforge.library.behavior.FlagsRegisterBehavior;
import dev.logicforge.library.behavior.FullAdderBehavior;
import dev.logicforge.library.behavior.HalfAdderBehavior;
import dev.logicforge.library.behavior.IncrementDecrementBehavior;
import dev.logicforge.library.behavior.JkFlipFlopBehavior;
import dev.logicforge.library.behavior.JoinerBehavior;
import dev.logicforge.library.behavior.LoadableCounterBehavior;
import dev.logicforge.library.behavior.ModuloCounterBehavior;
import dev.logicforge.library.behavior.MuxBehavior;
import dev.logicforge.library.behavior.NaryGateBehavior;
import dev.logicforge.library.behavior.OverflowDetectorBehavior;
import dev.logicforge.library.behavior.ParityBehavior;
import dev.logicforge.library.behavior.PisoBehavior;
import dev.logicforge.library.behavior.PriorityEncoderBehavior;
import dev.logicforge.library.behavior.RamBehavior;
import dev.logicforge.library.behavior.RegisterBehavior;
import dev.logicforge.library.behavior.RingJohnsonCounterBehavior;
import dev.logicforge.library.behavior.RomBehavior;
import dev.logicforge.library.behavior.ShiftBehavior;
import dev.logicforge.library.behavior.ShiftRegisterBehavior;
import dev.logicforge.library.behavior.SignDetectorBehavior;
import dev.logicforge.library.behavior.SignedComparatorBehavior;
import dev.logicforge.library.behavior.SinkBehavior;
import dev.logicforge.library.behavior.SplitterBehavior;
import dev.logicforge.library.behavior.SrLatchBehavior;
import dev.logicforge.library.behavior.SubtractorBehavior;
import dev.logicforge.library.behavior.TFlipFlopBehavior;
import dev.logicforge.library.behavior.TriStateBehavior;
import dev.logicforge.library.behavior.UnaryGateBehavior;
import dev.logicforge.library.behavior.UniversalShiftRegisterBehavior;
import dev.logicforge.library.behavior.UserInputBehavior;
import dev.logicforge.library.behavior.WideTriStateBehavior;
import dev.logicforge.library.behavior.ZeroDetectorBehavior;
import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperation;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import java.util.ArrayList;
import java.util.List;

/**
 * The built-in components of LogicForge 0.1: signal sources, the elementary gates and the
 * output devices.
 *
 * <p>Component ids are the identity stored in project files and never change. Gates that
 * share an implementation still get their own id — a saved circuit says
 * {@code logic.nand}, not "an AND gate with an inverted output".
 */
final class StandardLibrary {

    private StandardLibrary() {
    }

    static ComponentRegistry createRegistry() {
        ComponentRegistry registry = new ComponentRegistry();
        registerSources(registry);
        registerDrivers(registry);
        registerGates(registry);
        registerSequential(registry);
        registerRegisters(registry);
        registerCounters(registry);
        registerSequentialExpansion(registry);
        registerRouting(registry);
        registerArithmetic(registry);
        registerMemory(registry);
        registerHierarchy(registry);
        registerOutputs(registry);
        return registry;
    }

    private static void registerSources(ComponentRegistry registry) {
        registry.register(ComponentType.of(
                definition("source.zero", "Logic 0", SOURCES,
                        "Constant low level", List.of(), PortLayouts.source(),
                        List.of("constant", "low", "gnd", "ground", "0")),
                new ConstantBehavior(LogicState.ZERO)));

        registry.register(ComponentType.of(
                definition("source.one", "Logic 1", SOURCES,
                        "Constant high level", List.of(), PortLayouts.source(),
                        List.of("constant", "high", "vcc", "1")),
                new ConstantBehavior(LogicState.ONE)));

        registry.register(ComponentType.of(
                definition("source.unknown", "Logic X", SOURCES,
                        "Constant unknown level, useful for testing how a circuit reacts to X",
                        List.of(), PortLayouts.source(), List.of("constant", "unknown", "x")),
                new ConstantBehavior(LogicState.UNKNOWN)));

        registry.register(ComponentType.of(
                definition("source.highz", "Logic Z", SOURCES,
                        "Constant high impedance, an output that never drives",
                        List.of(), PortLayouts.source(),
                        List.of("constant", "float", "floating", "tri", "z")),
                new ConstantBehavior(LogicState.HIGH_IMPEDANCE)));

        registry.register(new ComponentType(
                definition("source.toggle", "Toggle Switch", SOURCES,
                        "Switches between 0 and 1 when clicked", List.of(LibraryParameters.INITIALLY_ON),
                        PortLayouts.source(), List.of("switch", "input", "toggle"), TOGGLE),
                values -> new UserInputBehavior(
                        values.getBoolean(LibraryParameters.INITIALLY_ON) ? LogicState.ONE : LogicState.ZERO,
                        false)));

        registry.register(new ComponentType(
                definition("source.button", "Push Button", SOURCES,
                        "Reads 1 while held down and 0 when released",
                        List.of(LibraryParameters.INVERTED), PortLayouts.source(),
                        List.of("button", "momentary", "input", "push"), MOMENTARY),
                values -> new UserInputBehavior(LogicState.ZERO,
                        values.getBoolean(LibraryParameters.INVERTED))));

        registry.register(new ComponentType(
                definition("source.clock", "Clock", SOURCES,
                        "Free-running square wave, driven entirely by virtual simulation time",
                        List.of(LibraryParameters.FREQUENCY_HZ, LibraryParameters.DUTY_CYCLE_PERCENT,
                                LibraryParameters.INITIALLY_HIGH, LibraryParameters.ENABLED),
                        PortLayouts.source(), List.of("clock", "oscillator", "clk", "timer", "pulse")),
                values -> ClockBehavior.ofFrequency(
                        values.getInt(LibraryParameters.FREQUENCY_HZ),
                        values.getInt(LibraryParameters.DUTY_CYCLE_PERCENT),
                        values.getBoolean(LibraryParameters.INITIALLY_HIGH),
                        values.getBoolean(LibraryParameters.ENABLED))));
    }

    private static void registerDrivers(ComponentRegistry registry) {
        registry.register(ComponentType.of(
                definition("logic.buffer", "Buffer", LOGIC,
                        "Passes its input through unchanged", List.of(), PortLayouts.unary(),
                        List.of("driver", "repeater")),
                UnaryGateBehavior.BUFFER));

        registry.register(ComponentType.of(
                definition("logic.not", "NOT", LOGIC,
                        "Inverter: 0 becomes 1, 1 becomes 0, X stays X", List.of(), PortLayouts.unary(),
                        List.of("inverter", "invert", "negate")),
                UnaryGateBehavior.INVERTER));

        registry.register(new ComponentType(
                definition("logic.tristate", "Tri-State Buffer", LOGIC,
                        "Drives its input onto the net while enabled, and floats otherwise",
                        List.of(LibraryParameters.ACTIVE_LOW), PortLayouts.triState(),
                        List.of("tristate", "three state", "bus", "driver", "z")),
                values -> new TriStateBehavior(false, values.getBoolean(LibraryParameters.ACTIVE_LOW))));

        registry.register(new ComponentType(
                definition("logic.tristate.inverting", "Inverting Tri-State Buffer", LOGIC,
                        "Drives the inverted input onto the net while enabled, and floats otherwise",
                        List.of(LibraryParameters.ACTIVE_LOW), PortLayouts.triState(),
                        List.of("tristate", "inverter", "bus", "driver", "z")),
                values -> new TriStateBehavior(true, values.getBoolean(LibraryParameters.ACTIVE_LOW))));
    }

    private static void registerGates(ComponentRegistry registry) {
        registerGate(registry, "logic.and", "AND", LogicOperation.AND, false,
                "Output is 1 when every input is 1");
        registerGate(registry, "logic.nand", "NAND", LogicOperation.AND, true,
                "Inverted AND: output is 0 only when every input is 1");
        registerGate(registry, "logic.or", "OR", LogicOperation.OR, false,
                "Output is 1 when any input is 1");
        registerGate(registry, "logic.nor", "NOR", LogicOperation.OR, true,
                "Inverted OR: output is 1 only when every input is 0");
        registerGate(registry, "logic.xor", "XOR", LogicOperation.XOR, false,
                "Output is 1 when an odd number of inputs is 1");
        registerGate(registry, "logic.xnor", "XNOR", LogicOperation.XOR, true,
                "Inverted XOR: output is 1 when an even number of inputs is 1");
    }

    private static void registerGate(ComponentRegistry registry, String id, String name,
                                     LogicOperation operation, boolean invert, String description) {
        registry.register(ComponentType.of(
                definition(id, name, LOGIC, description, List.of(LibraryParameters.INPUT_COUNT),
                        PortLayouts.gate(LibraryParameters.INPUT_COUNT), List.of("gate", name.toLowerCase())),
                new NaryGateBehavior(operation, invert)));
    }

    /** {@code Q}/{@code Q'} outputs shared by every latch and flip-flop below. */
    private static List<PortLayouts.PortDef> qOutputs() {
        return List.of(
                new PortLayouts.PortDef("Q", dev.logicforge.logic.BitWidth.ONE, "Stored output"),
                new PortLayouts.PortDef("Q'", dev.logicforge.logic.BitWidth.ONE, "Inverted stored output"));
    }

    private static void registerSequential(ComponentRegistry registry) {
        registry.register(ComponentType.of(
                definition("sequential.sr_latch", "SR Latch", SEQUENTIAL,
                        "Level-sensitive set/reset latch (NOR-based, active-high S/R)",
                        List.of(), PortLayouts.box(List.of(
                                new PortLayouts.PortDef("S", BitWidth.ONE, "Set: forces Q to 1 while high"),
                                new PortLayouts.PortDef("R", BitWidth.ONE, "Reset: forces Q to 0 while high")),
                                qOutputs(), PortLayouts.GATE_WIDTH),
                        List.of("latch", "sr", "set", "reset", "nor")),
                new SrLatchBehavior(false)));

        registry.register(ComponentType.of(
                definition("sequential.sr_latch_nand", "SR Latch (Active-Low)", SEQUENTIAL,
                        "Level-sensitive set/reset latch (NAND-based, active-low S/R)",
                        List.of(), PortLayouts.box(List.of(
                                new PortLayouts.PortDef("S", BitWidth.ONE, "Set: forces Q to 1 while low"),
                                new PortLayouts.PortDef("R", BitWidth.ONE, "Reset: forces Q to 0 while low")),
                                qOutputs(), PortLayouts.GATE_WIDTH),
                        List.of("latch", "sr", "set", "reset", "nand")),
                new SrLatchBehavior(true)));

        registry.register(ComponentType.of(
                definition("sequential.d_latch", "D Latch", SEQUENTIAL,
                        "Level-sensitive latch: Q follows D while EN is 1, holds while EN is 0",
                        List.of(), PortLayouts.box(List.of(
                                new PortLayouts.PortDef("D", BitWidth.ONE, "Data input, sampled while EN is high"),
                                new PortLayouts.PortDef("EN", BitWidth.ONE, "Enable: transparent while high, latched while low")),
                                qOutputs(), PortLayouts.GATE_WIDTH),
                        List.of("latch", "d", "transparent")),
                DLatchBehavior.INSTANCE));

        registry.register(new ComponentType(
                definition("sequential.d_ff", "D Flip-Flop", SEQUENTIAL,
                        "Edge-triggered: Q takes D's value on the configured clock edge",
                        List.of(LibraryParameters.CLOCK_EDGE),
                        PortLayouts.box(List.of(
                                new PortLayouts.PortDef("D", BitWidth.ONE, "Data sampled on the active clock edge"),
                                new PortLayouts.PortDef("CLK", BitWidth.ONE, "Clock input")),
                                qOutputs(), PortLayouts.GATE_WIDTH),
                        List.of("flipflop", "flip-flop", "d", "register bit")),
                values -> new DFlipFlopBehavior(isRisingEdge(values), false)));

        registry.register(new ComponentType(
                definition("sequential.d_ff_sr", "D Flip-Flop (Set/Reset)", SEQUENTIAL,
                        "Edge-triggered D flip-flop with asynchronous SET and RESET",
                        List.of(LibraryParameters.CLOCK_EDGE),
                        PortLayouts.box(List.of(
                                new PortLayouts.PortDef("D", BitWidth.ONE, "Data sampled on the active clock edge"),
                                new PortLayouts.PortDef("CLK", BitWidth.ONE, "Clock input"),
                                new PortLayouts.PortDef("SET", BitWidth.ONE, "Asynchronous set: forces Q to 1 while high"),
                                new PortLayouts.PortDef("RESET", BitWidth.ONE, "Asynchronous reset: forces Q to 0 while high")),
                                qOutputs(), PortLayouts.GATE_WIDTH),
                        List.of("flipflop", "flip-flop", "d", "set", "reset", "async")),
                values -> new DFlipFlopBehavior(isRisingEdge(values), true)));

        registry.register(new ComponentType(
                definition("sequential.jk_ff", "JK Flip-Flop", SEQUENTIAL,
                        "Edge-triggered: hold, set, reset or toggle depending on J and K",
                        List.of(LibraryParameters.CLOCK_EDGE),
                        PortLayouts.box(List.of(
                                new PortLayouts.PortDef("J", BitWidth.ONE, "Set input: J=1,K=0 forces Q to 1"),
                                new PortLayouts.PortDef("K", BitWidth.ONE, "Reset input: J=0,K=1 forces Q to 0; J=K=1 toggles"),
                                new PortLayouts.PortDef("CLK", BitWidth.ONE, "Clock input")),
                                qOutputs(), PortLayouts.GATE_WIDTH),
                        List.of("flipflop", "flip-flop", "jk", "toggle")),
                values -> new JkFlipFlopBehavior(isRisingEdge(values))));

        registry.register(new ComponentType(
                definition("sequential.t_ff", "T Flip-Flop", SEQUENTIAL,
                        "Edge-triggered: toggles Q when T is 1, holds when T is 0",
                        List.of(LibraryParameters.CLOCK_EDGE),
                        PortLayouts.box(List.of(
                                new PortLayouts.PortDef("T", BitWidth.ONE, "Toggle enable: 1 flips Q on the active edge"),
                                new PortLayouts.PortDef("CLK", BitWidth.ONE, "Clock input")),
                                qOutputs(), PortLayouts.GATE_WIDTH),
                        List.of("flipflop", "flip-flop", "t", "toggle", "counter bit")),
                values -> new TFlipFlopBehavior(isRisingEdge(values))));
    }

    private static boolean isRisingEdge(dev.logicforge.circuit.component.ParameterValues values) {
        return values.get(LibraryParameters.CLOCK_EDGE).equals("rising");
    }

    /** Width of the boxy multi-port register/counter symbols. */
    private static final double REGISTER_WIDTH = 72;

    private static void registerRegisters(ComponentRegistry registry) {
        registry.register(new ComponentType(
                definition("memory.register_file", "Register File", MEMORY,
                        "Multi-port register file", List.of(LibraryParameters.WIDTH, LibraryParameters.REGISTER_COUNT),
                        PortLayouts.variableBox(
                                values -> {
                                    int addrBits = dev.logicforge.library.behavior.RegisterFileBehavior.addrBits(values.getInt(LibraryParameters.REGISTER_COUNT));
                                    dev.logicforge.logic.BitWidth addrWidth = dev.logicforge.logic.BitWidth.of(addrBits);
                                    return List.of(
                                        new PortLayouts.DynamicPortDef("RD_ADDR_A", v -> addrWidth, "Address for combinational read port A"),
                                        new PortLayouts.DynamicPortDef("RD_ADDR_B", v -> addrWidth, "Address for combinational read port B"),
                                        new PortLayouts.DynamicPortDef("WR_ADDR", v -> addrWidth, "Register written on the rising clock edge"),
                                        PortLayouts.DynamicPortDef.bus("WR_DATA", LibraryParameters.WIDTH, "Data written to WR_ADDR"),
                                        PortLayouts.DynamicPortDef.fixed("WR_EN", "Write enable sampled on the rising clock edge"),
                                        PortLayouts.DynamicPortDef.fixed("CLK", "Rising-edge write clock")
                                    );
                                },
                                values -> List.of(
                                    PortLayouts.DynamicPortDef.bus("RD_DATA_A", LibraryParameters.WIDTH, "Contents selected by RD_ADDR_A"),
                                    PortLayouts.DynamicPortDef.bus("RD_DATA_B", LibraryParameters.WIDTH, "Contents selected by RD_ADDR_B")
                                ),
                                PortLayouts.GATE_WIDTH),
                        List.of("register file", "rf", "regfile")),
                values -> new dev.logicforge.library.behavior.RegisterFileBehavior(dev.logicforge.logic.BitWidth.of(values.getInt(LibraryParameters.WIDTH)), values.getInt(LibraryParameters.REGISTER_COUNT))));

        registry.register(new ComponentType(
                definition("sequential.register", "Register", SEQUENTIAL,
                        "Parallel-in/parallel-out register: Q loads DATA on the clock edge while LOAD is 1",
                        List.of(LibraryParameters.WIDTH, LibraryParameters.CLOCK_EDGE),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.bus("DATA", LibraryParameters.WIDTH,
                                                "Parallel data input"),
                                        PortLayouts.DynamicPortDef.fixed("CLK", "Clock input"),
                                        PortLayouts.DynamicPortDef.fixed("LOAD",
                                                "While high, Q captures DATA on the active clock edge")),
                                List.of(PortLayouts.DynamicPortDef.bus("Q", LibraryParameters.WIDTH,
                                        "Stored register value")),
                                REGISTER_WIDTH),
                        List.of("register", "pipo", "latch", "storage")),
                values -> new RegisterBehavior(
                        BitWidth.of(values.getInt(LibraryParameters.WIDTH)), isRisingEdge(values), false)));

        registry.register(new ComponentType(
                definition("sequential.flags_register", "Flags Register", SEQUENTIAL,
                        "Latches Z, C, N and V on the clock edge while LOAD is 1, holding a "
                                + "combinational ALU's condition codes for later branch decisions",
                        List.of(LibraryParameters.CLOCK_EDGE),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.fixed("Z", "Zero flag"),
                                        PortLayouts.DynamicPortDef.fixed("C", "Carry flag"),
                                        PortLayouts.DynamicPortDef.fixed("N", "Negative flag"),
                                        PortLayouts.DynamicPortDef.fixed("V", "Overflow flag"),
                                        PortLayouts.DynamicPortDef.fixed("CLK", "Clock input"),
                                        PortLayouts.DynamicPortDef.fixed("LOAD",
                                                "While high, FLAGS captures Z/C/N/V on the active clock edge")),
                                List.of(new PortLayouts.DynamicPortDef("FLAGS", v -> BitWidth.of(4),
                                        "Stored condition codes, 4 bits: bit0=Z, bit1=C, bit2=N, bit3=V")),
                                REGISTER_WIDTH),
                        List.of("flags", "condition codes", "status register", "z", "c", "n", "v")),
                values -> new FlagsRegisterBehavior(isRisingEdge(values))));

        registry.register(new ComponentType(
                definition("sequential.register_reset", "Register (Reset)", SEQUENTIAL,
                        "Parallel register with an asynchronous RESET that clears Q to zero",
                        List.of(LibraryParameters.WIDTH, LibraryParameters.CLOCK_EDGE),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.bus("DATA", LibraryParameters.WIDTH,
                                                "Parallel data input"),
                                        PortLayouts.DynamicPortDef.fixed("CLK", "Clock input"),
                                        PortLayouts.DynamicPortDef.fixed("LOAD",
                                                "While high, Q captures DATA on the active clock edge"),
                                        PortLayouts.DynamicPortDef.fixed("RESET",
                                                "Asynchronous clear: forces Q to zero, independent of CLK")),
                                List.of(PortLayouts.DynamicPortDef.bus("Q", LibraryParameters.WIDTH,
                                        "Stored register value")),
                                REGISTER_WIDTH),
                        List.of("register", "pipo", "reset", "clear", "storage")),
                values -> new RegisterBehavior(
                        BitWidth.of(values.getInt(LibraryParameters.WIDTH)), isRisingEdge(values), true)));

        registry.register(new ComponentType(
                definition("sequential.shift_register", "Shift Register", SEQUENTIAL,
                        "Serial-in/parallel-out: shifts one bit towards the MSB on every clock edge",
                        List.of(LibraryParameters.WIDTH, LibraryParameters.CLOCK_EDGE),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.fixed("SIN", "Serial data input, enters at bit 0"),
                                        PortLayouts.DynamicPortDef.fixed("CLK", "Clock input"),
                                        PortLayouts.DynamicPortDef.fixed("RESET",
                                                "Asynchronous clear: forces Q to zero, independent of CLK")),
                                List.of(PortLayouts.DynamicPortDef.bus("Q", LibraryParameters.WIDTH,
                                                "Current shift register contents"),
                                        PortLayouts.DynamicPortDef.fixed("SOUT",
                                                "Current most significant bit, the next value shifted out")),
                                REGISTER_WIDTH),
                        List.of("shift register", "siso", "sipo", "serial")),
                values -> new ShiftRegisterBehavior(
                        BitWidth.of(values.getInt(LibraryParameters.WIDTH)), isRisingEdge(values))));
    }

    private static void registerCounters(ComponentRegistry registry) {
        registerCounter(registry, "sequential.counter_up", "Up Counter",
                "Counts up by one on every clock edge while ENABLE is 1, wrapping at the top",
                CounterBehavior.Direction.UP,
                List.of(PortLayouts.DynamicPortDef.fixed("CLK", "Clock input"),
                        PortLayouts.DynamicPortDef.fixed("ENABLE", "While high, COUNT advances on the active edge"),
                        PortLayouts.DynamicPortDef.fixed("RESET",
                                "Asynchronous clear: forces COUNT to zero, independent of CLK")),
                List.of("counter", "up", "increment", "binary counter"));

        registerCounter(registry, "sequential.counter_down", "Down Counter",
                "Counts down by one on every clock edge while ENABLE is 1, wrapping at zero",
                CounterBehavior.Direction.DOWN,
                List.of(PortLayouts.DynamicPortDef.fixed("CLK", "Clock input"),
                        PortLayouts.DynamicPortDef.fixed("ENABLE", "While high, COUNT advances on the active edge"),
                        PortLayouts.DynamicPortDef.fixed("RESET",
                                "Asynchronous clear: forces COUNT to zero, independent of CLK")),
                List.of("counter", "down", "decrement", "binary counter"));

        registerCounter(registry, "sequential.counter_updown", "Up/Down Counter",
                "Counts up or down depending on UP_DOWN, on every clock edge while ENABLE is 1",
                CounterBehavior.Direction.SELECTABLE,
                List.of(PortLayouts.DynamicPortDef.fixed("CLK", "Clock input"),
                        PortLayouts.DynamicPortDef.fixed("ENABLE", "While high, COUNT advances on the active edge"),
                        PortLayouts.DynamicPortDef.fixed("RESET",
                                "Asynchronous clear: forces COUNT to zero, independent of CLK"),
                        PortLayouts.DynamicPortDef.fixed("UP_DOWN", "1 counts up, 0 counts down")),
                List.of("counter", "updown", "up/down", "bidirectional", "binary counter"));
    }

    private static void registerCounter(ComponentRegistry registry, String id, String name, String description,
                                        CounterBehavior.Direction direction, List<PortLayouts.DynamicPortDef> inputs,
                                        List<String> keywords) {
        registry.register(new ComponentType(
                definition(id, name, SEQUENTIAL, description,
                        List.of(LibraryParameters.WIDTH, LibraryParameters.CLOCK_EDGE),
                        PortLayouts.dynamicBox(inputs,
                                List.of(PortLayouts.DynamicPortDef.bus("COUNT", LibraryParameters.WIDTH),
                                        PortLayouts.DynamicPortDef.fixed("TC",
                                                "Terminal count: 1 when COUNT is about to wrap on the next edge")),
                                REGISTER_WIDTH),
                        keywords),
                values -> new CounterBehavior(
                        BitWidth.of(values.getInt(LibraryParameters.WIDTH)), isRisingEdge(values), direction)));
    }

    private static void registerSequentialExpansion(ComponentRegistry registry) {
        registry.register(new ComponentType(
                definition("sequential.loadable_counter", "Loadable Counter", SEQUENTIAL,
                        "Counts up by one on every clock edge while ENABLE is 1; LOAD instead "
                                + "captures DATA, taking priority over ENABLE — the shape a program "
                                + "counter needs for jumps and branches",
                        List.of(LibraryParameters.WIDTH, LibraryParameters.CLOCK_EDGE),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.bus("DATA", LibraryParameters.WIDTH,
                                                "Parallel value captured when LOAD is 1"),
                                        PortLayouts.DynamicPortDef.fixed("CLK", "Clock input"),
                                        PortLayouts.DynamicPortDef.fixed("ENABLE",
                                                "While high (and LOAD is 0), COUNT advances on the active edge"),
                                        PortLayouts.DynamicPortDef.fixed("LOAD",
                                                "While high, COUNT captures DATA on the active edge instead of counting"),
                                        PortLayouts.DynamicPortDef.fixed("RESET",
                                                "Asynchronous clear: forces COUNT to zero, independent of CLK")),
                                List.of(PortLayouts.DynamicPortDef.bus("COUNT", LibraryParameters.WIDTH),
                                        PortLayouts.DynamicPortDef.fixed("TC",
                                                "Terminal count: 1 when COUNT is at its maximum value")),
                                REGISTER_WIDTH),
                        List.of("counter", "program counter", "pc", "loadable", "jump")),
                values -> new LoadableCounterBehavior(
                        BitWidth.of(values.getInt(LibraryParameters.WIDTH)), isRisingEdge(values))));

        registry.register(new ComponentType(
                definition("sequential.modulo_counter", "Modulo Counter", SEQUENTIAL,
                        "Counts up by one on every clock edge while ENABLE is 1, wrapping back "
                                + "to zero at an arbitrary modulus rather than at the full bit range",
                        List.of(LibraryParameters.WIDTH, LibraryParameters.MODULUS, LibraryParameters.CLOCK_EDGE),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.fixed("CLK", "Clock input"),
                                        PortLayouts.DynamicPortDef.fixed("ENABLE",
                                                "While high, COUNT advances on the active edge"),
                                        PortLayouts.DynamicPortDef.fixed("RESET",
                                                "Asynchronous clear: forces COUNT to zero, independent of CLK")),
                                List.of(PortLayouts.DynamicPortDef.bus("COUNT", LibraryParameters.WIDTH),
                                        PortLayouts.DynamicPortDef.fixed("TC",
                                                "Terminal count: 1 when COUNT equals modulus - 1")),
                                REGISTER_WIDTH),
                        List.of("counter", "modulo", "wraparound", "bcd", "divide by n")),
                values -> new ModuloCounterBehavior(BitWidth.of(values.getInt(LibraryParameters.WIDTH)),
                        isRisingEdge(values), values.getInt(LibraryParameters.MODULUS))));

        registry.register(new ComponentType(
                definition("sequential.clock_divider", "Clock Divider", SEQUENTIAL,
                        "Divides CLK down by counting its active edges in virtual simulation "
                                + "time: DIVIDE_BY=N produces an average frequency of CLK/N",
                        List.of(LibraryParameters.DIVIDE_BY, LibraryParameters.CLOCK_EDGE),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.fixed("CLK", "Clock input to divide"),
                                        PortLayouts.DynamicPortDef.fixed("RESET",
                                                "Asynchronous clear: forces CLK_OUT low and resets the edge count"),
                                        PortLayouts.DynamicPortDef.fixed("ENABLE",
                                                "While high, CLK edges are counted towards the next toggle")),
                                List.of(PortLayouts.DynamicPortDef.fixed("CLK_OUT",
                                        "Average frequency is CLK divided by DIVIDE_BY")),
                                REGISTER_WIDTH),
                        List.of("clock", "divider", "prescaler", "frequency")),
                values -> new ClockDividerBehavior(isRisingEdge(values), values.getInt(LibraryParameters.DIVIDE_BY))));

        registry.register(new ComponentType(
                definition("sequential.universal_shift_register", "Universal Shift Register", SEQUENTIAL,
                        "Holds, parallel-loads, or shifts either direction depending on MODE, "
                                + "sampled on the active clock edge (0=HOLD, 1=LOAD, 2=SHIFT_LEFT, 3=SHIFT_RIGHT)",
                        List.of(LibraryParameters.WIDTH, LibraryParameters.CLOCK_EDGE),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.bus("PARALLEL_DATA", LibraryParameters.WIDTH,
                                                "Parallel value captured in LOAD mode"),
                                        PortLayouts.DynamicPortDef.fixed("SERIAL_LEFT",
                                                "Bit shifted in at bit 0 in SHIFT_LEFT mode"),
                                        PortLayouts.DynamicPortDef.fixed("SERIAL_RIGHT",
                                                "Bit shifted in at the MSB in SHIFT_RIGHT mode"),
                                        new PortLayouts.DynamicPortDef("MODE", v -> BitWidth.of(2),
                                                "0=HOLD, 1=LOAD, 2=SHIFT_LEFT, 3=SHIFT_RIGHT"),
                                        PortLayouts.DynamicPortDef.fixed("CLK", "Clock input"),
                                        PortLayouts.DynamicPortDef.fixed("RESET",
                                                "Asynchronous clear: forces Q to zero, independent of CLK")),
                                List.of(PortLayouts.DynamicPortDef.bus("Q", LibraryParameters.WIDTH,
                                                "Current register contents"),
                                        PortLayouts.DynamicPortDef.fixed("SERIAL_OUT_LEFT",
                                                "Current MSB — the bit the next SHIFT_LEFT would drop"),
                                        PortLayouts.DynamicPortDef.fixed("SERIAL_OUT_RIGHT",
                                                "Current LSB — the bit the next SHIFT_RIGHT would drop")),
                                REGISTER_WIDTH),
                        List.of("shift register", "universal", "bidirectional", "siso", "piso", "sipo")),
                values -> new UniversalShiftRegisterBehavior(
                        BitWidth.of(values.getInt(LibraryParameters.WIDTH)), isRisingEdge(values))));

        registry.register(new ComponentType(
                definition("sequential.piso", "PISO Shift Register", SEQUENTIAL,
                        "Parallel-in/serial-out: LOAD captures DATA in parallel; otherwise, "
                                + "while SHIFT is 1, shifts one bit towards the LSB every clock edge, "
                                + "pulling SERIAL_IN in at the MSB",
                        List.of(LibraryParameters.WIDTH, LibraryParameters.CLOCK_EDGE),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.bus("DATA", LibraryParameters.WIDTH,
                                                "Parallel value captured when LOAD is 1"),
                                        PortLayouts.DynamicPortDef.fixed("LOAD",
                                                "While high, Q captures DATA on the active edge; takes priority over SHIFT"),
                                        PortLayouts.DynamicPortDef.fixed("SHIFT",
                                                "While high (and LOAD is 0), Q shifts towards the LSB on the active edge"),
                                        PortLayouts.DynamicPortDef.fixed("SERIAL_IN",
                                                "Bit shifted in at the MSB — chain to the previous stage's SERIAL_OUT"),
                                        PortLayouts.DynamicPortDef.fixed("CLK", "Clock input"),
                                        PortLayouts.DynamicPortDef.fixed("RESET",
                                                "Asynchronous clear: forces Q to zero, independent of CLK")),
                                List.of(PortLayouts.DynamicPortDef.bus("Q", LibraryParameters.WIDTH,
                                                "Current register contents"),
                                        PortLayouts.DynamicPortDef.fixed("SERIAL_OUT",
                                                "Current bit 0 — the bit the next shift would drop")),
                                REGISTER_WIDTH),
                        List.of("shift register", "piso", "parallel load", "serializer")),
                values -> new PisoBehavior(BitWidth.of(values.getInt(LibraryParameters.WIDTH)), isRisingEdge(values))));

        registry.register(new ComponentType(
                definition("sequential.sipo", "SIPO Shift Register", SEQUENTIAL,
                        "Serial-in/parallel-out: shifts SIN in at bit 0 towards the MSB on "
                                + "every clock edge, exposing the full register on Q",
                        List.of(LibraryParameters.WIDTH, LibraryParameters.CLOCK_EDGE),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.fixed("SIN", "Serial data input, enters at bit 0"),
                                        PortLayouts.DynamicPortDef.fixed("CLK", "Clock input"),
                                        PortLayouts.DynamicPortDef.fixed("RESET",
                                                "Asynchronous clear: forces Q to zero, independent of CLK")),
                                List.of(PortLayouts.DynamicPortDef.bus("Q", LibraryParameters.WIDTH,
                                                "Current shift register contents"),
                                        PortLayouts.DynamicPortDef.fixed("SOUT",
                                                "Current most significant bit, the next value shifted out")),
                                REGISTER_WIDTH),
                        List.of("shift register", "sipo", "serial to parallel", "deserializer")),
                values -> new ShiftRegisterBehavior(
                        BitWidth.of(values.getInt(LibraryParameters.WIDTH)), isRisingEdge(values))));

        registerRingJohnson(registry, "sequential.ring_counter", "Ring Counter",
                "Walks a single 1 bit around a loop of flip-flops: resets to 0...01 and "
                        + "shifts the MSB back in at bit 0 every clock edge, an n-cycle sequence",
                RingJohnsonCounterBehavior.Kind.RING,
                List.of("ring counter", "shift counter", "one-hot"));

        registerRingJohnson(registry, "sequential.johnson_counter", "Johnson Counter",
                "A twisted ring counter: resets to all zero and shifts the inverted MSB back "
                        + "in at bit 0 every clock edge, a 2n-cycle sequence",
                RingJohnsonCounterBehavior.Kind.JOHNSON,
                List.of("johnson counter", "twisted ring counter", "walking ring"));
    }

    private static void registerRingJohnson(ComponentRegistry registry, String id, String name, String description,
                                            RingJohnsonCounterBehavior.Kind kind, List<String> keywords) {
        registry.register(new ComponentType(
                definition(id, name, SEQUENTIAL, description,
                        List.of(LibraryParameters.WIDTH, LibraryParameters.CLOCK_EDGE),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.fixed("CLK", "Clock input"),
                                        PortLayouts.DynamicPortDef.fixed("RESET",
                                                "Asynchronous clear, independent of CLK")),
                                List.of(PortLayouts.DynamicPortDef.bus("Q", LibraryParameters.WIDTH)),
                                REGISTER_WIDTH),
                        keywords),
                values -> new RingJohnsonCounterBehavior(
                        BitWidth.of(values.getInt(LibraryParameters.WIDTH)), isRisingEdge(values), kind)));
    }

    private static void registerRouting(ComponentRegistry registry) {
        // --- Multiplexers -------------------------------------------------
        registry.register(new ComponentType(
                definition("routing.mux2", "2:1 Multiplexer", ROUTING,
                        "Drives IN0 or IN1 onto OUT depending on SEL",
                        List.of(LibraryParameters.WIDTH),
                        PortLayouts.variableBox(
                                values -> List.of(
                                        PortLayouts.DynamicPortDef.bus("IN0", LibraryParameters.WIDTH, "Selected when SEL is 0"),
                                        PortLayouts.DynamicPortDef.bus("IN1", LibraryParameters.WIDTH, "Selected when SEL is 1"),
                                        PortLayouts.DynamicPortDef.fixed("SEL", "Selects which input reaches OUT")),
                                values -> List.of(PortLayouts.DynamicPortDef.bus("OUT", LibraryParameters.WIDTH, "Selected input")),
                                REGISTER_WIDTH),
                        List.of("mux", "multiplexer", "select", "2:1")),
                values -> new MuxBehavior(busWidth(values), 2)));

        registry.register(new ComponentType(
                definition("routing.mux", "Multiplexer", ROUTING,
                        "Drives the selected input onto OUT; a generic N:1 MUX",
                        List.of(LibraryParameters.WIDTH, LibraryParameters.PORT_COUNT),
                        PortLayouts.variableBox(
                                values -> muxDataPorts(values),
                                values -> List.of(PortLayouts.DynamicPortDef.bus("OUT", LibraryParameters.WIDTH, "Selected input")),
                                REGISTER_WIDTH),
                        List.of("mux", "multiplexer", "select")),
                values -> new MuxBehavior(busWidth(values), values.getInt(LibraryParameters.PORT_COUNT))));

        // --- Demultiplexers -------------------------------------------------
        registry.register(new ComponentType(
                definition("routing.demux2", "1:2 Demultiplexer", ROUTING,
                        "Routes IN onto OUT0 or OUT1 depending on SEL; the other output floats",
                        List.of(LibraryParameters.WIDTH),
                        PortLayouts.variableBox(
                                values -> List.of(
                                        PortLayouts.DynamicPortDef.bus("IN", LibraryParameters.WIDTH, "Data to route"),
                                        PortLayouts.DynamicPortDef.fixed("SEL", "Selects which output receives IN")),
                                values -> List.of(
                                        PortLayouts.DynamicPortDef.bus("OUT0", LibraryParameters.WIDTH, "IN when SEL is 0, otherwise Z"),
                                        PortLayouts.DynamicPortDef.bus("OUT1", LibraryParameters.WIDTH, "IN when SEL is 1, otherwise Z")),
                                REGISTER_WIDTH),
                        List.of("demux", "demultiplexer", "1:2")),
                values -> new DemuxBehavior(busWidth(values), 2)));

        registry.register(new ComponentType(
                definition("routing.demux", "Demultiplexer", ROUTING,
                        "Routes IN onto the selected output; every other output floats (Z)",
                        List.of(LibraryParameters.WIDTH, LibraryParameters.PORT_COUNT),
                        PortLayouts.variableBox(
                                values -> List.of(
                                        PortLayouts.DynamicPortDef.bus("IN", LibraryParameters.WIDTH, "Data to route"),
                                        PortLayouts.DynamicPortDef.fixed("SEL", "Selects which output receives IN")),
                                values -> demuxOutputPorts(values),
                                REGISTER_WIDTH),
                        List.of("demux", "demultiplexer")),
                values -> new DemuxBehavior(busWidth(values), values.getInt(LibraryParameters.PORT_COUNT))));

        // --- Decoder / encoders ---------------------------------------------
        registry.register(new ComponentType(
                definition("routing.decoder", "Decoder", ROUTING,
                        "While ENABLE is 1, drives exactly the output SEL selects high",
                        List.of(LibraryParameters.SELECT_BITS),
                        PortLayouts.variableBox(
                                values -> List.of(
                                        new PortLayouts.DynamicPortDef("SEL",
                                                v -> BitWidth.of(v.getInt(LibraryParameters.SELECT_BITS)),
                                                "Selects which output is driven high"),
                                        PortLayouts.DynamicPortDef.fixed("ENABLE", "While low, every output is low")),
                                values -> decoderOutputPorts(values),
                                REGISTER_WIDTH),
                        List.of("decoder", "demux", "address decoder")),
                values -> new DecoderBehavior(1 << values.getInt(LibraryParameters.SELECT_BITS))));

        registry.register(new ComponentType(
                definition("routing.encoder", "Encoder", ROUTING,
                        "OUT is the index of the single active input; ambiguous otherwise (X)",
                        List.of(LibraryParameters.PORT_COUNT),
                        PortLayouts.variableBox(
                                values -> encoderInputPorts(values),
                                values -> List.of(new PortLayouts.DynamicPortDef("OUT",
                                        v -> BitWidth.of(MuxBehavior.selectWidth(v.getInt(LibraryParameters.PORT_COUNT))),
                                        "Binary index of the single active input")),
                                REGISTER_WIDTH),
                        List.of("encoder", "priority")),
                values -> new EncoderBehavior(values.getInt(LibraryParameters.PORT_COUNT))));

        registry.register(new ComponentType(
                definition("routing.priority_encoder", "Priority Encoder", ROUTING,
                        "OUT is the index of the highest-indexed active input; VALID is 1 if any input is active",
                        List.of(LibraryParameters.PORT_COUNT),
                        PortLayouts.variableBox(
                                values -> encoderInputPorts(values),
                                values -> List.of(
                                        new PortLayouts.DynamicPortDef("OUT",
                                                v -> BitWidth.of(MuxBehavior.selectWidth(v.getInt(LibraryParameters.PORT_COUNT))),
                                                "Binary index of the highest-priority active input"),
                                        PortLayouts.DynamicPortDef.fixed("VALID", "1 if any input is active")),
                                REGISTER_WIDTH),
                        List.of("encoder", "priority encoder")),
                values -> new PriorityEncoderBehavior(values.getInt(LibraryParameters.PORT_COUNT))));

        // --- Comparator -------------------------------------------------------
        registry.register(new ComponentType(
                definition("routing.comparator", "Comparator", ROUTING,
                        "Unsigned magnitude comparison of A and B",
                        List.of(LibraryParameters.WIDTH),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.bus("A", LibraryParameters.WIDTH, "First operand"),
                                        PortLayouts.DynamicPortDef.bus("B", LibraryParameters.WIDTH, "Second operand")),
                                List.of(PortLayouts.DynamicPortDef.fixed("LT", "1 if A < B"),
                                        PortLayouts.DynamicPortDef.fixed("EQ", "1 if A = B"),
                                        PortLayouts.DynamicPortDef.fixed("GT", "1 if A > B")),
                                REGISTER_WIDTH),
                        List.of("comparator", "compare", "lt", "eq", "gt")),
                values -> ComparatorBehavior.INSTANCE));

        registry.register(new ComponentType(
                definition("routing.comparator_signed", "Signed Comparator", ROUTING,
                        "Two's-complement signed comparison of A and B",
                        List.of(LibraryParameters.WIDTH),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.bus("A", LibraryParameters.WIDTH, "First operand"),
                                        PortLayouts.DynamicPortDef.bus("B", LibraryParameters.WIDTH, "Second operand")),
                                List.of(PortLayouts.DynamicPortDef.fixed("LT", "1 if A < B (signed)"),
                                        PortLayouts.DynamicPortDef.fixed("EQ", "1 if A = B"),
                                        PortLayouts.DynamicPortDef.fixed("GT", "1 if A > B (signed)")),
                                REGISTER_WIDTH),
                        List.of("comparator", "compare", "signed", "lt", "eq", "gt")),
                values -> new SignedComparatorBehavior(busWidth(values))));

        // --- Bus utilities ------------------------------------------------
        registry.register(new ComponentType(
                definition("routing.tristate_n", "N-bit Tri-State Buffer", ROUTING,
                        "Drives A onto Y while ENABLE is active; otherwise every bit floats (Z)",
                        List.of(LibraryParameters.WIDTH, LibraryParameters.ACTIVE_LOW),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.bus("A", LibraryParameters.WIDTH, "Data input"),
                                        PortLayouts.DynamicPortDef.fixed("ENABLE", "Drives Y while active")),
                                List.of(PortLayouts.DynamicPortDef.bus("Y", LibraryParameters.WIDTH,
                                        "A while enabled, otherwise high-impedance (Z)")),
                                REGISTER_WIDTH),
                        List.of("tristate", "buffer", "bus", "z")),
                values -> new WideTriStateBehavior(busWidth(values), values.getBoolean(LibraryParameters.ACTIVE_LOW))));

        registry.register(new ComponentType(
                definition("routing.splitter", "Splitter", ROUTING,
                        "Splits a bus into its individual bits, LSB first",
                        List.of(LibraryParameters.WIDTH),
                        PortLayouts.variableBox(
                                values -> List.of(PortLayouts.DynamicPortDef.bus("BUS", LibraryParameters.WIDTH, "Bus to split")),
                                values -> bitPorts(busWidth(values)),
                                REGISTER_WIDTH),
                        List.of("splitter", "bus", "bits")),
                values -> new SplitterBehavior(busWidth(values))));

        registry.register(new ComponentType(
                definition("routing.joiner", "Joiner", ROUTING,
                        "Assembles a bus from individual bits, LSB first",
                        List.of(LibraryParameters.WIDTH),
                        PortLayouts.variableBox(
                                values -> bitPorts(busWidth(values)),
                                values -> List.of(PortLayouts.DynamicPortDef.bus("BUS", LibraryParameters.WIDTH, "Assembled bus")),
                                REGISTER_WIDTH),
                        List.of("joiner", "bus", "bits")),
                values -> new JoinerBehavior(busWidth(values))));

        registry.register(new ComponentType(
                definition("routing.bus_constant", "Bus Constant", ROUTING,
                        "Drives a fixed value onto OUT, entered in hex",
                        List.of(LibraryParameters.WIDTH, LibraryParameters.BUS_CONSTANT_VALUE),
                        PortLayouts.dynamicBox(List.of(),
                                List.of(PortLayouts.DynamicPortDef.bus("OUT", LibraryParameters.WIDTH, "The configured constant value")),
                                REGISTER_WIDTH),
                        List.of("constant", "bus", "value", "hex")),
                values -> new ConstantVectorBehavior(parseBusConstant(
                        values.get(LibraryParameters.BUS_CONSTANT_VALUE), values.getInt(LibraryParameters.WIDTH)))));

        registry.register(ComponentType.of(
                definition("routing.bus_probe", "Bus Probe", ROUTING,
                        "Shows the value of a bus; read its value in the inspector or the logic analyzer",
                        List.of(LibraryParameters.WIDTH),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.bus("IN", LibraryParameters.WIDTH, "Bus to observe")),
                                List.of(), REGISTER_WIDTH),
                        List.of("probe", "bus", "measure", "hex", "debug")),
                SinkBehavior.INSTANCE));
    }

    private static List<PortLayouts.DynamicPortDef> bitPorts(BitWidth width) {
        List<PortLayouts.DynamicPortDef> ports = new ArrayList<>(width.bits());
        for (int i = 0; i < width.bits(); i++) {
            ports.add(PortLayouts.DynamicPortDef.fixed("BIT" + i, "Bit " + i));
        }
        return ports;
    }

    private static List<PortLayouts.DynamicPortDef> muxDataPorts(dev.logicforge.circuit.component.ParameterValues values) {
        int n = values.getInt(LibraryParameters.PORT_COUNT);
        List<PortLayouts.DynamicPortDef> ports = new ArrayList<>(n + 1);
        for (int i = 0; i < n; i++) {
            ports.add(PortLayouts.DynamicPortDef.bus("IN" + i, LibraryParameters.WIDTH, "Data input " + i));
        }
        ports.add(PortLayouts.DynamicPortDef.fixed("SEL", "Selects which input reaches OUT"));
        return ports;
    }

    private static List<PortLayouts.DynamicPortDef> demuxOutputPorts(dev.logicforge.circuit.component.ParameterValues values) {
        int n = values.getInt(LibraryParameters.PORT_COUNT);
        List<PortLayouts.DynamicPortDef> ports = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ports.add(PortLayouts.DynamicPortDef.bus("OUT" + i, LibraryParameters.WIDTH, "IN when SEL = " + i + ", otherwise Z"));
        }
        return ports;
    }

    private static List<PortLayouts.DynamicPortDef> decoderOutputPorts(dev.logicforge.circuit.component.ParameterValues values) {
        int n = 1 << values.getInt(LibraryParameters.SELECT_BITS);
        List<PortLayouts.DynamicPortDef> ports = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ports.add(PortLayouts.DynamicPortDef.fixed("OUT" + i, "High when SEL = " + i + " and ENABLE is 1"));
        }
        return ports;
    }

    private static List<PortLayouts.DynamicPortDef> encoderInputPorts(dev.logicforge.circuit.component.ParameterValues values) {
        int n = values.getInt(LibraryParameters.PORT_COUNT);
        List<PortLayouts.DynamicPortDef> ports = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ports.add(PortLayouts.DynamicPortDef.fixed("IN" + i, "Request input " + i));
        }
        return ports;
    }

    private static BitWidth busWidth(dev.logicforge.circuit.component.ParameterValues values) {
        return BitWidth.of(values.getInt(LibraryParameters.WIDTH));
    }

    private static LogicVector parseBusConstant(String hex, int width) {
        try {
            String trimmed = hex.trim().replaceFirst("^0[xX]", "");
            if (trimmed.isEmpty()) {
                return LogicVector.repeat(LogicState.ZERO, width);
            }
            long value = Long.parseUnsignedLong(trimmed, 16);
            return LogicVector.fromUnsignedLong(value, width);
        } catch (NumberFormatException failure) {
            return LogicVector.repeat(LogicState.ZERO, width);
        }
    }

    private static void registerArithmetic(ComponentRegistry registry) {
        registry.register(new ComponentType(
                definition("arithmetic.alu", "ALU", ARITHMETIC,
                        "Arithmetic Logic Unit", List.of(LibraryParameters.WIDTH),
                        PortLayouts.dynamicBox(List.of(
                                        PortLayouts.DynamicPortDef.bus("A", LibraryParameters.WIDTH, "First operand"),
                                        PortLayouts.DynamicPortDef.bus("B", LibraryParameters.WIDTH, "Second operand"),
                                        new PortLayouts.DynamicPortDef("OP", v -> dev.logicforge.logic.BitWidth.of(4), "Operation selector: ADD=0, SUB=1, AND=2, OR=3, XOR=4"),
                                        PortLayouts.DynamicPortDef.fixed("CIN", "Carry in; use 1 for A−B without borrow")),
                                List.of(PortLayouts.DynamicPortDef.bus("RESULT", LibraryParameters.WIDTH, "Selected arithmetic or logical result"),
                                        PortLayouts.DynamicPortDef.fixed("ZERO", "1 when RESULT is zero"),
                                        PortLayouts.DynamicPortDef.fixed("CARRY", "Arithmetic carry or shifted-out bit"),
                                        PortLayouts.DynamicPortDef.fixed("OVERFLOW", "Signed overflow for ADD and SUB"),
                                        PortLayouts.DynamicPortDef.fixed("NEGATIVE", "Most significant bit of RESULT")),
                                PortLayouts.GATE_WIDTH),
                        List.of("alu", "arithmetic", "math")),
                values -> new dev.logicforge.library.behavior.AluBehavior(dev.logicforge.logic.BitWidth.of(values.getInt(LibraryParameters.WIDTH)))));
                
        registry.register(ComponentType.of(
                definition("arithmetic.half_adder", "Half Adder", ARITHMETIC,
                        "SUM = A XOR B, CARRY = A AND B", List.of(),
                        PortLayouts.box(List.of(
                                        new PortLayouts.PortDef("A", BitWidth.ONE, "First operand"),
                                        new PortLayouts.PortDef("B", BitWidth.ONE, "Second operand")),
                                List.of(new PortLayouts.PortDef("SUM", BitWidth.ONE, "A XOR B"),
                                        new PortLayouts.PortDef("CARRY", BitWidth.ONE, "A AND B")),
                                PortLayouts.GATE_WIDTH),
                        List.of("adder", "half adder", "sum", "carry")),
                HalfAdderBehavior.INSTANCE));

        registry.register(ComponentType.of(
                definition("arithmetic.full_adder", "Full Adder", ARITHMETIC,
                        "SUM = A XOR B XOR CIN, COUT = majority(A, B, CIN)", List.of(),
                        PortLayouts.box(List.of(
                                        new PortLayouts.PortDef("A", BitWidth.ONE, "First operand"),
                                        new PortLayouts.PortDef("B", BitWidth.ONE, "Second operand"),
                                        new PortLayouts.PortDef("CIN", BitWidth.ONE, "Carry in")),
                                List.of(new PortLayouts.PortDef("SUM", BitWidth.ONE, "A XOR B XOR CIN"),
                                        new PortLayouts.PortDef("COUT", BitWidth.ONE, "Carry out")),
                                PortLayouts.GATE_WIDTH),
                        List.of("adder", "full adder", "sum", "carry")),
                FullAdderBehavior.INSTANCE));

        registry.register(new ComponentType(
                definition("arithmetic.adder", "Adder", ARITHMETIC,
                        "N-bit unsigned addition: SUM = A + B + CIN",
                        List.of(LibraryParameters.WIDTH),
                        addSubLayout("SUM", "A + B + CIN", "COUT", "Carry out of the top bit", "CIN", "Carry in"),
                        List.of("adder", "add", "sum", "carry", "alu")),
                values -> new AdderBehavior(busWidth(values))));

        registry.register(new ComponentType(
                definition("arithmetic.subtractor", "Subtractor", ARITHMETIC,
                        "N-bit unsigned subtraction: DIFF = A - B - BIN",
                        List.of(LibraryParameters.WIDTH),
                        addSubLayout("DIFF", "A - B - BIN", "BORROW", "1 if the subtraction goes negative",
                                "BIN", "Borrow in"),
                        List.of("subtractor", "subtract", "diff", "borrow", "alu")),
                values -> new SubtractorBehavior(busWidth(values))));

        registry.register(new ComponentType(
                definition("arithmetic.add_sub", "Add/Sub Unit", ARITHMETIC,
                        "RESULT = A + B when SUB is 0, A - B when SUB is 1",
                        List.of(LibraryParameters.WIDTH),
                        addSubLayout("RESULT", "A + B, or A - B when SUB is 1", "COUT",
                                "Carry (adding) or NOT borrow (subtracting)", "SUB", "0 adds, 1 subtracts"),
                        List.of("add", "subtract", "alu", "add/sub")),
                values -> new AddSubBehavior(busWidth(values))));

        registry.register(new ComponentType(
                definition("arithmetic.incrementer", "Incrementer", ARITHMETIC,
                        "OUT = A + 1, wrapping at the top", List.of(LibraryParameters.WIDTH),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.bus("A", LibraryParameters.WIDTH, "Operand")),
                                List.of(PortLayouts.DynamicPortDef.bus("OUT", LibraryParameters.WIDTH, "A + 1")),
                                REGISTER_WIDTH),
                        List.of("increment", "add one", "counter")),
                values -> new IncrementDecrementBehavior(busWidth(values), true)));

        registry.register(new ComponentType(
                definition("arithmetic.decrementer", "Decrementer", ARITHMETIC,
                        "OUT = A - 1, wrapping at zero", List.of(LibraryParameters.WIDTH),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.bus("A", LibraryParameters.WIDTH, "Operand")),
                                List.of(PortLayouts.DynamicPortDef.bus("OUT", LibraryParameters.WIDTH, "A - 1")),
                                REGISTER_WIDTH),
                        List.of("decrement", "subtract one", "counter")),
                values -> new IncrementDecrementBehavior(busWidth(values), false)));

        registerShift(registry, "arithmetic.shift_left", "Shift Left",
                "Logical shift left: vacated low bits become 0", ShiftBehavior.Direction.LEFT);
        registerShift(registry, "arithmetic.shift_right", "Shift Right",
                "Logical shift right: vacated high bits become 0", ShiftBehavior.Direction.RIGHT_LOGICAL);
        registerShift(registry, "arithmetic.shift_right_arithmetic", "Shift Right (Arithmetic)",
                "Arithmetic shift right: vacated high bits copy the sign bit", ShiftBehavior.Direction.RIGHT_ARITHMETIC);
        registerShift(registry, "arithmetic.rotate_left", "Rotate Left",
                "Rotate left: bits shifted off the top wrap around to the bottom", ShiftBehavior.Direction.ROTATE_LEFT);
        registerShift(registry, "arithmetic.rotate_right", "Rotate Right",
                "Rotate right: bits shifted off the bottom wrap around to the top", ShiftBehavior.Direction.ROTATE_RIGHT);

        registry.register(new ComponentType(
                definition("arithmetic.parity_generator", "Parity Generator", ARITHMETIC,
                        "P makes the total number of ones in A and P together even",
                        List.of(LibraryParameters.WIDTH),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.bus("A", LibraryParameters.WIDTH, "Data")),
                                List.of(PortLayouts.DynamicPortDef.fixed("P", "Even parity bit")),
                                REGISTER_WIDTH),
                        List.of("parity", "even", "checksum")),
                values -> new ParityBehavior(false)));

        registry.register(new ComponentType(
                definition("arithmetic.parity_checker", "Parity Checker", ARITHMETIC,
                        "ERROR is 1 when P does not match the parity A implies",
                        List.of(LibraryParameters.WIDTH),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.bus("A", LibraryParameters.WIDTH, "Data"),
                                        PortLayouts.DynamicPortDef.fixed("P", "Parity bit to check")),
                                List.of(PortLayouts.DynamicPortDef.fixed("ERROR", "1 if the parity bit is wrong")),
                                REGISTER_WIDTH),
                        List.of("parity", "checksum", "error")),
                values -> new ParityBehavior(true)));

        // --- Condition-code / flag helpers ---------------------------------
        registry.register(new ComponentType(
                definition("arithmetic.zero_detector", "Zero Detector", ARITHMETIC,
                        "ZERO is 1 iff every bit of A is 0",
                        List.of(LibraryParameters.WIDTH),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.bus("A", LibraryParameters.WIDTH, "Value to test")),
                                List.of(PortLayouts.DynamicPortDef.fixed("ZERO", "1 if A is all zero bits")),
                                REGISTER_WIDTH),
                        List.of("zero", "flag", "condition code", "z")),
                values -> new ZeroDetectorBehavior(busWidth(values))));

        registry.register(new ComponentType(
                definition("arithmetic.sign_detector", "Sign Detector", ARITHMETIC,
                        "NEGATIVE is A's most significant bit, read as a two's-complement sign",
                        List.of(LibraryParameters.WIDTH),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.bus("A", LibraryParameters.WIDTH, "Value to test")),
                                List.of(PortLayouts.DynamicPortDef.fixed("NEGATIVE", "A's sign bit")),
                                REGISTER_WIDTH),
                        List.of("negative", "sign", "flag", "condition code", "n")),
                values -> new SignDetectorBehavior(busWidth(values))));

        registry.register(new ComponentType(
                definition("arithmetic.overflow_detector", "Overflow Detector", ARITHMETIC,
                        "Signed overflow of an addition or subtraction computed elsewhere: OVERFLOW is 1 "
                                + "when RESULT's sign cannot be correct for signed operands of this width",
                        List.of(LibraryParameters.WIDTH, LibraryParameters.OVERFLOW_OPERATION),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.bus("A", LibraryParameters.WIDTH, "First operand"),
                                        PortLayouts.DynamicPortDef.bus("B", LibraryParameters.WIDTH, "Second operand"),
                                        PortLayouts.DynamicPortDef.bus("RESULT", LibraryParameters.WIDTH,
                                                "Already-computed A+B (or A-B) to check")),
                                List.of(PortLayouts.DynamicPortDef.fixed("OVERFLOW", "1 if the signed result overflowed")),
                                REGISTER_WIDTH),
                        List.of("overflow", "flag", "condition code", "v", "signed")),
                values -> new OverflowDetectorBehavior(busWidth(values),
                        values.get(LibraryParameters.OVERFLOW_OPERATION).equals("sub"))));

        // --- Bit counting ---------------------------------------------------
        registerBitCounter(registry, "arithmetic.leading_zero_count", "Leading Zero Count",
                "How many leading (most-significant) bits of A are 0 before the first 1",
                BitCounterBehavior.Kind.LEADING_ZEROS);
        registerBitCounter(registry, "arithmetic.trailing_zero_count", "Trailing Zero Count",
                "How many trailing (least-significant) bits of A are 0 before the first 1",
                BitCounterBehavior.Kind.TRAILING_ZEROS);
        registerBitCounter(registry, "arithmetic.population_count", "Population Count",
                "How many bits of A are 1 (Hamming weight)",
                BitCounterBehavior.Kind.POPULATION_COUNT);
    }

    private static void registerBitCounter(ComponentRegistry registry, String id, String name, String description,
                                           BitCounterBehavior.Kind kind) {
        registry.register(new ComponentType(
                definition(id, name, ARITHMETIC, description, List.of(LibraryParameters.WIDTH),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.bus("A", LibraryParameters.WIDTH, "Value to count")),
                                List.of(new PortLayouts.DynamicPortDef("COUNT",
                                        v -> BitWidth.of(BitCounterBehavior.countWidth(v.getInt(LibraryParameters.WIDTH))),
                                        "The resulting count, from 0 up to and including the width of A")),
                                REGISTER_WIDTH),
                        List.of("count", "zeros", "ones", "population", "hamming weight")),
                values -> new BitCounterBehavior(busWidth(values), kind)));
    }

    private static void registerShift(ComponentRegistry registry, String id, String name, String description,
                                      ShiftBehavior.Direction direction) {
        registry.register(new ComponentType(
                definition(id, name, ARITHMETIC, description, List.of(LibraryParameters.WIDTH),
                        PortLayouts.dynamicBox(
                                List.of(PortLayouts.DynamicPortDef.bus("A", LibraryParameters.WIDTH, "Value to shift"),
                                        new PortLayouts.DynamicPortDef("SHIFT",
                                                v -> BitWidth.of(ShiftBehavior.shiftAmountWidth(v.getInt(LibraryParameters.WIDTH))),
                                                "How many positions to shift")),
                                List.of(PortLayouts.DynamicPortDef.bus("OUT", LibraryParameters.WIDTH, "Shifted result")),
                                REGISTER_WIDTH),
                        List.of("shift", "shifter")),
                values -> new ShiftBehavior(busWidth(values), direction)));
    }

    private static PortLayout addSubLayout(String resultName, String resultDescription, String carryName,
                                           String carryDescription, String cinName, String cinDescription) {
        return PortLayouts.dynamicBox(
                List.of(PortLayouts.DynamicPortDef.bus("A", LibraryParameters.WIDTH, "First operand"),
                        PortLayouts.DynamicPortDef.bus("B", LibraryParameters.WIDTH, "Second operand"),
                        PortLayouts.DynamicPortDef.fixed(cinName, cinDescription)),
                List.of(PortLayouts.DynamicPortDef.bus(resultName, LibraryParameters.WIDTH, resultDescription),
                        PortLayouts.DynamicPortDef.fixed(carryName, carryDescription)),
                REGISTER_WIDTH);
    }

    private static void registerMemory(ComponentRegistry registry) {
        registry.register(new ComponentType(
                definition("memory.rom", "ROM", MEMORY,
                        "Read-only memory: while ENABLE is 1, drives contents[ADDRESS] onto DATA",
                        List.of(LibraryParameters.ADDRESS_WIDTH, LibraryParameters.WIDTH,
                                LibraryParameters.ROM_CONTENTS),
                        PortLayouts.dynamicBox(
                                List.of(new PortLayouts.DynamicPortDef("ADDRESS",
                                                v -> BitWidth.of(v.getInt(LibraryParameters.ADDRESS_WIDTH)),
                                                "Word address"),
                                        PortLayouts.DynamicPortDef.fixed("ENABLE", "While low, DATA floats (Z)")),
                                List.of(PortLayouts.DynamicPortDef.bus("DATA", LibraryParameters.WIDTH,
                                        "The word stored at ADDRESS")),
                                REGISTER_WIDTH),
                        List.of("rom", "memory", "read-only", "lookup table")),
                values -> new RomBehavior(busWidth(values), parseMemoryContents(
                        values.get(LibraryParameters.ROM_CONTENTS),
                        1 << values.getInt(LibraryParameters.ADDRESS_WIDTH),
                        values.getInt(LibraryParameters.WIDTH)))));

        registry.register(new ComponentType(
                definition("memory.ram", "RAM", MEMORY,
                        "Read/write memory: reads while CS=1,WE=0,OE=1; writes while CS=1,WE=1; "
                                + "otherwise DATA floats",
                        List.of(LibraryParameters.ADDRESS_WIDTH, LibraryParameters.WIDTH),
                        PortLayouts.dynamicBoxWithInout(
                                List.of(new PortLayouts.DynamicPortDef("ADDRESS",
                                                v -> BitWidth.of(v.getInt(LibraryParameters.ADDRESS_WIDTH)),
                                                "Word address"),
                                        PortLayouts.DynamicPortDef.fixed("WE", "Write enable: 1 stores DATA at ADDRESS"),
                                        PortLayouts.DynamicPortDef.fixed("OE", "Output enable: 1 allows RAM to drive DATA"),
                                        PortLayouts.DynamicPortDef.fixed("CS", "Chip select: 0 floats DATA regardless of WE/OE")),
                                List.of(PortLayouts.DynamicPortDef.bus("DATA", LibraryParameters.WIDTH,
                                        "Bidirectional data bus")),
                                List.of(), REGISTER_WIDTH),
                        List.of("ram", "memory", "read-write", "storage")),
                values -> new RamBehavior(
                        BitWidth.of(values.getInt(LibraryParameters.ADDRESS_WIDTH)), busWidth(values))));
    }

    private static void registerHierarchy(ComponentRegistry registry) {
        registry.register(ComponentType.of(
                definition(SubcircuitSupport.INPUT_DEFINITION_ID, "Hierarchy Input", HIERARCHY,
                        "Signal enters this child circuit from its parent",
                        List.of(SubcircuitSupport.INTERFACE_NAME, SubcircuitSupport.INTERFACE_WIDTH),
                        PortLayouts.dynamicBox(List.of(),
                                List.of(new PortLayouts.DynamicPortDef("OUT",
                                        values -> BitWidth.of(values.getInt(SubcircuitSupport.INTERFACE_WIDTH)),
                                        "Signal entering this child circuit")), REGISTER_WIDTH),
                        List.of("subcircuit", "interface", "input")),
                context -> { }));
        registry.register(ComponentType.of(
                definition(SubcircuitSupport.OUTPUT_DEFINITION_ID, "Hierarchy Output", HIERARCHY,
                        "Signal leaves this child circuit for its parent",
                        List.of(SubcircuitSupport.INTERFACE_NAME, SubcircuitSupport.INTERFACE_WIDTH),
                        PortLayouts.dynamicBox(
                                List.of(new PortLayouts.DynamicPortDef("IN",
                                        values -> BitWidth.of(values.getInt(SubcircuitSupport.INTERFACE_WIDTH)),
                                        "Signal leaving this child circuit")),
                                List.of(), REGISTER_WIDTH),
                        List.of("subcircuit", "interface", "output")),
                context -> { }));
        registry.register(ComponentType.of(
                definition(SubcircuitSupport.INOUT_DEFINITION_ID, "Hierarchy InOut", HIERARCHY,
                        "Bidirectional signal shared between this child circuit and its parent",
                        List.of(SubcircuitSupport.INTERFACE_NAME, SubcircuitSupport.INTERFACE_WIDTH),
                        PortLayouts.dynamicBoxWithInout(List.of(),
                                List.of(new PortLayouts.DynamicPortDef("BUS",
                                        values -> BitWidth.of(values.getInt(SubcircuitSupport.INTERFACE_WIDTH)),
                                        "Bidirectional child/parent interface")),
                                List.of(), REGISTER_WIDTH),
                        List.of("subcircuit", "interface", "inout", "bidirectional")),
                context -> { }));
    }

    /** Parses comma-separated hex words into {@code wordCount} vectors; blank/bad entries default to 0. */
    private static LogicVector[] parseMemoryContents(String csv, int wordCount, int dataWidth) {
        LogicVector[] words = new LogicVector[wordCount];
        java.util.Arrays.fill(words, LogicVector.repeat(LogicState.ZERO, dataWidth));
        if (csv == null || csv.isBlank()) {
            return words;
        }
        String[] tokens = csv.split(",");
        for (int i = 0; i < tokens.length && i < wordCount; i++) {
            String token = tokens[i].trim();
            if (token.isEmpty()) {
                continue;
            }
            try {
                long value = Long.parseUnsignedLong(token.replaceFirst("^0[xX]", ""), 16);
                words[i] = LogicVector.fromUnsignedLong(value, dataWidth);
            } catch (NumberFormatException ignored) {
                // Leave that word at its zero default rather than failing the whole ROM.
            }
        }
        return words;
    }

    private static void registerOutputs(ComponentRegistry registry) {
        registry.register(ComponentType.of(
                definition("output.led", "LED", OUTPUTS,
                        "Lights up while its input is 1 and shows X and Z distinctly",
                        List.of(LibraryParameters.LED_COLOR), PortLayouts.sink(),
                        List.of("lamp", "light", "output", "indicator")),
                SinkBehavior.INSTANCE));

        registry.register(ComponentType.of(
                definition("output.probe", "Logic Probe", OUTPUTS,
                        "Shows the value of a net as 0, 1, X or Z", List.of(), PortLayouts.sink(),
                        List.of("probe", "measure", "debug", "value")),
                SinkBehavior.INSTANCE));

        registry.register(ComponentType.of(
                definition("output.pin", "Output Pin", OUTPUTS,
                        "A named output of the circuit", List.of(), PortLayouts.sink(),
                        List.of("pin", "port", "output")),
                SinkBehavior.INSTANCE));
    }

    private static LibraryDefinition definition(String id, String displayName, ComponentCategory category,
                                                String description, List<ParameterSpec<?>> parameters,
                                                PortLayout layout, List<String> keywords) {
        return new LibraryDefinition(id, displayName, category, description, parameters, layout, keywords, InputInteraction.NONE);
    }

    private static LibraryDefinition definition(String id, String displayName, ComponentCategory category,
                                                String description, List<ParameterSpec<?>> parameters,
                                                PortLayout layout, List<String> keywords, InputInteraction inputInteraction) {
        return new LibraryDefinition(id, displayName, category, description, parameters, layout, keywords, inputInteraction);
    }
}
