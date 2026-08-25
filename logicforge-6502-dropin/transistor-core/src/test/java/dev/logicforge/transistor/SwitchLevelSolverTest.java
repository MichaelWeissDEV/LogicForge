package dev.logicforge.transistor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

final class SwitchLevelSolverTest {
    @Test
    void solvesNmosInverter() {
        TransistorNetlist source = TransistorNetlist.builder(4, 1, 0)
                .name("vss", 0).name("vcc", 1).name("out", 2).name("in", 3)
                .pullUp(2)
                .transistor(new SwitchTransistor("t0", 3, 2, 0))
                .build();
        SwitchLevelSolver solver = new SwitchLevelSolver(CompiledTransistorNetlist.compile(source));

        solver.drive("in", DriveState.LOW);
        solver.settle();
        assertEquals(NodeState.HIGH, solver.state("out"));

        solver.drive("in", DriveState.HIGH);
        solver.settle();
        assertEquals(NodeState.LOW, solver.state("out"));
    }
}
