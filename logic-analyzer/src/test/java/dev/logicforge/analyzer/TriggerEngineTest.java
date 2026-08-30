package dev.logicforge.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.CompiledCircuit;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;
import dev.logicforge.simulation.InputSourceState;
import dev.logicforge.simulation.Simulation;
import org.junit.jupiter.api.Test;

/**
 * {@link TriggerEngine} driven against a real {@link Simulation} — the same minimal
 * settable-source harness {@link SignalRecorderTest} uses, so a trigger is proven against
 * the exact event stream the waveform itself is built from, not a hand-rolled substitute.
 */
class TriggerEngineTest {

    private static final int[] NONE = new int[0];

    private static final ComponentBehavior SWITCH = new ComponentBehavior() {

        @Override
        public void evaluate(ComponentContext context) {
            context.driveOutput(0, ((InputSourceState) context.state()).value());
        }

        @Override
        public ComponentRuntimeState createState() {
            return new InputSourceState() {

                private LogicVector value = LogicVector.ZERO;

                @Override
                public LogicVector value() {
                    return value;
                }

                @Override
                public void setValue(LogicVector newValue) {
                    this.value = newValue;
                }

                @Override
                public void reset() {
                    this.value = LogicVector.ZERO;
                }
            };
        }
    };

    private record Circuit(Simulation simulation, int net, int source) {
    }

    private static Circuit oneBitSwitch() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int net = builder.addNet(BitWidth.ONE);
        int src = builder.addComponent("test.switch", "SW", SWITCH, NONE, new int[]{net});
        return new Circuit(new Simulation(builder.build()), net, src);
    }

    @Test
    void startsDisarmedAndIgnoresNetChangesUntilArmed() {
        Circuit circuit = oneBitSwitch();
        TriggerEngine engine = new TriggerEngine(circuit.simulation());
        assertEquals(TriggerEngine.Status.DISARMED, engine.status());

        circuit.simulation().setInput(circuit.source(), LogicVector.ONE);
        assertEquals(TriggerEngine.Status.DISARMED, engine.status(),
                "an unarmed engine must not react to circuit activity at all");
    }

    @Test
    void firesOnARisingEdgeAndRecordsWhenItHappened() {
        Circuit circuit = oneBitSwitch();
        TriggerEngine engine = new TriggerEngine(circuit.simulation());
        engine.arm(new AnalyzerSignalBinding.Vector(circuit.net(), 0, 1), new TriggerCondition.RisingEdge(0));

        circuit.simulation().setInput(circuit.source(), LogicVector.ONE);

        assertEquals(TriggerEngine.Status.TRIGGERED, engine.status());
        assertEquals(1L, engine.triggerTime().orElseThrow());
    }

    @Test
    void doesNotFireOnAFallingEdgeWhenArmedForRising() {
        Circuit circuit = oneBitSwitch();
        TriggerEngine engine = new TriggerEngine(circuit.simulation());
        circuit.simulation().setInput(circuit.source(), LogicVector.ONE); // now high
        engine.arm(new AnalyzerSignalBinding.Vector(circuit.net(), 0, 1), new TriggerCondition.RisingEdge(0));

        circuit.simulation().setInput(circuit.source(), LogicVector.ZERO); // falling, not rising

        assertEquals(TriggerEngine.Status.ARMED, engine.status());
    }

    @Test
    void fireListenerIsCalledExactlyOnceWhenTheConditionMatches() {
        Circuit circuit = oneBitSwitch();
        TriggerEngine engine = new TriggerEngine(circuit.simulation());
        engine.arm(new AnalyzerSignalBinding.Vector(circuit.net(), 0, 1), new TriggerCondition.RisingEdge(0));
        int[] fireCount = {0};
        engine.addFireListener(() -> fireCount[0]++);

        circuit.simulation().setInput(circuit.source(), LogicVector.ONE);
        circuit.simulation().setInput(circuit.source(), LogicVector.ZERO);
        circuit.simulation().setInput(circuit.source(), LogicVector.ONE);

        assertEquals(1, fireCount[0],
                "the engine latches on first match; it does not keep firing while triggered");
    }

    @Test
    void disarmStopsReactingAndClearsTheTriggerRecord() {
        Circuit circuit = oneBitSwitch();
        TriggerEngine engine = new TriggerEngine(circuit.simulation());
        engine.arm(new AnalyzerSignalBinding.Vector(circuit.net(), 0, 1), new TriggerCondition.RisingEdge(0));
        circuit.simulation().setInput(circuit.source(), LogicVector.ONE);
        assertEquals(TriggerEngine.Status.TRIGGERED, engine.status());

        engine.disarm();

        assertEquals(TriggerEngine.Status.DISARMED, engine.status());
        assertTrue(engine.triggerTime().isEmpty());
    }

    @Test
    void rearmGoesBackToWatchingWithTheSameBindingAndCondition() {
        Circuit circuit = oneBitSwitch();
        TriggerEngine engine = new TriggerEngine(circuit.simulation());
        engine.arm(new AnalyzerSignalBinding.Vector(circuit.net(), 0, 1), new TriggerCondition.RisingEdge(0));
        circuit.simulation().setInput(circuit.source(), LogicVector.ONE);
        circuit.simulation().setInput(circuit.source(), LogicVector.ZERO);
        assertEquals(TriggerEngine.Status.TRIGGERED, engine.status());

        engine.rearm();
        assertEquals(TriggerEngine.Status.ARMED, engine.status());
        assertTrue(engine.triggerTime().isEmpty());

        circuit.simulation().setInput(circuit.source(), LogicVector.ONE);
        assertEquals(TriggerEngine.Status.TRIGGERED, engine.status(), "the same condition fires again");
    }

    @Test
    void valueTriggerFiresOnABusReachingAnExactTarget() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int netA = builder.addNet(BitWidth.ONE);
        int netB = builder.addNet(BitWidth.ONE);
        int srcA = builder.addComponent("test.switch", "A", SWITCH, NONE, new int[]{netA});
        int srcB = builder.addComponent("test.switch", "B", SWITCH, NONE, new int[]{netB});
        Simulation simulation = new Simulation(builder.build());
        TriggerEngine engine = new TriggerEngine(simulation);
        engine.arm(new AnalyzerSignalBinding.Bits(new int[]{netA, netB}),
                new TriggerCondition.ValueEquals(LogicVector.fromUnsignedLong(0b11, 2)));

        simulation.setInput(srcA, LogicVector.ONE);
        assertEquals(TriggerEngine.Status.ARMED, engine.status(), "only one of the two bits is set so far");

        simulation.setInput(srcB, LogicVector.ONE);
        assertEquals(TriggerEngine.Status.TRIGGERED, engine.status());
    }

    @Test
    void detachStopsTheEngineFromObservingFurtherChanges() {
        Circuit circuit = oneBitSwitch();
        TriggerEngine engine = new TriggerEngine(circuit.simulation());
        engine.arm(new AnalyzerSignalBinding.Vector(circuit.net(), 0, 1), new TriggerCondition.RisingEdge(0));

        engine.detach();
        circuit.simulation().setInput(circuit.source(), LogicVector.ONE);

        assertEquals(TriggerEngine.Status.ARMED, engine.status(),
                "a detached engine must not see simulation events at all");
    }

    @Test
    void aNetChangeUnrelatedToTheWatchedBindingIsIgnored() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int watched = builder.addNet(BitWidth.ONE);
        int other = builder.addNet(BitWidth.ONE);
        builder.addComponent("test.switch", "W", SWITCH, NONE, new int[]{watched});
        int otherSrc = builder.addComponent("test.switch", "O", SWITCH, NONE, new int[]{other});
        Simulation simulation = new Simulation(builder.build());
        TriggerEngine engine = new TriggerEngine(simulation);
        engine.arm(new AnalyzerSignalBinding.Vector(watched, 0, 1), new TriggerCondition.RisingEdge(0));

        simulation.setInput(otherSrc, LogicVector.ONE);

        assertEquals(TriggerEngine.Status.ARMED, engine.status(),
                "a change on an unwatched net must never be mistaken for the watched signal's edge");
    }
}
