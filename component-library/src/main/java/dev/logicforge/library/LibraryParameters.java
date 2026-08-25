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

    /** A ROM's contents: comma-separated hex words, one per address, MSB word first is not
     *  implied — index 0 is the first entry. Missing or unparsable entries default to 0. */
    public static final ParameterSpec.StringParameter ROM_CONTENTS =
            new ParameterSpec.StringParameter("contents", "Contents (hex, comma-separated)", "");

    private LibraryParameters() {
    }
}
