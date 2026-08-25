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
        return of(behavior, 1, inputs);
    }

    /** Runs a stateful, multi-output behaviour over several evaluations. */
    static BehaviorHarness of(ComponentBehavior behavior, int outputCount, LogicState... inputs) {
        LogicVector[] vectors = new LogicVector[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            vectors[i] = LogicVector.single(inputs[i]);
        }
        return new BehaviorHarness(behavior, outputCount, vectors);
    }

    LogicState run(ComponentBehavior behavior) {
        behavior.evaluate(this);
        return outputs[0].singleBit();
    }

    /** Changes one input between evaluations, e.g. to drive a clock edge. */
    void setInput(int index, LogicState value) {
        inputs[index] = LogicVector.single(value);
    }

    /** The value output port {@code index} was last driven with. */
    LogicState output(int index) {
        return outputs[index].singleBit();
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
    public void driveOutputAfter(int index, long delay, LogicVector value) {
        if (delay <= 0) {
            throw new IllegalArgumentException("delay must be positive, was " + delay);
        }
        outputs[index] = value;
    }

    @Override
    public void driveOutputAt(int index, long time, LogicVector value) {
        if (time <= 0) {
            throw new IllegalArgumentException("time must be after the current time, was " + time);
        }
        outputs[index] = value;
    }

    @Override
    public void scheduleWakeup(long time) {
        if (time <= 0) {
            throw new IllegalArgumentException("time must be after the current time, was " + time);
        }
        // The harness evaluates a behaviour once at a fixed time 0; there is no queue to
        // schedule a future wakeup on.
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
