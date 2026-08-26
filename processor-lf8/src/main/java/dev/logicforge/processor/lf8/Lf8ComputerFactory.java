package dev.logicforge.processor.lf8;

import dev.logicforge.circuit.document.CircuitProject;

/** Creates a complete, executable LF-8 prototype project. */
public final class Lf8ComputerFactory {

    private Lf8ComputerFactory() {
    }

    public static CircuitProject create(int... program) {
        return Lf8CircuitFactory.createProject(program);
    }

    public static CircuitProject create(Lf8ImplementationMode mode, int... program) {
        return Lf8CircuitFactory.createProject(mode, program);
    }

    /** Creates a computer with explicit little-endian vector-table contents in external ROM. */
    public static CircuitProject createWithVectors(int[] program, int irqVector, int nmiVector,
                                                   int resetVector) {
        return Lf8CircuitFactory.createProject(program, irqVector, nmiVector, resetVector);
    }

    public static CircuitProject createWithVectors(Lf8ImplementationMode mode, int[] program,
                                                   int irqVector, int nmiVector,
                                                   int resetVector) {
        return Lf8CircuitFactory.createProject(program, irqVector, nmiVector, resetVector, mode);
    }
}
