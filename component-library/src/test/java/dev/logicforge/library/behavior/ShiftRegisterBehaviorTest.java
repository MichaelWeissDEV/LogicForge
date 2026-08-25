package dev.logicforge.library.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.CompiledCircuit;
import dev.logicforge.simulation.Simulation;
import org.junit.jupiter.api.Test;

class ShiftRegisterBehaviorTest {

    private static final BitWidth WIDTH = BitWidth.of(4);
    private static final int[] NONE = new int[0];

    @Test
    void shiftsInOneBitPerRisingEdgeTowardsTheMsb() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int sin = builder.addNet(BitWidth.ONE);
        int clk = builder.addNet(BitWidth.ONE);
        int reset = builder.addNet(BitWidth.ONE);
        int q = builder.addNet(WIDTH);
        int sout = builder.addNet(BitWidth.ONE);
        int sinSrc = builder.addComponent("test.sin", "SIN", new BusSource(BitWidth.ONE), NONE, new int[]{sin});
        int clkSrc = builder.addComponent("test.clk", "CLK", new BusSource(BitWidth.ONE), NONE, new int[]{clk});
        builder.addComponent("test.reset", "RESET", new BusSource(BitWidth.ONE), NONE, new int[]{reset});
        builder.addComponent("sequential.shift_register", "SR", new ShiftRegisterBehavior(WIDTH, true),
                new int[]{sin, clk, reset}, new int[]{q, sout});
        Simulation simulation = new Simulation(builder.build());

        for (LogicState bit : new LogicState[]{LogicState.ONE, LogicState.ZERO, LogicState.ONE, LogicState.ONE}) {
            simulation.setInput(clkSrc, LogicVector.ZERO);
            simulation.setInput(sinSrc, LogicVector.single(bit));
            simulation.setInput(clkSrc, LogicVector.ONE);
        }

        assertEquals(LogicVector.fromUnsignedLong(0b1011, 4), simulation.readNet(q));
        assertEquals(LogicVector.ONE, simulation.readNet(sout), "SOUT taps the current MSB");
    }

    @Test
    void resetClearsTheRegisterImmediately() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int sin = builder.addNet(BitWidth.ONE);
        int clk = builder.addNet(BitWidth.ONE);
        int reset = builder.addNet(BitWidth.ONE);
        int q = builder.addNet(WIDTH);
        int sout = builder.addNet(BitWidth.ONE);
        int sinSrc = builder.addComponent("test.sin", "SIN", new BusSource(BitWidth.ONE), NONE, new int[]{sin});
        int clkSrc = builder.addComponent("test.clk", "CLK", new BusSource(BitWidth.ONE), NONE, new int[]{clk});
        int resetSrc = builder.addComponent("test.reset", "RESET", new BusSource(BitWidth.ONE), NONE,
                new int[]{reset});
        builder.addComponent("sequential.shift_register", "SR", new ShiftRegisterBehavior(WIDTH, true),
                new int[]{sin, clk, reset}, new int[]{q, sout});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(sinSrc, LogicVector.ONE);
        simulation.setInput(clkSrc, LogicVector.ONE);
        assertEquals(LogicVector.fromUnsignedLong(1, 4), simulation.readNet(q));

        simulation.setInput(resetSrc, LogicVector.ONE);
        assertEquals(LogicVector.fromUnsignedLong(0, 4), simulation.readNet(q));
    }
}
