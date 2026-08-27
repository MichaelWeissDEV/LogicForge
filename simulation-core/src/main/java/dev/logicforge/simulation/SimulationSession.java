package dev.logicforge.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Headless shared ownership of a live simulation and its run/step control state. */
public final class SimulationSession {

    private final List<Runnable> listeners = new ArrayList<>();
    private Simulation simulation;
    private boolean running;

    public SimulationSession(Simulation simulation) {
        this.simulation = Objects.requireNonNull(simulation, "simulation");
        this.running = simulation.isRunning();
    }

    public Simulation simulation() {
        return simulation;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        if (this.running == running && simulation.isRunning() == running) {
            return;
        }
        this.running = running;
        simulation.setRunning(running);
        publishChange();
    }

    public boolean stepEvent() {
        setRunning(false);
        boolean advanced = simulation.step();
        publishChange();
        return advanced;
    }

    public boolean stepTime() {
        setRunning(false);
        boolean advanced = simulation.advanceToNextEvent();
        publishChange();
        return advanced;
    }

    public void reset() {
        simulation.reset();
        publishChange();
    }

    /** Rebinds after recompilation while preserving the shared run/pause intent. */
    public void replaceSimulation(Simulation replacement) {
        simulation = Objects.requireNonNull(replacement, "replacement");
        simulation.setRunning(running);
        publishChange();
    }

    /** Publishes a mutation performed through a specialized controller. */
    public void simulationChanged() {
        publishChange();
    }

    public void addListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void publishChange() {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }
}
