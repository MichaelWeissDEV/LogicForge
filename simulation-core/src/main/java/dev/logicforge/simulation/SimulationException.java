package dev.logicforge.simulation;

/** Base class for failures that happen while a circuit is running. */
public class SimulationException extends RuntimeException {

    public SimulationException(String message) {
        super(message);
    }
}
