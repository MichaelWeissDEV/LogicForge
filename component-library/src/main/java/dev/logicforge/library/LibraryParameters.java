package dev.logicforge.library;

import dev.logicforge.circuit.component.ParameterSpec;
import java.util.List;

/** The configurable properties shared by the built-in components. */
public final class LibraryParameters {

    /** How many inputs a multi-input gate has. */
    public static final ParameterSpec.IntegerParameter INPUT_COUNT =
            new ParameterSpec.IntegerParameter("inputs", "Input Count", 2, 2, 16);

    /** The value a toggle switch returns to when the simulation is reset. */
    public static final ParameterSpec.BooleanParameter INITIALLY_ON =
            new ParameterSpec.BooleanParameter("initialOn", "Initially On", false);

    /** Makes a push button read 1 while released and 0 while pressed. */
    public static final ParameterSpec.BooleanParameter INVERTED =
            new ParameterSpec.BooleanParameter("inverted", "Inverted", false);

    /** Makes a tri-state buffer drive while its ENABLE input is 0. */
    public static final ParameterSpec.BooleanParameter ACTIVE_LOW =
            new ParameterSpec.BooleanParameter("activeLow", "Active Low Enable", false);

    /** Purely visual: which colour an LED lights up in. */
    public static final ParameterSpec.EnumParameter LED_COLOR =
            new ParameterSpec.EnumParameter("color", "Colour", "green",
                    List.of("green", "red", "amber", "blue"));

    /** Clock frequency in Hz; the period follows as one second divided by this value. */
    public static final ParameterSpec.IntegerParameter FREQUENCY_HZ =
            new ParameterSpec.IntegerParameter("frequencyHz", "Frequency (Hz)", 1, 1, 50_000_000);

    /** Percentage of the clock's period spent at the high level. */
    public static final ParameterSpec.IntegerParameter DUTY_CYCLE_PERCENT =
            new ParameterSpec.IntegerParameter("dutyCycle", "Duty Cycle (%)", 50, 1, 99);

    /** The level a clock starts at after reset. */
    public static final ParameterSpec.BooleanParameter INITIALLY_HIGH =
            new ParameterSpec.BooleanParameter("initiallyHigh", "Initially High", false);

    /** Whether a clock actually runs, or sits at its initial level like a constant. */
    public static final ParameterSpec.BooleanParameter ENABLED =
            new ParameterSpec.BooleanParameter("enabled", "Enabled", true);

    /** Which clock transition an edge-triggered component reacts to. */
    public static final ParameterSpec.EnumParameter CLOCK_EDGE =
            new ParameterSpec.EnumParameter("edge", "Clock Edge", "rising", List.of("rising", "falling"));

    /** Which operation an Overflow Detector's already-computed RESULT came from. */
    public static final ParameterSpec.EnumParameter OVERFLOW_OPERATION =
            new ParameterSpec.EnumParameter("operation", "Operation", "add", List.of("add", "sub"));

    /** Bit width of a bus-shaped component: registers, counters, buses, memories. */
    public static final ParameterSpec.IntegerParameter WIDTH =
            new ParameterSpec.IntegerParameter("width", "Width", 8, 1, 64);

    /** How many data inputs a MUX/encoder has, or data outputs a DEMUX has. */
    public static final ParameterSpec.IntegerParameter PORT_COUNT =
            new ParameterSpec.IntegerParameter("ports", "Port Count", 4, 2, 16);

    /** How many address bits a decoder reads; it drives 2^selectBits outputs. */
    public static final ParameterSpec.IntegerParameter SELECT_BITS =
            new ParameterSpec.IntegerParameter("selectBits", "Select Bits", 2, 1, 6);

    /** The value a Bus Constant drives, entered in hex. */
    public static final ParameterSpec.StringParameter BUS_CONSTANT_VALUE =
            new ParameterSpec.StringParameter("value", "Value (hex)", "0");

    /** Address bus width of a memory: 2^addressWidth addressable words. */
    public static final ParameterSpec.IntegerParameter ADDRESS_WIDTH =
            new ParameterSpec.IntegerParameter("addressWidth", "Address Width", 8, 1, 16);

    /** Whether an asynchronous SRAM package also requires an active-high second select. */
    public static final ParameterSpec.BooleanParameter DUAL_CHIP_SELECT =
            new ParameterSpec.BooleanParameter("dualChipSelect", "Second Chip Select", false);

    /** A ROM's contents: comma-separated hex words, one per address, MSB word first is not
     *  implied — index 0 is the first entry. Missing or unparsable entries default to 0. */
    public static final ParameterSpec.StringParameter ROM_CONTENTS =
            new ParameterSpec.StringParameter("contents", "Contents (hex, comma-separated)", "");

    /** How many registers a register file contains. */
    public static final ParameterSpec.IntegerParameter REGISTER_COUNT =
            new ParameterSpec.IntegerParameter("registerCount", "Register Count", 8, 2, 32);

    /** Width of the low-order input to a bus concatenator. */
    public static final ParameterSpec.IntegerParameter LOW_WIDTH =
            new ParameterSpec.IntegerParameter("lowWidth", "Low Width", 8, 1, 32);

    /** Width of the high-order input to a bus concatenator. */
    public static final ParameterSpec.IntegerParameter HIGH_WIDTH =
            new ParameterSpec.IntegerParameter("highWidth", "High Width", 8, 1, 32);

    /** Width of a bus utility's input independently of its output width. */
    public static final ParameterSpec.IntegerParameter INPUT_WIDTH =
            new ParameterSpec.IntegerParameter("inputWidth", "Input Width", 16, 1, 64);

    /** Width of a bus utility's output independently of its input width. */
    public static final ParameterSpec.IntegerParameter OUTPUT_WIDTH =
            new ParameterSpec.IntegerParameter("outputWidth", "Output Width", 8, 1, 64);

    /** Least-significant source bit selected by a bus slice. */
    public static final ParameterSpec.IntegerParameter SLICE_LSB =
            new ParameterSpec.IntegerParameter("sliceLsb", "Slice LSB", 0, 0, 63);

    /** Address decoder base and mask, entered in hexadecimal. */
    public static final ParameterSpec.StringParameter ADDRESS_BASE =
            new ParameterSpec.StringParameter("base", "Base (hex)", "2000");
    public static final ParameterSpec.StringParameter ADDRESS_MASK =
            new ParameterSpec.StringParameter("mask", "Mask (hex)", "f000");

    /** The wrap point of a modulo counter: COUNT cycles through 0..modulus-1. */
    public static final ParameterSpec.IntegerParameter MODULUS =
            new ParameterSpec.IntegerParameter("modulus", "Modulus", 10, 2, 1 << 24);

    /** Output frequency divisor; an output period spans this many active input edges. */
    public static final ParameterSpec.IntegerParameter DIVIDE_BY =
            new ParameterSpec.IntegerParameter("divideBy", "Divide By", 2, 2, 1 << 24);

    /** The value a loadable counter's COUNT returns to when RESET is asserted, in hex. */
    public static final ParameterSpec.StringParameter RESET_VALUE =
            new ParameterSpec.StringParameter("resetValue", "Reset Value (hex)", "0");

    /** Maximum retained characters for the character-output debug buffer. */
    public static final ParameterSpec.IntegerParameter BUFFER_CAPACITY =
            new ParameterSpec.IntegerParameter("bufferCapacity", "Buffer Capacity", 256, 1, 65536);

    /** Entry count of bounded FIFO and stack components. */
    public static final ParameterSpec.IntegerParameter DEPTH =
            new ParameterSpec.IntegerParameter("depth", "Depth", 16, 2, 4096);

    private LibraryParameters() {
    }
}
