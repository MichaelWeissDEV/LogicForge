package dev.logicforge.library;

import static dev.logicforge.logic.LogicState.ONE;
import static dev.logicforge.logic.LogicState.UNKNOWN;
import static dev.logicforge.logic.LogicState.ZERO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.library.behavior.DualPortRamBehavior;
import dev.logicforge.library.behavior.QueueStorageBehavior;
import dev.logicforge.library.behavior.SynchronousRamBehavior;
import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicVector;
import org.junit.jupiter.api.Test;

class StorageBehaviorTest {
    private static final BitWidth BYTE = BitWidth.of(8);
    private static final BitWidth ADDRESS = BitWidth.of(4);

    @Test
    void synchronousRamWritesReadsResetsAndRestores() {
        SynchronousRamBehavior behavior = new SynchronousRamBehavior(ADDRESS, BYTE);
        BehaviorHarness harness = BehaviorHarness.ofVectors(behavior, 1,
                word(2, 4), word(0x5a, 8), bit(ONE), bit(ZERO), bit(ZERO));
        behavior.evaluate(harness);
        harness.setInput(3, ONE);
        behavior.evaluate(harness);
        Object snapshot = harness.state().snapshot();

        harness.setInput(3, ZERO);
        harness.setInput(2, ZERO);
        behavior.evaluate(harness);
        harness.setInput(3, ONE);
        behavior.evaluate(harness);
        assertEquals(word(0x5a, 8), harness.outputVector(0));

        harness.setInput(4, ONE);
        behavior.evaluate(harness);
        assertEquals(word(0, 8), harness.outputVector(0));
        harness.state().restore(snapshot);
        assertEquals(word(0x5a, 8), harness.state().memoryPage(2, 1).wordAt(2));
    }

    @Test
    void dualPortSameAddressDifferingWritesStoreUnknown() {
        DualPortRamBehavior behavior = new DualPortRamBehavior(ADDRESS, BYTE);
        BehaviorHarness harness = BehaviorHarness.ofVectors(behavior, 2,
                word(3, 4), word(0xaa, 8), bit(ONE), bit(ZERO),
                word(3, 4), word(0x55, 8), bit(ONE), bit(ZERO), bit(ZERO));
        behavior.evaluate(harness);
        harness.setInput(3, ONE);
        harness.setInput(7, ONE);
        behavior.evaluate(harness);
        assertEquals(LogicVector.repeat(UNKNOWN, BYTE), harness.outputVector(0));
        assertEquals(LogicVector.repeat(UNKNOWN, BYTE), harness.outputVector(1));
        assertTrue(harness.state().memoryRevision() > 0);

        harness.setInput(8, UNKNOWN);
        behavior.evaluate(harness);
        assertEquals(LogicVector.repeat(UNKNOWN, BYTE), harness.outputVector(0));
    }

    @Test
    void fifoAndStackHaveDistinctOrderingAndSnapshotSafeState() {
        QueueStorageBehavior fifo = new QueueStorageBehavior(
                QueueStorageBehavior.Kind.FIFO, BYTE, 3);
        BehaviorHarness queue = queueHarness(fifo);
        push(fifo, queue, 1);
        Object snapshot = queue.state().snapshot();
        push(fifo, queue, 2);
        assertEquals(word(1, 8), queue.outputVector(0));
        queue.state().restore(snapshot);
        fifo.evaluate(queue);
        assertEquals(word(1, 8), queue.outputVector(0));

        QueueStorageBehavior stack = new QueueStorageBehavior(
                QueueStorageBehavior.Kind.STACK, BYTE, 3);
        BehaviorHarness lifo = queueHarness(stack);
        push(stack, lifo, 1);
        push(stack, lifo, 2);
        assertEquals(word(2, 8), lifo.outputVector(0));
        assertEquals(3L, stack.debugSnapshot(lifo.state()).counters().get("capacity"));
    }

    @Test
    void registryContainsEveryStorageFamily() {
        ComponentRegistry registry = ComponentRegistry.standard();
        for (String id : new String[]{"memory.synchronous_ram", "memory.dual_port_ram",
                "memory.fifo", "memory.stack"}) {
            registry.require(id);
        }
    }

    private static BehaviorHarness queueHarness(QueueStorageBehavior behavior) {
        BehaviorHarness harness = BehaviorHarness.ofVectors(behavior, 4,
                word(0, 8), bit(ZERO), bit(ZERO), bit(ZERO), bit(ZERO));
        behavior.evaluate(harness);
        return harness;
    }

    private static void push(QueueStorageBehavior behavior, BehaviorHarness harness, int value) {
        harness.setInput(0, word(value, 8));
        harness.setInput(1, ONE);
        harness.setInput(3, ZERO);
        behavior.evaluate(harness);
        harness.setInput(3, ONE);
        behavior.evaluate(harness);
    }

    private static LogicVector bit(dev.logicforge.logic.LogicState state) {
        return LogicVector.single(state);
    }

    private static LogicVector word(int value, int width) {
        return LogicVector.fromUnsignedLong(value, width);
    }
}
