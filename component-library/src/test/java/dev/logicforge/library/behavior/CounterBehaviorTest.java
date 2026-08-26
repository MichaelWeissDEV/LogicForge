package dev.logicforge.library.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.CompiledCircuit;
import dev.logicforge.simulation.Simulation;
import org.junit.jupiter.api.Test;

class CounterBehaviorTest {

    private static final BitWidth WIDTH = BitWidth.of(4);
    private static final int[] NONE = new int[0];

    @Test
    void threeRisingEdgesCountToThree() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int clk = builder.addNet(BitWidth.ONE);
        int enable = builder.addNet(BitWidth.ONE);
        int reset = builder.addNet(BitWidth.ONE);
        int count = builder.addNet(WIDTH);
        int tc = builder.addNet(BitWidth.ONE);
        int clkSrc = builder.addComponent("test.clk", "CLK", new BusSource(BitWidth.ONE), NONE, new int[]{clk});
        int enableSrc = builder.addComponent("test.enable", "EN", new BusSource(BitWidth.ONE), NONE,
                new int[]{enable});
        builder.addComponent("test.reset", "RST", new BusSource(BitWidth.ONE), NONE, new int[]{reset});
        builder.addComponent("sequential.counter_up", "CNT",
                new CounterBehavior(WIDTH, true, CounterBehavior.Direction.UP),
                new int[]{clk, enable, reset}, new int[]{count, tc});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(enableSrc, LogicVector.ONE);
        for (int i = 0; i < 3; i++) {
            simulation.setInput(clkSrc, LogicVector.ZERO);
            simulation.setInput(clkSrc, LogicVector.ONE);
        }

        assertEquals(LogicVector.fromUnsignedLong(3, 4), simulation.readNet(count));
    }

    @Test
    void wrapsAtTheTopAndSignalsTerminalCount() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int clk = builder.addNet(BitWidth.ONE);
        int enable = builder.addNet(BitWidth.ONE);
        int reset = builder.addNet(BitWidth.ONE);
        int count = builder.addNet(WIDTH);
        int tc = builder.addNet(BitWidth.ONE);
        int clkSrc = builder.addComponent("test.clk", "CLK", new BusSource(BitWidth.ONE), NONE, new int[]{clk});
        int enableSrc = builder.addComponent("test.enable", "EN", new BusSource(BitWidth.ONE), NONE,
                new int[]{enable});
        builder.addComponent("test.reset", "RST", new BusSource(BitWidth.ONE), NONE, new int[]{reset});
        builder.addComponent("sequential.counter_up", "CNT",
                new CounterBehavior(WIDTH, true, CounterBehavior.Direction.UP),
                new int[]{clk, enable, reset}, new int[]{count, tc});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(enableSrc, LogicVector.ONE);
        for (int i = 0; i < 15; i++) {
            simulation.setInput(clkSrc, LogicVector.ZERO);
            simulation.setInput(clkSrc, LogicVector.ONE);
        }
        assertEquals(LogicVector.fromUnsignedLong(15, 4), simulation.readNet(count), "4 bits: 15 is the max");
        assertEquals(LogicVector.ONE, simulation.readNet(tc), "at the top, TC signals the wrap about to happen");

        simulation.setInput(clkSrc, LogicVector.ZERO);
        simulation.setInput(clkSrc, LogicVector.ONE);
        assertEquals(LogicVector.fromUnsignedLong(0, 4), simulation.readNet(count), "wraps back to 0");
        assertEquals(LogicVector.ZERO, simulation.readNet(tc));
    }

    @Test
    void unknownEnableMergesHoldAndCountBitwise() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int clk = builder.addNet(BitWidth.ONE);
        int enable = builder.addNet(BitWidth.ONE);
        int reset = builder.addNet(BitWidth.ONE);
        int count = builder.addNet(WIDTH);
        int tc = builder.addNet(BitWidth.ONE);
        int clkSrc = builder.addComponent("test.clk", "CLK", new BusSource(BitWidth.ONE), NONE, new int[]{clk});
        int enableSrc = builder.addComponent("test.enable", "EN", new BusSource(BitWidth.ONE), NONE,
                new int[]{enable});
        builder.addComponent("test.reset", "RST", new BusSource(BitWidth.ONE), NONE, new int[]{reset});
        builder.addComponent("sequential.counter_up", "CNT",
                new CounterBehavior(WIDTH, true, CounterBehavior.Direction.UP),
                new int[]{clk, enable, reset}, new int[]{count, tc});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(enableSrc, LogicVector.ONE);
        for (int i = 0; i < 2; i++) {
            simulation.setInput(clkSrc, LogicVector.ZERO);
            simulation.setInput(clkSrc, LogicVector.ONE);
        }
        simulation.setInput(clkSrc, LogicVector.ZERO);
        simulation.setInput(enableSrc, LogicVector.UNKNOWN);
        simulation.setInput(clkSrc, LogicVector.ONE);

        assertEquals(LogicVector.of("001X"), simulation.readNet(count),
                "possible states 2 and 3 differ only in bit zero");
    }
}
