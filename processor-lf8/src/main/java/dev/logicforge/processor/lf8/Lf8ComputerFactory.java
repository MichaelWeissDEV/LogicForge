package dev.logicforge.processor.lf8;

import dev.logicforge.circuit.document.CircuitProject;

/** Creates a complete, executable LF-8 prototype project. */
public final class Lf8ComputerFactory {

    private Lf8ComputerFactory() {
    }

    public static CircuitProject create(int... program) {
        return Lf8CircuitFactory.createProject(program);
    }
}
