package dev.logicforge.simulation;

/** Where the simulation currently stands. */
public enum SimulationStatus {

    /** Nothing left to propagate. */
    STABLE("Stable"),
    /** Events are waiting; the circuit has not settled yet. */
    PENDING("Pending"),
    /** Propagation did not settle within the delta cycle limit. */
    OSCILLATING("Oscillating");

    private final String displayName;

    SimulationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
