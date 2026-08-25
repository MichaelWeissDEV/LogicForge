package dev.logicforge.processor.mos6502.transistor;

import java.util.List;

/** Physical 40-pin DIP metadata for the original MOS Technology MCS6502. */
public final class Mos6502Package {
    public enum Direction { INPUT, OUTPUT, BIDIRECTIONAL, POWER, GROUND, NC }

    public record Pin(int number, String name, Direction direction, boolean activeLow) { }

    private static final List<Pin> DIP40 = List.of(
            new Pin(1,  "VSS", Direction.GROUND, false),
            new Pin(2,  "RDY", Direction.INPUT, false),
            new Pin(3,  "PHI1_OUT", Direction.OUTPUT, false),
            new Pin(4,  "IRQ", Direction.INPUT, true),
            new Pin(5,  "NC1", Direction.NC, false),
            new Pin(6,  "NMI", Direction.INPUT, true),
            new Pin(7,  "SYNC", Direction.OUTPUT, false),
            new Pin(8,  "VCC", Direction.POWER, false),
            new Pin(9,  "A0", Direction.OUTPUT, false),
            new Pin(10, "A1", Direction.OUTPUT, false),
            new Pin(11, "A2", Direction.OUTPUT, false),
            new Pin(12, "A3", Direction.OUTPUT, false),
            new Pin(13, "A4", Direction.OUTPUT, false),
            new Pin(14, "A5", Direction.OUTPUT, false),
            new Pin(15, "A6", Direction.OUTPUT, false),
            new Pin(16, "A7", Direction.OUTPUT, false),
            new Pin(17, "A8", Direction.OUTPUT, false),
            new Pin(18, "A9", Direction.OUTPUT, false),
            new Pin(19, "A10", Direction.OUTPUT, false),
            new Pin(20, "A11", Direction.OUTPUT, false),
            new Pin(21, "VSS", Direction.GROUND, false),
            new Pin(22, "A12", Direction.OUTPUT, false),
            new Pin(23, "A13", Direction.OUTPUT, false),
            new Pin(24, "A14", Direction.OUTPUT, false),
            new Pin(25, "A15", Direction.OUTPUT, false),
            new Pin(26, "D7", Direction.BIDIRECTIONAL, false),
            new Pin(27, "D6", Direction.BIDIRECTIONAL, false),
            new Pin(28, "D5", Direction.BIDIRECTIONAL, false),
            new Pin(29, "D4", Direction.BIDIRECTIONAL, false),
            new Pin(30, "D3", Direction.BIDIRECTIONAL, false),
            new Pin(31, "D2", Direction.BIDIRECTIONAL, false),
            new Pin(32, "D1", Direction.BIDIRECTIONAL, false),
            new Pin(33, "D0", Direction.BIDIRECTIONAL, false),
            new Pin(34, "RW", Direction.OUTPUT, false),
            new Pin(35, "NC2", Direction.NC, false),
            new Pin(36, "NC3", Direction.NC, false),
            new Pin(37, "PHI0_IN", Direction.INPUT, false),
            new Pin(38, "SO", Direction.INPUT, true),
            new Pin(39, "PHI2_OUT", Direction.OUTPUT, false),
            new Pin(40, "RES", Direction.INPUT, true));

    private Mos6502Package() { }

    public static List<Pin> dip40() {
        return DIP40;
    }
}
