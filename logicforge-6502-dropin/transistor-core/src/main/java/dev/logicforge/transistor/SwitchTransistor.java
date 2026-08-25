package dev.logicforge.transistor;

/** One idealized NMOS switch: a HIGH gate electrically connects terminal A and B. */
public record SwitchTransistor(String name, int gate, int terminalA, int terminalB) {
    public SwitchTransistor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Transistor needs a name");
        }
        if (gate < 0 || terminalA < 0 || terminalB < 0) {
            throw new IllegalArgumentException("Node ids must be non-negative");
        }
    }
}
