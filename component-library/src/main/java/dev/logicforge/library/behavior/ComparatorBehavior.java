package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import java.util.OptionalLong;

/**
 * An unsigned magnitude comparator: LT, EQ and GT for {@code A} compared to {@code B}. If
 * either side is not fully defined, all three outputs are X — a comparison cannot be
 * partially known.
 *
 * <p>Ports are {@code A, B} in, {@code LT, EQ, GT} out.
 */
public final class ComparatorBehavior implements ComponentBehavior {

    public static final ComparatorBehavior INSTANCE = new ComparatorBehavior();

    private ComparatorBehavior() {
    }

    @Override
    public void evaluate(ComponentContext context) {
        OptionalLong a = context.readInput(0).toUnsignedLong();
        OptionalLong b = context.readInput(1).toUnsignedLong();
        if (a.isEmpty() || b.isEmpty()) {
            context.driveOutput(0, LogicVector.UNKNOWN);
            context.driveOutput(1, LogicVector.UNKNOWN);
            context.driveOutput(2, LogicVector.UNKNOWN);
            return;
        }
        int cmp = Long.compareUnsigned(a.getAsLong(), b.getAsLong());
        context.driveOutput(0, LogicVector.single(LogicState.of(cmp < 0)));
        context.driveOutput(1, LogicVector.single(LogicState.of(cmp == 0)));
        context.driveOutput(2, LogicVector.single(LogicState.of(cmp > 0)));
    }
}
