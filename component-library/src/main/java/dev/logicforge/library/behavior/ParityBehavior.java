package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/**
 * Parity generator and checker, sharing the same even-parity computation: XOR-fold every
 * bit of {@code A}. Any undefined bit makes the fold X, the same way multi-input XOR has no
 * controlling value in {@link LogicOperations}.
 *
 * <p>The generator's port is {@code A} in, {@code P} out: {@code P} is the bit that makes
 * the total number of ones in {@code A} and {@code P} together even.
 *
 * <p>The checker's ports are {@code A, P} in, {@code ERROR} out: 1 if the parity bit does
 * not match what {@code A} implies.
 *
 * @param checker {@code false} for the generator, {@code true} for the checker
 */
public record ParityBehavior(boolean checker) implements ComponentBehavior {

    @Override
    public void evaluate(ComponentContext context) {
        LogicState parity = fold(context.readInput(0));
        if (!checker) {
            context.driveOutput(0, LogicVector.single(parity));
            return;
        }
        LogicState given = context.readInput(1).singleBit();
        context.driveOutput(0, LogicVector.single(LogicOperations.xor(parity, given)));
    }

    private LogicState fold(LogicVector value) {
        LogicState parity = LogicState.ZERO;
        for (int i = 0; i < value.width(); i++) {
            parity = LogicOperations.xor(parity, value.getBit(i));
        }
        return parity;
    }
}
