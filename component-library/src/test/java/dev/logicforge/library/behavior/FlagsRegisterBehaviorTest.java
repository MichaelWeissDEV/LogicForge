package dev.logicforge.library.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.CompiledCircuit;
import dev.logicforge.simulation.Simulation;
import org.junit.jupiter.api.Test;

class FlagsRegisterBehaviorTest {

    private static final int[] NONE = new int[0];

    @Test
    void latchesZcnvOnTheRisingEdgeWhileLoadIsHighAndHoldsOtherwise() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int z = builder.addNet(BitWidth.ONE);
        int c = builder.addNet(BitWidth.ONE);
        int n = builder.addNet(BitWidth.ONE);
        int v = builder.addNet(BitWidth.ONE);
        int clk = builder.addNet(BitWidth.ONE);
        int load = builder.addNet(BitWidth.ONE);
        int flags = builder.addNet(BitWidth.of(4));
        int zSrc = builder.addComponent("test.z", "Z", new BusSource(BitWidth.ONE), NONE, new int[]{z});
        int cSrc = builder.addComponent("test.c", "C", new BusSource(BitWidth.ONE), NONE, new int[]{c});
        int nSrc = builder.addComponent("test.n", "N", new BusSource(BitWidth.ONE), NONE, new int[]{n});
        int vSrc = builder.addComponent("test.v", "V", new BusSource(BitWidth.ONE), NONE, new int[]{v});
        int clkSrc = builder.addComponent("test.clk", "CLK", new BusSource(BitWidth.ONE), NONE, new int[]{clk});
        int loadSrc = builder.addComponent("test.load", "LOAD", new BusSource(BitWidth.ONE), NONE, new int[]{load});
        builder.addComponent("sequential.flags_register", "FLAGS", new FlagsRegisterBehavior(true),
                new int[]{z, c, n, v, clk, load}, new int[]{flags});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(loadSrc, LogicVector.ONE);
        simulation.setInput(zSrc, LogicVector.ONE);
        simulation.setInput(cSrc, LogicVector.ZERO);
        simulation.setInput(nSrc, LogicVector.ONE);
        simulation.setInput(vSrc, LogicVector.ZERO);
        assertEquals(LogicVector.fromUnsignedLong(0, 4), simulation.readNet(flags), "no clock edge yet");

        simulation.setInput(clkSrc, LogicVector.ONE);
        // bit0=Z=1, bit1=C=0, bit2=N=1, bit3=V=0 -> 0b0101
        assertEquals(LogicVector.fromUnsignedLong(0b0101, 4), simulation.readNet(flags),
                "rising edge captures Z,C,N,V LSB-first");

        simulation.setInput(loadSrc, LogicVector.ZERO);
        simulation.setInput(clkSrc, LogicVector.ZERO);
        simulation.setInput(zSrc, LogicVector.ZERO);
        simulation.setInput(cSrc, LogicVector.ONE);
        simulation.setInput(clkSrc, LogicVector.ONE);
        assertEquals(LogicVector.fromUnsignedLong(0b0101, 4), simulation.readNet(flags),
                "LOAD=0 holds despite a new edge");
    }
}
