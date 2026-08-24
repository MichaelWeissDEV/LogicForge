package dev.logicforge.simulation;

import java.util.List;

/**
 * Thrown when a circuit does not settle — the classic case being an inverter whose output
 * feeds its own input. The simulator gives up after a fixed number of delta cycles instead
 * of freezing the application.
 */
public class SimulationOscillationException extends SimulationException {

    private final int deltaCycles;
    private final List<Integer> oscillatingNets;

    public SimulationOscillationException(int deltaCycles, List<Integer> oscillatingNets) {
        super("Combinational oscillation detected: no stable state after " + deltaCycles
                + " delta cycles (nets " + oscillatingNets + ")");
        this.deltaCycles = deltaCycles;
        this.oscillatingNets = List.copyOf(oscillatingNets);
    }

    public int deltaCycles() {
        return deltaCycles;
    }

    /** Runtime ids of the nets that were still changing when the limit was reached. */
    public List<Integer> oscillatingNets() {
        return oscillatingNets;
    }
}
