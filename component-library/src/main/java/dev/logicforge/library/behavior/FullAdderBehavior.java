package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/**
 * A single-bit full adder: SUM = A XOR B XOR CIN, COUT = majority(A, B, CIN). Ports are
 * {@code A, B, CIN, SUM, COUT}.
 */
public final class FullAdderBehavior implements ComponentBehavior {

    public static final FullAdderBehavior INSTANCE = new FullAdderBehavior();

    private FullAdderBehavior() {
    }

    @Override
    public void evaluate(ComponentContext context) {
        LogicState a = context.readInput(0).singleBit();
        LogicState b = context.readInput(1).singleBit();
        LogicState cin = context.readInput(2).singleBit();
        LogicState sum = LogicOperations.xor(LogicOperations.xor(a, b), cin);
        LogicState cout = LogicOperations.or(LogicOperations.or(
                LogicOperations.and(a, b), LogicOperations.and(b, cin)), LogicOperations.and(a, cin));
        context.driveOutput(0, LogicVector.single(sum));
        context.driveOutput(1, LogicVector.single(cout));
    }
}
