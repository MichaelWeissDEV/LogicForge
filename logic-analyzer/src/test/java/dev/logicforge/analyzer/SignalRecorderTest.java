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
import java.util.List;
import org.junit.jupiter.api.Test;

class SignalRecorderTest {

    private static final int[] NONE = new int[0];

    /** A minimal settable source, so tests can drive net changes without the component library. */
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

    @Test
    void recordsTransitionsInOrderWithTimeAndValue() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int net = builder.addNet(BitWidth.ONE);
        int src = builder.addComponent("test.switch", "SW", SWITCH, NONE, new int[]{net});
        Simulation simulation = new Simulation(builder.build());
        SignalRecorder recorder = new SignalRecorder(simulation);
        SignalTrace trace = recorder.watch(net, "A");

        simulation.setInput(src, LogicVector.ONE);
        simulation.setInput(src, LogicVector.ZERO);
        simulation.setInput(src, LogicVector.ONE);

        List<SignalTransition> transitions = trace.transitions();
        assertEquals(4, transitions.size(), "the initial seed plus three real changes");
        assertEquals(0, transitions.get(0).time());
        assertEquals(LogicVector.ZERO, transitions.get(0).value());
        assertEquals(1, transitions.get(1).time());
        assertEquals(LogicVector.ONE, transitions.get(1).value());
        assertEquals(2, transitions.get(2).time());
        assertEquals(LogicVector.ZERO, transitions.get(2).value());
        assertEquals(3, transitions.get(3).time());
        assertEquals(LogicVector.ONE, transitions.get(3).value());
    }

    @Test
    void capturingOffSkipsChangesAndResumingReSeedsTheCurrentValue() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int net = builder.addNet(BitWidth.ONE);
        int src = builder.addComponent("test.switch", "SW", SWITCH, NONE, new int[]{net});
        Simulation simulation = new Simulation(builder.build());
        SignalRecorder recorder = new SignalRecorder(simulation);
        SignalTrace trace = recorder.watch(net, "A");

        recorder.setCapturing(false);
        simulation.setInput(src, LogicVector.ONE);
        assertEquals(1, trace.transitions().size(), "the change while stopped was not recorded");

        recorder.setCapturing(true);
        List<SignalTransition> afterResume = trace.transitions();
        assertEquals(2, afterResume.size(), "resuming re-seeds the value that was missed");
        assertEquals(LogicVector.ONE, afterResume.get(1).value());
    }

    @Test
    void multipleNetsAreWatchedIndependentlyAndInAddOrder() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int netA = builder.addNet(BitWidth.ONE);
        int netB = builder.addNet(BitWidth.ONE);
        int srcA = builder.addComponent("test.switch", "A", SWITCH, NONE, new int[]{netA});
        int srcB = builder.addComponent("test.switch", "B", SWITCH, NONE, new int[]{netB});
        Simulation simulation = new Simulation(builder.build());
        SignalRecorder recorder = new SignalRecorder(simulation);
        recorder.watch(netB, "B");
        recorder.watch(netA, "A");

        assertEquals(List.of("B", "A"), recorder.traces().stream().map(SignalTrace::label).toList());

        simulation.setInput(srcA, LogicVector.ONE);
        assertEquals(2, recorder.trace(netA).orElseThrow().transitions().size());
        assertEquals(1, recorder.trace(netB).orElseThrow().transitions().size(), "B did not change");

        recorder.unwatch(netB);
        assertFalse(recorder.isWatching(netB));
        assertTrue(recorder.isWatching(netA));

        simulation.setInput(srcB, LogicVector.ONE);
        assertEquals(1, recorder.traces().size(), "B is no longer tracked");
    }

    @Test
    void clearErasesHistoryButKeepsTheCurrentValue() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int net = builder.addNet(BitWidth.ONE);
        int src = builder.addComponent("test.switch", "SW", SWITCH, NONE, new int[]{net});
        Simulation simulation = new Simulation(builder.build());
        SignalRecorder recorder = new SignalRecorder(simulation);
        SignalTrace trace = recorder.watch(net, "A");
        simulation.setInput(src, LogicVector.ONE);

        recorder.clear();

        assertEquals(1, trace.transitions().size());
        assertEquals(LogicVector.ONE, trace.transitions().get(0).value());
    }
}
