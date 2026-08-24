package dev.logicforge.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicOperation;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationTest {

    private static final int[] NONE = new int[0];

    @Test
    void inputChangesPropagateThroughAGate() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int netA = builder.addNet(BitWidth.ONE);
        int netB = builder.addNet(BitWidth.ONE);
        int netOut = builder.addNet(BitWidth.ONE);
        int switchA = builder.addComponent("source.toggle", "A", TestBehaviors.SWITCH, NONE, new int[]{netA});
        int switchB = builder.addComponent("source.toggle", "B", TestBehaviors.SWITCH, NONE, new int[]{netB});
        builder.addComponent("logic.and", "AND1", TestBehaviors.gate(LogicOperation.AND, false),
                new int[]{netA, netB}, new int[]{netOut});
        Simulation simulation = new Simulation(builder.build());

        assertEquals(LogicVector.ZERO, simulation.readNet(netOut), "both switches start at 0");

        simulation.setInput(switchA, LogicState.ONE);
        assertEquals(LogicVector.ZERO, simulation.readNet(netOut));

        simulation.setInput(switchB, LogicState.ONE);
        assertEquals(LogicVector.ONE, simulation.readNet(netOut));

        simulation.setInput(switchA, LogicState.ZERO);
        assertEquals(LogicVector.ZERO, simulation.readNet(netOut));
        assertTrue(simulation.isStable());
        assertEquals(SimulationStatus.STABLE, simulation.status());
    }

    @Test
    void unconnectedInputsAreUnknownNotZero() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int floating = builder.addNet(BitWidth.ONE);
        int netOut = builder.addNet(BitWidth.ONE);
        builder.addComponent("logic.and", "AND1", TestBehaviors.gate(LogicOperation.AND, false),
                new int[]{floating}, new int[]{netOut});
        Simulation simulation = new Simulation(builder.build());

        assertEquals(LogicVector.HIGH_IMPEDANCE, simulation.readNet(floating), "nobody drives that net");
        assertEquals(LogicVector.UNKNOWN, simulation.readNet(netOut), "a floating input reads as X");
    }

    @Test
    void onlyAffectedComponentsAreEvaluated() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int netA = builder.addNet(BitWidth.ONE);
        int netUnrelated = builder.addNet(BitWidth.ONE);
        int netOut = builder.addNet(BitWidth.ONE);
        int netOther = builder.addNet(BitWidth.ONE);
        List<String> evaluated = new ArrayList<>();
        int switchA = builder.addComponent("source.toggle", "A", TestBehaviors.SWITCH, NONE, new int[]{netA});
        builder.addComponent("logic.not", "N1", context -> {
            evaluated.add("N1");
            TestBehaviors.NOT.evaluate(context);
        }, new int[]{netA}, new int[]{netOut});
        builder.addComponent("logic.not", "N2", context -> {
            evaluated.add("N2");
            TestBehaviors.NOT.evaluate(context);
        }, new int[]{netUnrelated}, new int[]{netOther});
        Simulation simulation = new Simulation(builder.build());

        evaluated.clear();
        simulation.setInput(switchA, LogicState.ONE);

        assertEquals(List.of("N1"), evaluated, "the gate on an unchanged net is not re-evaluated");
    }

    @Test
    void triStateDriversShareANetWithoutConflict() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int dataA = builder.addNet(BitWidth.ONE);
        int dataB = builder.addNet(BitWidth.ONE);
        int enableA = builder.addNet(BitWidth.ONE);
        int enableB = builder.addNet(BitWidth.ONE);
        int bus = builder.addNet(BitWidth.ONE);
        builder.addComponent("source.constant", "0", TestBehaviors.constant(LogicState.ZERO), NONE, new int[]{dataA});
        builder.addComponent("source.constant", "1", TestBehaviors.constant(LogicState.ONE), NONE, new int[]{dataB});
        int enableSwitchA = builder.addComponent("source.toggle", "EA", TestBehaviors.SWITCH, NONE, new int[]{enableA});
        int enableSwitchB = builder.addComponent("source.toggle", "EB", TestBehaviors.SWITCH, NONE, new int[]{enableB});
        builder.addComponent("logic.tristate", "T1", TestBehaviors.TRI_STATE,
                new int[]{dataA, enableA}, new int[]{bus});
        builder.addComponent("logic.tristate", "T2", TestBehaviors.TRI_STATE,
                new int[]{dataB, enableB}, new int[]{bus});
        Simulation simulation = new Simulation(builder.build());

        assertEquals(LogicVector.HIGH_IMPEDANCE, simulation.readNet(bus), "both drivers disabled");
        assertFalse(simulation.hasDriverConflict(bus));

        simulation.setInput(enableSwitchA, LogicState.ONE);
        assertEquals(LogicVector.ZERO, simulation.readNet(bus));
        assertFalse(simulation.hasDriverConflict(bus));

        simulation.setInput(enableSwitchB, LogicState.ONE);
        assertEquals(LogicVector.UNKNOWN, simulation.readNet(bus), "0 against 1 is a conflict");
        assertTrue(simulation.hasDriverConflict(bus));

        simulation.setInput(enableSwitchA, LogicState.ZERO);
        assertEquals(LogicVector.ONE, simulation.readNet(bus));
        assertFalse(simulation.hasDriverConflict(bus));
    }

    @Test
    void fourStateFeedbackSettlesOnUnknown() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int loop = builder.addNet(BitWidth.ONE);
        builder.addComponent("logic.not", "N1", TestBehaviors.NOT, new int[]{loop}, new int[]{loop});
        Simulation simulation = new Simulation(builder.build());

        assertEquals(LogicVector.UNKNOWN, simulation.readNet(loop),
                "an inverter feeding itself has X as its stable state");
        assertTrue(simulation.isStable());
    }

    @Test
    void runawayFeedbackHitsTheDeltaCycleLimit() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int loop = builder.addNet(BitWidth.ONE);
        builder.addComponent("test.flipping", "OSC", TestBehaviors.ALWAYS_FLIPPING,
                new int[]{loop}, new int[]{loop});
        Simulation simulation = new Simulation(builder.build());

        assertEquals(SimulationStatus.OSCILLATING, simulation.status(),
                "building an oscillating circuit is a state to show, not a crash");
        assertTrue(simulation.oscillation().isPresent());
        assertEquals(List.of(loop), simulation.oscillation().orElseThrow().oscillatingNets());
        simulation.setRunning(false);
        simulation.reset();
        assertThrows(SimulationOscillationException.class, () -> simulation.setRunning(true),
                "an explicit run reports the oscillation instead of hanging");
    }

    @Test
    void pausedSimulationOnlyAdvancesOnStep() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int netA = builder.addNet(BitWidth.ONE);
        int netB = builder.addNet(BitWidth.ONE);
        int netC = builder.addNet(BitWidth.ONE);
        int switchA = builder.addComponent("source.toggle", "A", TestBehaviors.SWITCH, NONE, new int[]{netA});
        builder.addComponent("logic.not", "N1", TestBehaviors.NOT, new int[]{netA}, new int[]{netB});
        builder.addComponent("logic.not", "N2", TestBehaviors.NOT, new int[]{netB}, new int[]{netC});
        Simulation simulation = new Simulation(builder.build());

        simulation.setRunning(false);
        simulation.setInput(switchA, LogicState.ONE);

        assertEquals(SimulationStatus.PENDING, simulation.status());
        assertEquals(LogicVector.ZERO, simulation.readNet(netA), "nothing propagated yet");

        assertTrue(simulation.step());
        assertEquals(LogicVector.ONE, simulation.readNet(netA));
        assertEquals(LogicVector.ZERO, simulation.readNet(netC), "the far end lags behind");

        simulation.step();
        assertEquals(LogicVector.ZERO, simulation.readNet(netB));

        simulation.setRunning(true);
        assertEquals(LogicVector.ONE, simulation.readNet(netC));
        assertTrue(simulation.isStable());
    }

    @Test
    void observersSeeEveryNetChange() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int netA = builder.addNet(BitWidth.ONE);
        int netOut = builder.addNet(BitWidth.ONE);
        int switchA = builder.addComponent("source.toggle", "A", TestBehaviors.SWITCH, NONE, new int[]{netA});
        builder.addComponent("logic.not", "N1", TestBehaviors.NOT, new int[]{netA}, new int[]{netOut});
        Simulation simulation = new Simulation(builder.build());

        List<String> trace = new ArrayList<>();
        simulation.addObserver((netId, previous, current, time, delta) ->
                trace.add(netId + ":" + previous + "->" + current + "@" + time + "/" + delta));

        simulation.setInput(switchA, LogicState.ONE);

        assertEquals(List.of("0:0->1@1/0", "1:1->0@1/1"), trace,
                "each user stimulus starts a new moment in simulated time");
    }

    @Test
    void resetRestoresTheInitialState() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int netA = builder.addNet(BitWidth.ONE);
        int netOut = builder.addNet(BitWidth.ONE);
        int switchA = builder.addComponent("source.toggle", "A", TestBehaviors.SWITCH, NONE, new int[]{netA});
        builder.addComponent("logic.not", "N1", TestBehaviors.NOT, new int[]{netA}, new int[]{netOut});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(switchA, LogicState.ONE);
        assertEquals(LogicVector.ZERO, simulation.readNet(netOut));

        simulation.reset();

        assertEquals(LogicVector.ZERO, simulation.readNet(netA), "the switch returns to its power-on value");
        assertEquals(LogicVector.ONE, simulation.readNet(netOut));
        assertEquals(0, simulation.time());
    }

    @Test
    void aDriverThatDoesNotChangeCostsNoEvent() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int netA = builder.addNet(BitWidth.ONE);
        int netOut = builder.addNet(BitWidth.ONE);
        int switchA = builder.addComponent("source.toggle", "A", TestBehaviors.SWITCH, NONE, new int[]{netA});
        builder.addComponent("logic.not", "N1", TestBehaviors.NOT, new int[]{netA}, new int[]{netOut});
        Simulation simulation = new Simulation(builder.build());

        simulation.setRunning(false);
        simulation.setInput(switchA, LogicState.ZERO);
        assertEquals(0, simulation.pendingEventCount(), "setting the value it already has changes nothing");
        assertTrue(simulation.isStable());
    }

    @Test
    void settingAValueOnSomethingThatIsNotAnInputFails() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int netA = builder.addNet(BitWidth.ONE);
        int netOut = builder.addNet(BitWidth.ONE);
        int gate = builder.addComponent("logic.not", "N1", TestBehaviors.NOT,
                new int[]{netA}, new int[]{netOut});
        Simulation simulation = new Simulation(builder.build());

        assertThrows(SimulationException.class, () -> simulation.setInput(gate, LogicState.ONE));
    }

    @Test
    void identicalRunsProduceIdenticalTraces() {
        List<String> first = traceOfSampleCircuit();
        List<String> second = traceOfSampleCircuit();
        List<String> third = traceOfSampleCircuit();

        assertEquals(first, second);
        assertEquals(second, third);
        assertFalse(first.isEmpty());
        assertNotEquals(List.of(), first);
    }

    /** A small mixed circuit run through a fixed input sequence, recording every change. */
    private static List<String> traceOfSampleCircuit() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int a = builder.addNet(BitWidth.ONE);
        int b = builder.addNet(BitWidth.ONE);
        int c = builder.addNet(BitWidth.ONE);
        int notA = builder.addNet(BitWidth.ONE);
        int andOut = builder.addNet(BitWidth.ONE);
        int orOut = builder.addNet(BitWidth.ONE);
        int xorOut = builder.addNet(BitWidth.ONE);
        int switchA = builder.addComponent("source.toggle", "A", TestBehaviors.SWITCH, NONE, new int[]{a});
        int switchB = builder.addComponent("source.toggle", "B", TestBehaviors.SWITCH, NONE, new int[]{b});
        int switchC = builder.addComponent("source.toggle", "C", TestBehaviors.SWITCH, NONE, new int[]{c});
        builder.addComponent("logic.not", "N", TestBehaviors.NOT, new int[]{a}, new int[]{notA});
        builder.addComponent("logic.and", "AND", TestBehaviors.gate(LogicOperation.AND, false),
                new int[]{notA, b, c}, new int[]{andOut});
        builder.addComponent("logic.or", "OR", TestBehaviors.gate(LogicOperation.OR, false),
                new int[]{andOut, c}, new int[]{orOut});
        builder.addComponent("logic.xor", "XOR", TestBehaviors.gate(LogicOperation.XOR, false),
                new int[]{orOut, andOut, b}, new int[]{xorOut});
        Simulation simulation = new Simulation(builder.build());

        List<String> trace = new ArrayList<>();
        simulation.addObserver((netId, previous, current, time, delta) ->
                trace.add(netId + " " + previous + "->" + current + " @" + time + "/" + delta));

        for (LogicState[] inputs : new LogicState[][]{
                {LogicState.ONE, LogicState.ZERO, LogicState.ZERO},
                {LogicState.ZERO, LogicState.ONE, LogicState.ONE},
                {LogicState.ONE, LogicState.ONE, LogicState.ZERO},
                {LogicState.ZERO, LogicState.ZERO, LogicState.ONE}}) {
            simulation.setInput(switchA, inputs[0]);
            simulation.setInput(switchB, inputs[1]);
            simulation.setInput(switchC, inputs[2]);
            trace.add("--- " + simulation.readNet(xorOut) + " " + simulation.status());
        }
        return trace;
    }

    @Test
    void oscillationDetectionIsPerTimestampNotTotal() {
        // Test that many successive timestamps do NOT cause oscillation detection
        // Only too many delta cycles at the SAME timestamp should trigger oscillation
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int netA = builder.addNet(BitWidth.ONE);
        int netB = builder.addNet(BitWidth.ONE);
        int netC = builder.addNet(BitWidth.ONE);
        
        // Create a long chain of inverters that will take many delta cycles to settle
        // but each at the same timestamp (time 0)
        int switchA = builder.addComponent("source.toggle", "A", TestBehaviors.SWITCH, NONE, new int[]{netA});
        int prev = netA;
        for (int i = 0; i < 50; i++) {
            int net = builder.addNet(BitWidth.ONE);
            builder.addComponent("logic.not", "N" + i, TestBehaviors.NOT, new int[]{prev}, new int[]{net});
            prev = net;
        }
        
        Simulation simulation = new Simulation(builder.build());
        
        // This should NOT oscillate because each delta cycle is at the same timestamp
        // but the limit is on delta cycles per timestamp, not total
        // With maxDeltaCycles = 1000, 50 inverters in a chain should be fine
        simulation.setInput(switchA, LogicState.ONE);
        
        // Should stabilize without oscillation
        assertEquals(SimulationStatus.STABLE, simulation.status());
    }

    @Test
    void zeroDelayLoopIsDetectedAsOscillation() {
        // Test that a true zero-delay loop is detected as oscillation
        // Use ALWAYS_FLIPPING which treats UNKNOWN as 0 and always inverts,
        // causing an infinite oscillation: 0 -> 1 -> 0 -> 1 ...
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int loop = builder.addNet(BitWidth.ONE);
        builder.addComponent("test.flipping", "OSC", TestBehaviors.ALWAYS_FLIPPING,
                new int[]{loop}, new int[]{loop});
        
        Simulation simulation = new Simulation(builder.build());
        
        // This should detect oscillation because we have an infinite loop at timestamp 0
        assertEquals(SimulationStatus.OSCILLATING, simulation.status());
        assertTrue(simulation.oscillation().isPresent());
    }
}
