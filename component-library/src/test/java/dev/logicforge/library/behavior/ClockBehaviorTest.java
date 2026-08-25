package dev.logicforge.library.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.CompiledCircuit;
import dev.logicforge.simulation.Simulation;
import org.junit.jupiter.api.Test;

class ClockBehaviorTest {

    @Test
    void transitionsOccurAtTheRequestedVirtualTimes() {
        // 1 MHz, 50% duty, starting high: period is 1,000,000 ps, so the first edge is at
        // 500,000 ps (500 ns) and the second at 1,000,000 ps (1 us).
        ClockBehavior clock = ClockBehavior.ofFrequency(1_000_000, 50, true, true);
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int clk = builder.addNet(BitWidth.ONE);
        builder.addComponent("source.clock", "CLK", clock, new int[0], new int[]{clk});
        Simulation simulation = new Simulation(builder.build());

        assertEquals(0, simulation.time());
        assertEquals(LogicVector.ONE, simulation.readNet(clk));

        assertTrue(simulation.advanceToNextEvent());
        assertEquals(500_000, simulation.time());
        assertEquals(LogicVector.ZERO, simulation.readNet(clk));

        assertTrue(simulation.advanceToNextEvent());
        assertEquals(1_000_000, simulation.time());
        assertEquals(LogicVector.ONE, simulation.readNet(clk));
    }

    @Test
    void aDisabledClockNeverSchedulesAWakeup() {
        ClockBehavior clock = ClockBehavior.ofFrequency(1_000, 50, false, false);
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int clk = builder.addNet(BitWidth.ONE);
        builder.addComponent("source.clock", "CLK", clock, new int[0], new int[]{clk});
        Simulation simulation = new Simulation(builder.build());

        assertEquals(LogicVector.ZERO, simulation.readNet(clk), "sits at its initial level");
        assertTrue(simulation.isStable(), "a disabled clock never queues a future event");
    }

    @Test
    void standardFrequenciesProduceTheExpectedPeriod() {
        assertEquals(1_000_000_000_000L, ClockBehavior.ofFrequency(1, 50, false, true).periodPs(), "1 Hz");
        assertEquals(1_000_000_000L, ClockBehavior.ofFrequency(1_000, 50, false, true).periodPs(), "1 kHz");
        assertEquals(1_000_000L, ClockBehavior.ofFrequency(1_000_000, 50, false, true).periodPs(), "1 MHz");
    }
}
