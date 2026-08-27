package dev.logicforge.simulation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SimulationSessionTest {

    @Test
    void sharesRunStateAndSurvivesSimulationReplacement() {
        Simulation first = new Simulation(CompiledCircuit.builder().build());
        SimulationSession session = new SimulationSession(first);
        AtomicInteger changes = new AtomicInteger();
        session.addListener(changes::incrementAndGet);

        session.setRunning(false);
        assertFalse(first.isRunning());
        assertFalse(session.isRunning());

        Simulation replacement = new Simulation(CompiledCircuit.builder().build());
        session.replaceSimulation(replacement);
        assertSame(replacement, session.simulation());
        assertFalse(replacement.isRunning());

        session.setRunning(true);
        assertTrue(replacement.isRunning());
        assertTrue(changes.get() >= 3);
    }
}
