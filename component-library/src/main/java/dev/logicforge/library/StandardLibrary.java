package dev.logicforge.library;

import static dev.logicforge.circuit.component.ComponentCategory.LOGIC;
import static dev.logicforge.circuit.component.ComponentCategory.OUTPUTS;
import static dev.logicforge.circuit.component.ComponentCategory.SEQUENTIAL;
import static dev.logicforge.circuit.component.ComponentCategory.SOURCES;
import static dev.logicforge.circuit.component.InputInteraction.MOMENTARY;
import static dev.logicforge.circuit.component.InputInteraction.TOGGLE;

import dev.logicforge.circuit.component.InputInteraction;

import dev.logicforge.circuit.component.ComponentCategory;
import dev.logicforge.circuit.component.ParameterSpec;
import dev.logicforge.library.behavior.ClockBehavior;
import dev.logicforge.library.behavior.ConstantBehavior;
import dev.logicforge.library.behavior.DFlipFlopBehavior;
import dev.logicforge.library.behavior.DLatchBehavior;
import dev.logicforge.library.behavior.JkFlipFlopBehavior;
import dev.logicforge.library.behavior.NaryGateBehavior;
import dev.logicforge.library.behavior.SinkBehavior;
import dev.logicforge.library.behavior.SrLatchBehavior;
import dev.logicforge.library.behavior.TFlipFlopBehavior;
import dev.logicforge.library.behavior.TriStateBehavior;
import dev.logicforge.library.behavior.UnaryGateBehavior;
import dev.logicforge.library.behavior.UserInputBehavior;
import dev.logicforge.logic.LogicOperation;
import dev.logicforge.logic.LogicState;
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

    private static void registerSequential(ComponentRegistry registry) {
        registry.register(ComponentType.of(
                definition("sequential.sr_latch", "SR Latch", SEQUENTIAL,
                        "Level-sensitive set/reset latch (NOR-based, active-high S/R)",
                        List.of(), PortLayouts.box(List.of("S", "R"), List.of("Q", "Q'")),
                        List.of("latch", "sr", "set", "reset", "nor")),
                new SrLatchBehavior(false)));

        registry.register(ComponentType.of(
                definition("sequential.sr_latch_nand", "SR Latch (Active-Low)", SEQUENTIAL,
                        "Level-sensitive set/reset latch (NAND-based, active-low S/R)",
                        List.of(), PortLayouts.box(List.of("S", "R"), List.of("Q", "Q'")),
                        List.of("latch", "sr", "set", "reset", "nand")),
                new SrLatchBehavior(true)));

        registry.register(ComponentType.of(
                definition("sequential.d_latch", "D Latch", SEQUENTIAL,
                        "Level-sensitive latch: Q follows D while EN is 1, holds while EN is 0",
                        List.of(), PortLayouts.box(List.of("D", "EN"), List.of("Q", "Q'")),
                        List.of("latch", "d", "transparent")),
                DLatchBehavior.INSTANCE));

        registry.register(new ComponentType(
                definition("sequential.d_ff", "D Flip-Flop", SEQUENTIAL,
                        "Edge-triggered: Q takes D's value on the configured clock edge",
                        List.of(LibraryParameters.CLOCK_EDGE),
                        PortLayouts.box(List.of("D", "CLK"), List.of("Q", "Q'")),
                        List.of("flipflop", "flip-flop", "d", "register bit")),
                values -> new DFlipFlopBehavior(isRisingEdge(values), false)));

        registry.register(new ComponentType(
                definition("sequential.d_ff_sr", "D Flip-Flop (Set/Reset)", SEQUENTIAL,
                        "Edge-triggered D flip-flop with asynchronous SET and RESET",
                        List.of(LibraryParameters.CLOCK_EDGE),
                        PortLayouts.box(List.of("D", "CLK", "SET", "RESET"), List.of("Q", "Q'")),
                        List.of("flipflop", "flip-flop", "d", "set", "reset", "async")),
                values -> new DFlipFlopBehavior(isRisingEdge(values), true)));

        registry.register(new ComponentType(
                definition("sequential.jk_ff", "JK Flip-Flop", SEQUENTIAL,
                        "Edge-triggered: hold, set, reset or toggle depending on J and K",
                        List.of(LibraryParameters.CLOCK_EDGE),
                        PortLayouts.box(List.of("J", "K", "CLK"), List.of("Q", "Q'")),
                        List.of("flipflop", "flip-flop", "jk", "toggle")),
                values -> new JkFlipFlopBehavior(isRisingEdge(values))));

        registry.register(new ComponentType(
                definition("sequential.t_ff", "T Flip-Flop", SEQUENTIAL,
                        "Edge-triggered: toggles Q when T is 1, holds when T is 0",
                        List.of(LibraryParameters.CLOCK_EDGE),
                        PortLayouts.box(List.of("T", "CLK"), List.of("Q", "Q'")),
                        List.of("flipflop", "flip-flop", "t", "toggle", "counter bit")),
                values -> new TFlipFlopBehavior(isRisingEdge(values))));
    }

    private static boolean isRisingEdge(dev.logicforge.circuit.component.ParameterValues values) {
        return values.get(LibraryParameters.CLOCK_EDGE).equals("rising");
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
