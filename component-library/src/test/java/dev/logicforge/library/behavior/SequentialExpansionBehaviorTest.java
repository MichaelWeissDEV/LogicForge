package dev.logicforge.library.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.CompiledCircuit;
import dev.logicforge.simulation.Simulation;
import org.junit.jupiter.api.Test;

/** Focused smoke tests for the P1 sequential/CPU-datapath component batch. */
class SequentialExpansionBehaviorTest {

    private static final BitWidth WIDTH = BitWidth.of(4);
    private static final int[] NONE = new int[0];

    @Test
    void loadableCounterLoadsInsteadOfCountingWhenLoadIsHigh() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int data = builder.addNet(WIDTH);
        int clk = builder.addNet(BitWidth.ONE);
        int enable = builder.addNet(BitWidth.ONE);
        int load = builder.addNet(BitWidth.ONE);
        int reset = builder.addNet(BitWidth.ONE);
        int count = builder.addNet(WIDTH);
        int tc = builder.addNet(BitWidth.ONE);
        int dataSrc = builder.addComponent("test.data", "D", new BusSource(WIDTH), NONE, new int[]{data});
        int clkSrc = builder.addComponent("test.clk", "CLK", new BusSource(BitWidth.ONE), NONE, new int[]{clk});
        int enableSrc = builder.addComponent("test.en", "EN", new BusSource(BitWidth.ONE), NONE, new int[]{enable});
        int loadSrc = builder.addComponent("test.load", "LOAD", new BusSource(BitWidth.ONE), NONE, new int[]{load});
        builder.addComponent("test.reset", "RST", new BusSource(BitWidth.ONE), NONE, new int[]{reset});
        builder.addComponent("sequential.loadable_counter", "PC", new LoadableCounterBehavior(WIDTH, true),
                new int[]{data, clk, enable, load, reset}, new int[]{count, tc});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(enableSrc, LogicVector.ONE);
        simulation.setInput(clkSrc, LogicVector.ZERO);
        simulation.setInput(clkSrc, LogicVector.ONE);
        assertEquals(LogicVector.fromUnsignedLong(1, 4), simulation.readNet(count), "plain count advances");

        simulation.setInput(dataSrc, LogicVector.fromUnsignedLong(9, 4));
        simulation.setInput(loadSrc, LogicVector.ONE);
        simulation.setInput(clkSrc, LogicVector.ZERO);
        simulation.setInput(clkSrc, LogicVector.ONE);
        assertEquals(LogicVector.fromUnsignedLong(9, 4), simulation.readNet(count), "LOAD wins over ENABLE");
    }

    @Test
    void moduloCounterWrapsAtModulusNotAtTheBitRange() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int clk = builder.addNet(BitWidth.ONE);
        int enable = builder.addNet(BitWidth.ONE);
        int reset = builder.addNet(BitWidth.ONE);
        int count = builder.addNet(WIDTH);
        int tc = builder.addNet(BitWidth.ONE);
        int clkSrc = builder.addComponent("test.clk", "CLK", new BusSource(BitWidth.ONE), NONE, new int[]{clk});
        int enableSrc = builder.addComponent("test.en", "EN", new BusSource(BitWidth.ONE), NONE, new int[]{enable});
        builder.addComponent("test.reset", "RST", new BusSource(BitWidth.ONE), NONE, new int[]{reset});
        builder.addComponent("sequential.modulo_counter", "M", new ModuloCounterBehavior(WIDTH, true, 5),
                new int[]{clk, enable, reset}, new int[]{count, tc});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(enableSrc, LogicVector.ONE);
        for (int i = 0; i < 4; i++) {
            simulation.setInput(clkSrc, LogicVector.ZERO);
            simulation.setInput(clkSrc, LogicVector.ONE);
        }
        assertEquals(LogicVector.fromUnsignedLong(4, 4), simulation.readNet(count));
        assertEquals(LogicVector.ONE, simulation.readNet(tc), "4 is modulus - 1");

        simulation.setInput(clkSrc, LogicVector.ZERO);
        simulation.setInput(clkSrc, LogicVector.ONE);
        assertEquals(LogicVector.fromUnsignedLong(0, 4), simulation.readNet(count), "wraps at modulus, not at 15");
    }

    @Test
    void clockDividerDivideByThreeCompletesOnePeriodInThreeEdges() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int clk = builder.addNet(BitWidth.ONE);
        int reset = builder.addNet(BitWidth.ONE);
        int enable = builder.addNet(BitWidth.ONE);
        int clkOut = builder.addNet(BitWidth.ONE);
        int clkSrc = builder.addComponent("test.clk", "CLK", new BusSource(BitWidth.ONE), NONE, new int[]{clk});
        int enableSrc = builder.addComponent("test.en", "EN", new BusSource(BitWidth.ONE), NONE, new int[]{enable});
        builder.addComponent("test.reset", "RST", new BusSource(BitWidth.ONE), NONE, new int[]{reset});
        builder.addComponent("sequential.clock_divider", "DIV", new ClockDividerBehavior(true, 3),
                new int[]{clk, reset, enable}, new int[]{clkOut});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(enableSrc, LogicVector.ONE);
        simulation.setInput(clkSrc, LogicVector.ZERO);
        simulation.setInput(clkSrc, LogicVector.ONE);
        assertEquals(LogicVector.ZERO, simulation.readNet(clkOut), "odd divider's longer low half");
        simulation.setInput(clkSrc, LogicVector.ZERO);
        simulation.setInput(clkSrc, LogicVector.ONE);
        assertEquals(LogicVector.ONE, simulation.readNet(clkOut), "second edge begins the high half");
        simulation.setInput(clkSrc, LogicVector.ZERO);
        simulation.setInput(clkSrc, LogicVector.ONE);
        assertEquals(LogicVector.ZERO, simulation.readNet(clkOut),
                "third edge completes a full period: f_out = f_in / 3");
    }

    @Test
    void clockDividerEvenDivideByFourHasEqualTwoEdgeHalfPeriods() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int clk = builder.addNet(BitWidth.ONE);
        int reset = builder.addNet(BitWidth.ONE);
        int enable = builder.addNet(BitWidth.ONE);
        int clkOut = builder.addNet(BitWidth.ONE);
        int clkSrc = builder.addComponent("test.clk", "CLK", new BusSource(BitWidth.ONE), NONE, new int[]{clk});
        int enableSrc = builder.addComponent("test.en", "EN", new BusSource(BitWidth.ONE), NONE, new int[]{enable});
        builder.addComponent("test.reset", "RST", new BusSource(BitWidth.ONE), NONE, new int[]{reset});
        builder.addComponent("sequential.clock_divider", "DIV", new ClockDividerBehavior(true, 4),
                new int[]{clk, reset, enable}, new int[]{clkOut});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(enableSrc, LogicVector.ONE);
        LogicVector[] expected = {LogicVector.ZERO, LogicVector.ONE,
                LogicVector.ONE, LogicVector.ZERO};
        for (LogicVector level : expected) {
            simulation.setInput(clkSrc, LogicVector.ZERO);
            simulation.setInput(clkSrc, LogicVector.ONE);
            assertEquals(level, simulation.readNet(clkOut));
        }
    }

    @Test
    void universalShiftRegisterLoadsAndShiftsBothDirections() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int parallel = builder.addNet(WIDTH);
        int serialLeft = builder.addNet(BitWidth.ONE);
        int serialRight = builder.addNet(BitWidth.ONE);
        int mode = builder.addNet(BitWidth.of(2));
        int clk = builder.addNet(BitWidth.ONE);
        int reset = builder.addNet(BitWidth.ONE);
        int q = builder.addNet(WIDTH);
        int soutLeft = builder.addNet(BitWidth.ONE);
        int soutRight = builder.addNet(BitWidth.ONE);
        int parallelSrc = builder.addComponent("test.data", "D", new BusSource(WIDTH), NONE, new int[]{parallel});
        int leftSrc = builder.addComponent("test.left", "L", new BusSource(BitWidth.ONE), NONE, new int[]{serialLeft});
        builder.addComponent("test.right", "R", new BusSource(BitWidth.ONE), NONE, new int[]{serialRight});
        int modeSrc = builder.addComponent("test.mode", "MODE", new BusSource(BitWidth.of(2)), NONE, new int[]{mode});
        int clkSrc = builder.addComponent("test.clk", "CLK", new BusSource(BitWidth.ONE), NONE, new int[]{clk});
        builder.addComponent("test.reset", "RST", new BusSource(BitWidth.ONE), NONE, new int[]{reset});
        builder.addComponent("sequential.universal_shift_register", "USR",
                new UniversalShiftRegisterBehavior(WIDTH, true),
                new int[]{parallel, serialLeft, serialRight, mode, clk, reset},
                new int[]{q, soutLeft, soutRight});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(parallelSrc, LogicVector.fromUnsignedLong(0b0101, 4));
        simulation.setInput(modeSrc, LogicVector.fromUnsignedLong(1, 2)); // LOAD
        simulation.setInput(clkSrc, LogicVector.ZERO);
        simulation.setInput(clkSrc, LogicVector.ONE);
        assertEquals(LogicVector.fromUnsignedLong(0b0101, 4), simulation.readNet(q));

        simulation.setInput(leftSrc, LogicVector.ONE);
        simulation.setInput(modeSrc, LogicVector.fromUnsignedLong(2, 2)); // SHIFT_LEFT
        simulation.setInput(clkSrc, LogicVector.ZERO);
        simulation.setInput(clkSrc, LogicVector.ONE);
        assertEquals(LogicVector.fromUnsignedLong(0b1011, 4), simulation.readNet(q),
                "shift left drops the old MSB, pulls SERIAL_LEFT in at bit 0");
    }

    @Test
    void pisoShiftsOutTheLoadedValueLsbFirst() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int data = builder.addNet(WIDTH);
        int load = builder.addNet(BitWidth.ONE);
        int shift = builder.addNet(BitWidth.ONE);
        int serialIn = builder.addNet(BitWidth.ONE);
        int clk = builder.addNet(BitWidth.ONE);
        int reset = builder.addNet(BitWidth.ONE);
        int q = builder.addNet(WIDTH);
        int serialOut = builder.addNet(BitWidth.ONE);
        int dataSrc = builder.addComponent("test.data", "D", new BusSource(WIDTH), NONE, new int[]{data});
        int loadSrc = builder.addComponent("test.load", "LOAD", new BusSource(BitWidth.ONE), NONE, new int[]{load});
        int shiftSrc = builder.addComponent("test.shift", "SHIFT", new BusSource(BitWidth.ONE), NONE, new int[]{shift});
        builder.addComponent("test.sin", "SIN", new BusSource(BitWidth.ONE), NONE, new int[]{serialIn});
        int clkSrc = builder.addComponent("test.clk", "CLK", new BusSource(BitWidth.ONE), NONE, new int[]{clk});
        builder.addComponent("test.reset", "RST", new BusSource(BitWidth.ONE), NONE, new int[]{reset});
        builder.addComponent("sequential.piso", "PISO", new PisoBehavior(WIDTH, true),
                new int[]{data, load, shift, serialIn, clk, reset}, new int[]{q, serialOut});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(dataSrc, LogicVector.fromUnsignedLong(0b1010, 4));
        simulation.setInput(loadSrc, LogicVector.ONE);
        simulation.setInput(clkSrc, LogicVector.ZERO);
        simulation.setInput(clkSrc, LogicVector.ONE);
        assertEquals(LogicVector.ZERO, simulation.readNet(serialOut), "bit 0 of 1010 is 0");

        simulation.setInput(loadSrc, LogicVector.ZERO);
        simulation.setInput(shiftSrc, LogicVector.ONE);
        simulation.setInput(clkSrc, LogicVector.ZERO);
        simulation.setInput(clkSrc, LogicVector.ONE);
        assertEquals(LogicVector.ONE, simulation.readNet(serialOut), "next bit out is bit 1 of 1010, which is 1");
    }

    @Test
    void ringCounterWalksASingleBitAroundTheLoop() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int clk = builder.addNet(BitWidth.ONE);
        int reset = builder.addNet(BitWidth.ONE);
        int q = builder.addNet(WIDTH);
        int clkSrc = builder.addComponent("test.clk", "CLK", new BusSource(BitWidth.ONE), NONE, new int[]{clk});
        int resetSrc = builder.addComponent("test.reset", "RST", new BusSource(BitWidth.ONE), NONE, new int[]{reset});
        builder.addComponent("sequential.ring_counter", "RING",
                new RingJohnsonCounterBehavior(WIDTH, true, RingJohnsonCounterBehavior.Kind.RING),
                new int[]{clk, reset}, new int[]{q});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(resetSrc, LogicVector.ONE);
        simulation.setInput(resetSrc, LogicVector.ZERO);
        assertEquals(LogicVector.fromUnsignedLong(0b0001, 4), simulation.readNet(q));

        simulation.setInput(clkSrc, LogicVector.ZERO);
        simulation.setInput(clkSrc, LogicVector.ONE);
        assertEquals(LogicVector.fromUnsignedLong(0b0010, 4), simulation.readNet(q));

        for (int i = 0; i < 3; i++) {
            simulation.setInput(clkSrc, LogicVector.ZERO);
            simulation.setInput(clkSrc, LogicVector.ONE);
        }
        assertEquals(LogicVector.fromUnsignedLong(0b0001, 4), simulation.readNet(q), "wraps back after width cycles");
    }

    @Test
    void johnsonCounterCountsUpThenBackDown() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int clk = builder.addNet(BitWidth.ONE);
        int reset = builder.addNet(BitWidth.ONE);
        int q = builder.addNet(WIDTH);
        int clkSrc = builder.addComponent("test.clk", "CLK", new BusSource(BitWidth.ONE), NONE, new int[]{clk});
        int resetSrc = builder.addComponent("test.reset", "RST", new BusSource(BitWidth.ONE), NONE, new int[]{reset});
        builder.addComponent("sequential.johnson_counter", "JC",
                new RingJohnsonCounterBehavior(WIDTH, true, RingJohnsonCounterBehavior.Kind.JOHNSON),
                new int[]{clk, reset}, new int[]{q});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(resetSrc, LogicVector.ONE);
        simulation.setInput(resetSrc, LogicVector.ZERO);
        assertEquals(LogicVector.fromUnsignedLong(0, 4), simulation.readNet(q));

        long[] expected = {0b0001, 0b0011, 0b0111, 0b1111, 0b1110, 0b1100, 0b1000, 0b0000};
        for (long value : expected) {
            simulation.setInput(clkSrc, LogicVector.ZERO);
            simulation.setInput(clkSrc, LogicVector.ONE);
            assertEquals(LogicVector.fromUnsignedLong(value, 4), simulation.readNet(q));
        }
    }
}
