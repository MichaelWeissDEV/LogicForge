package dev.logicforge.library.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.CompiledCircuit;
import dev.logicforge.simulation.Simulation;
import org.junit.jupiter.api.Test;

class RegisterBehaviorTest {

    private static final BitWidth WIDTH = BitWidth.of(4);
    private static final int[] NONE = new int[0];

    @Test
    void loadsDataOnTheRisingEdgeWhileLoadIsHighAndHoldsOtherwise() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int data = builder.addNet(WIDTH);
        int clk = builder.addNet(BitWidth.ONE);
        int load = builder.addNet(BitWidth.ONE);
        int q = builder.addNet(WIDTH);
        int dataSrc = builder.addComponent("test.data", "DATA", new BusSource(WIDTH), NONE, new int[]{data});
        int clkSrc = builder.addComponent("test.clk", "CLK", new BusSource(BitWidth.ONE), NONE, new int[]{clk});
        int loadSrc = builder.addComponent("test.load", "LOAD", new BusSource(BitWidth.ONE), NONE, new int[]{load});
        builder.addComponent("sequential.register", "REG", new RegisterBehavior(WIDTH, true, false),
                new int[]{data, clk, load}, new int[]{q});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(loadSrc, LogicVector.ONE);
        simulation.setInput(dataSrc, LogicVector.fromUnsignedLong(0b0101, 4));
        assertEquals(LogicVector.fromUnsignedLong(0, 4), simulation.readNet(q), "no clock edge yet");

        simulation.setInput(clkSrc, LogicVector.ONE);
        assertEquals(LogicVector.fromUnsignedLong(0b0101, 4), simulation.readNet(q), "rising edge loads DATA");

        simulation.setInput(loadSrc, LogicVector.ZERO);
        simulation.setInput(clkSrc, LogicVector.ZERO);
        simulation.setInput(dataSrc, LogicVector.fromUnsignedLong(0b1111, 4));
        simulation.setInput(clkSrc, LogicVector.ONE);
        assertEquals(LogicVector.fromUnsignedLong(0b0101, 4), simulation.readNet(q), "LOAD=0 holds despite a new edge");
    }

    @Test
    void asynchronousResetClearsTheRegisterRegardlessOfTheClock() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int data = builder.addNet(WIDTH);
        int clk = builder.addNet(BitWidth.ONE);
        int load = builder.addNet(BitWidth.ONE);
        int reset = builder.addNet(BitWidth.ONE);
        int q = builder.addNet(WIDTH);
        int dataSrc = builder.addComponent("test.data", "DATA", new BusSource(WIDTH), NONE, new int[]{data});
        int clkSrc = builder.addComponent("test.clk", "CLK", new BusSource(BitWidth.ONE), NONE, new int[]{clk});
        int loadSrc = builder.addComponent("test.load", "LOAD", new BusSource(BitWidth.ONE), NONE, new int[]{load});
        int resetSrc = builder.addComponent("test.reset", "RESET", new BusSource(BitWidth.ONE), NONE,
                new int[]{reset});
        builder.addComponent("sequential.register_reset", "REG", new RegisterBehavior(WIDTH, true, true),
                new int[]{data, clk, load, reset}, new int[]{q});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(loadSrc, LogicVector.ONE);
        simulation.setInput(dataSrc, LogicVector.fromUnsignedLong(0b1010, 4));
        simulation.setInput(clkSrc, LogicVector.ONE);
        assertEquals(LogicVector.fromUnsignedLong(0b1010, 4), simulation.readNet(q));

        simulation.setInput(resetSrc, LogicVector.ONE);
        assertEquals(LogicVector.fromUnsignedLong(0, 4), simulation.readNet(q), "RESET clears immediately");
    }

    @Test
    void unknownResetMergesResetAndNormalStateBitwise() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int data = builder.addNet(WIDTH);
        int clk = builder.addNet(BitWidth.ONE);
        int load = builder.addNet(BitWidth.ONE);
        int reset = builder.addNet(BitWidth.ONE);
        int q = builder.addNet(WIDTH);
        int dataSrc = builder.addComponent("test.data", "DATA", new BusSource(WIDTH), NONE, new int[]{data});
        int clkSrc = builder.addComponent("test.clk", "CLK", new BusSource(BitWidth.ONE), NONE, new int[]{clk});
        int loadSrc = builder.addComponent("test.load", "LOAD", new BusSource(BitWidth.ONE), NONE, new int[]{load});
        int resetSrc = builder.addComponent("test.reset", "RESET", new BusSource(BitWidth.ONE), NONE,
                new int[]{reset});
        builder.addComponent("sequential.register_reset", "REG", new RegisterBehavior(WIDTH, true, true),
                new int[]{data, clk, load, reset}, new int[]{q});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(resetSrc, LogicVector.ZERO);
        simulation.setInput(loadSrc, LogicVector.ONE);
        simulation.setInput(dataSrc, LogicVector.fromUnsignedLong(0b1010, 4));
        simulation.setInput(clkSrc, LogicVector.ZERO);
        simulation.setInput(clkSrc, LogicVector.ONE);
        simulation.setInput(resetSrc, LogicVector.UNKNOWN);

        assertEquals(LogicVector.of("X0X0"), simulation.readNet(q),
                "bits already zero agree on both possible reset paths and remain known");
    }

    @Test
    void unknownLoadMergesHoldAndLoadResultsBitwise() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int data = builder.addNet(WIDTH);
        int clk = builder.addNet(BitWidth.ONE);
        int load = builder.addNet(BitWidth.ONE);
        int q = builder.addNet(WIDTH);
        int dataSrc = builder.addComponent("test.data", "DATA", new BusSource(WIDTH), NONE, new int[]{data});
        int clkSrc = builder.addComponent("test.clk", "CLK", new BusSource(BitWidth.ONE), NONE, new int[]{clk});
        int loadSrc = builder.addComponent("test.load", "LOAD", new BusSource(BitWidth.ONE), NONE, new int[]{load});
        builder.addComponent("sequential.register", "REG", new RegisterBehavior(WIDTH, true, false),
                new int[]{data, clk, load}, new int[]{q});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(loadSrc, LogicVector.ONE);
        simulation.setInput(dataSrc, LogicVector.fromUnsignedLong(0b1010, 4));
        simulation.setInput(clkSrc, LogicVector.ZERO);
        simulation.setInput(clkSrc, LogicVector.ONE);
        simulation.setInput(clkSrc, LogicVector.ZERO);
        simulation.setInput(loadSrc, LogicVector.UNKNOWN);
        simulation.setInput(dataSrc, LogicVector.fromUnsignedLong(0b1110, 4));
        simulation.setInput(clkSrc, LogicVector.ONE);

        assertEquals(LogicVector.of("1X10"), simulation.readNet(q));
    }
}
