package dev.logicforge.transistor;

/** Guard against malformed/oscillating switch networks. */
public final class SimulationDidNotSettleException extends RuntimeException {
    public SimulationDidNotSettleException(int operations) {
        super("Transistor network did not settle after " + operations + " group recalculations");
    }
}
