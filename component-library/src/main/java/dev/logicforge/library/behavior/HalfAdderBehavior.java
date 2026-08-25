package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/** A single-bit half adder: SUM = A XOR B, CARRY = A AND B. Ports are {@code A, B, SUM, CARRY}. */
public final class HalfAdderBehavior implements ComponentBehavior {

    public static final HalfAdderBehavior INSTANCE = new HalfAdderBehavior();

    private HalfAdderBehavior() {
    }

    @Override
    public void evaluate(ComponentContext context) {
        var a = context.readInput(0).singleBit();
        var b = context.readInput(1).singleBit();
        context.driveOutput(0, LogicVector.single(LogicOperations.xor(a, b)));
        context.driveOutput(1, LogicVector.single(LogicOperations.and(a, b)));
    }
}
