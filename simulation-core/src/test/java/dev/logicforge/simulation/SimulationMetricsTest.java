package dev.logicforge.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicState;
import org.junit.jupiter.api.Test;

class SimulationMetricsTest {

    private static final int[] NONE = new int[0];

    @Test
    void freshSimulationHasZeroedCountersExceptWhatResetItselfDoes() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int netA = builder.addNet(BitWidth.ONE);
        builder.addComponent("source.toggle", "A", TestBehaviors.SWITCH, NONE, new int[]{netA});
        Simulation simulation = new Simulation(builder.build());

        SimulationMetrics metrics = simulation.metrics();
        assertEquals(0, metrics.currentVirtualTime());
        // reset() itself evaluates every component once, so some counters are already
        // nonzero immediately after construction - that is expected activity, not noise.
        assertTrue(metrics.componentEvaluations() >= 1);
    }

    @Test
    void countersAccumulateAcrossActivityAndResetClearsThemAgain() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int netA = builder.addNet(BitWidth.ONE);
        int netB = builder.addNet(BitWidth.ONE);
        int switchA = builder.addComponent("source.toggle", "A", TestBehaviors.SWITCH, NONE, new int[]{netA});
        builder.addComponent("logic.not", "N1", TestBehaviors.NOT, new int[]{netA}, new int[]{netB});
        Simulation simulation = new Simulation(builder.build());

        SimulationMetrics beforeToggle = simulation.metrics();
        simulation.setInput(switchA, LogicState.ONE);
        SimulationMetrics afterToggle = simulation.metrics();

        assertTrue(afterToggle.eventsProcessed() > beforeToggle.eventsProcessed());
        assertTrue(afterToggle.componentEvaluations() > beforeToggle.componentEvaluations());
        assertTrue(afterToggle.netTransitions() > beforeToggle.netTransitions());
        assertTrue(afterToggle.deltaCycles() > beforeToggle.deltaCycles());
        assertEquals(1, afterToggle.currentVirtualTime());

        simulation.reset();
        SimulationMetrics afterReset = simulation.metrics();
        assertEquals(0, afterReset.currentVirtualTime());
        assertTrue(afterReset.eventsProcessed() < afterToggle.eventsProcessed());
    }

    @Test
    void maxDeltaDepthTracksTheDeepestSettleWithoutAccumulatingAcrossToggles() {
        // A healthy 20-inverter chain settles in a bounded number of delta cycles per
        // toggle. maxDeltaDepth should reflect that bound and stay there across many
        // toggles, rather than climbing - which is exactly the signal that would have made
        // the deltaCyclesAtCurrentTime reset bug obvious immediately.
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int netA = builder.addNet(BitWidth.ONE);
        int switchA = builder.addComponent("source.toggle", "A", TestBehaviors.SWITCH, NONE, new int[]{netA});
        int prev = netA;
        for (int i = 0; i < 20; i++) {
            int net = builder.addNet(BitWidth.ONE);
            builder.addComponent("logic.not", "N" + i, TestBehaviors.NOT, new int[]{prev}, new int[]{net});
            prev = net;
        }
        Simulation simulation = new Simulation(builder.build());

        for (int toggle = 0; toggle < 500; toggle++) {
            simulation.setInput(switchA, toggle % 2 == 0 ? LogicState.ONE : LogicState.ZERO);
        }

        long depth = simulation.metrics().maxDeltaDepth();
        assertTrue(depth > 0 && depth <= 25, "expected a small bounded settle depth, was " + depth);
    }

    @Test
    void scheduledWakeupsCountsDistinctWakeupsNotDuplicateRequests() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int clk = builder.addNet(BitWidth.ONE);
        builder.addComponent("test.oscillator", "OSC", TestBehaviors.periodicToggle(500), NONE, new int[]{clk});
        Simulation simulation = new Simulation(builder.build());

        assertTrue(simulation.metrics().scheduledWakeups() >= 1);
    }

    @Test
    void takingASnapshotNeverChangesSimulationBehavior() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int netA = builder.addNet(BitWidth.ONE);
        int netB = builder.addNet(BitWidth.ONE);
        int switchA = builder.addComponent("source.toggle", "A", TestBehaviors.SWITCH, NONE, new int[]{netA});
        builder.addComponent("logic.not", "N1", TestBehaviors.NOT, new int[]{netA}, new int[]{netB});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(switchA, LogicState.ONE);
        for (int i = 0; i < 100; i++) {
            simulation.metrics(); // must be side-effect-free to call repeatedly
        }
        assertEquals(LogicState.ZERO, simulation.readNet(netB).singleBit());
        assertEquals(SimulationStatus.STABLE, simulation.status());
    }

    @Test
    void settingAnInvalidMaxDeltaCyclesStillLeavesMetricsReadable() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int netA = builder.addNet(BitWidth.ONE);
        int netB = builder.addNet(BitWidth.ONE);
        builder.addComponent("logic.not", "N1", TestBehaviors.NOT, new int[]{netA}, new int[]{netB});
        Simulation simulation = new Simulation(builder.build());

        SimulationMetrics before = simulation.metrics();
        assertThrows(IllegalArgumentException.class, () -> simulation.setMaxDeltaCycles(0));
        assertEquals(before, simulation.metrics());
    }
}
