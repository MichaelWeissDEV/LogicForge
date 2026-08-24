package dev.logicforge.library;

import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;

/** Evaluates a single behaviour with fixed inputs, without building a whole circuit. */
final class BehaviorHarness implements ComponentContext {

    private final LogicVector[] inputs;
    private final LogicVector[] outputs;
    private final ComponentRuntimeState state;

    private BehaviorHarness(ComponentBehavior behavior, int outputCount, LogicVector... inputs) {
        this.inputs = inputs;
        this.outputs = new LogicVector[outputCount];
        this.state = behavior.createState();
    }

    /** Runs {@code behavior} with the given single-bit inputs and returns its output. */
    static LogicState evaluate(ComponentBehavior behavior, LogicState... inputs) {
        LogicVector[] vectors = new LogicVector[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            vectors[i] = LogicVector.single(inputs[i]);
        }
        BehaviorHarness harness = new BehaviorHarness(behavior, 1, vectors);
        behavior.evaluate(harness);
        return harness.outputs[0].singleBit();
    }

    /** Runs a stateful behaviour, letting the caller touch its state first. */
    static BehaviorHarness of(ComponentBehavior behavior, LogicState... inputs) {
        LogicVector[] vectors = new LogicVector[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            vectors[i] = LogicVector.single(inputs[i]);
        }
        return new BehaviorHarness(behavior, 1, vectors);
    }

    LogicState run(ComponentBehavior behavior) {
        behavior.evaluate(this);
        return outputs[0].singleBit();
    }

    @Override
    public ComponentRuntimeState state() {
        return state;
    }

    @Override
    public int inputCount() {
        return inputs.length;
    }

    @Override
    public LogicVector readInput(int index) {
        return inputs[index];
    }

    @Override
    public int outputCount() {
        return outputs.length;
    }

    @Override
    public void driveOutput(int index, LogicVector value) {
        outputs[index] = value;
    }

    @Override
    public long time() {
        return 0;
    }

    @Override
    public int deltaCycle() {
        return 0;
    }
}
