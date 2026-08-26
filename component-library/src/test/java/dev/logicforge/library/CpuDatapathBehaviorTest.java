package dev.logicforge.library;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.logicforge.library.behavior.AluBehavior;
import dev.logicforge.library.behavior.RegisterFileBehavior;
import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import org.junit.jupiter.api.Test;

class CpuDatapathBehaviorTest {

    private static final BitWidth WIDTH8 = BitWidth.of(8);

    @Test
    void aluAddAndFlags() {
        BehaviorHarness h = alu(0x7f, 1, 0, 0);
        assertEquals(byteValue(0x80), h.outputVector(0));
        assertEquals(LogicState.ZERO, h.output(1));
        assertEquals(LogicState.ZERO, h.output(2));
        assertEquals(LogicState.ONE, h.output(3));
        assertEquals(LogicState.ONE, h.output(4));
    }

    @Test
    void aluSubAndCarrySanity() {
        BehaviorHarness h = alu(5, 3, 1, 1);
        assertEquals(byteValue(2), h.outputVector(0));
        assertEquals(LogicState.ONE, h.output(2), "carry=1 means no borrow");
        assertEquals(LogicState.ZERO, h.output(3));
    }

    @Test
    void aluAndAndXor() {
        AluBehavior behavior = new AluBehavior(WIDTH8);
        BehaviorHarness h = BehaviorHarness.ofVectors(behavior, 5,
                byteValue(0xCC), byteValue(0xAA), LogicVector.fromUnsignedLong(2, 4), LogicVector.ZERO);
        behavior.evaluate(h);
        assertEquals(byteValue(0x88), h.outputVector(0));
        h.setInput(2, LogicVector.fromUnsignedLong(4, 4));
        behavior.evaluate(h);
        assertEquals(byteValue(0x66), h.outputVector(0));
    }

    @Test
    void aluUnknownOperationDrivesEveryOutputUnknown() {
        AluBehavior behavior = new AluBehavior(WIDTH8);
        BehaviorHarness h = BehaviorHarness.ofVectors(behavior, 5,
                byteValue(1), byteValue(2), LogicVector.repeat(LogicState.UNKNOWN, 4), LogicVector.ZERO);
        behavior.evaluate(h);
        assertEquals(LogicVector.repeat(LogicState.UNKNOWN, 8), h.outputVector(0));
        for (int output = 1; output < 5; output++) {
            assertEquals(LogicState.UNKNOWN, h.output(output));
        }
    }

    @Test
    void registerFileWritesOnRisingEdgeAndReadsTwoPorts() {
        RegisterFileBehavior behavior = new RegisterFileBehavior(WIDTH8, 8);
        BehaviorHarness h = registerFile(behavior);
        write(h, behavior, 2, 0xA5);
        write(h, behavior, 5, 0x3C);
        h.setInput(0, LogicVector.fromUnsignedLong(2, 3));
        h.setInput(1, LogicVector.fromUnsignedLong(5, 3));
        behavior.evaluate(h);
        assertEquals(byteValue(0xA5), h.outputVector(0));
        assertEquals(byteValue(0x3C), h.outputVector(1));
    }

    @Test
    void registerFileUnknownAndOutOfRangeAddressesReadAsX() {
        RegisterFileBehavior behavior = new RegisterFileBehavior(WIDTH8, 6);
        BehaviorHarness h = registerFile(behavior);
        h.setInput(0, LogicVector.repeat(LogicState.UNKNOWN, 3));
        h.setInput(1, LogicVector.fromUnsignedLong(7, 3));
        behavior.evaluate(h);
        LogicVector unknown = LogicVector.repeat(LogicState.UNKNOWN, 8);
        assertEquals(unknown, h.outputVector(0));
        assertEquals(unknown, h.outputVector(1));
    }

    @Test
    void registerFileUnknownWriteEnableMergesWriteAndHold() {
        RegisterFileBehavior behavior = new RegisterFileBehavior(WIDTH8, 8);
        BehaviorHarness h = registerFile(behavior);
        write(h, behavior, 2, 0xA5);

        h.setInput(0, LogicVector.fromUnsignedLong(2, 3));
        h.setInput(2, LogicVector.fromUnsignedLong(2, 3));
        h.setInput(3, byteValue(0xA7));
        h.setInput(4, LogicVector.UNKNOWN);
        h.setInput(5, LogicVector.ONE);
        behavior.evaluate(h);

        assertEquals(LogicVector.of("101001X1"), h.outputVector(0),
                "only the bit changed by the possible write becomes unknown");
    }

    private static BehaviorHarness alu(long a, long b, long op, long cin) {
        AluBehavior behavior = new AluBehavior(WIDTH8);
        BehaviorHarness h = BehaviorHarness.ofVectors(behavior, 5, byteValue(a), byteValue(b),
                LogicVector.fromUnsignedLong(op, 4), LogicVector.fromUnsignedLong(cin, 1));
        behavior.evaluate(h);
        return h;
    }

    private static BehaviorHarness registerFile(RegisterFileBehavior behavior) {
        BehaviorHarness h = BehaviorHarness.ofVectors(behavior, 2,
                LogicVector.fromUnsignedLong(0, 3), LogicVector.fromUnsignedLong(0, 3),
                LogicVector.fromUnsignedLong(0, 3), byteValue(0), LogicVector.ZERO, LogicVector.ZERO);
        behavior.evaluate(h);
        return h;
    }

    private static void write(BehaviorHarness h, RegisterFileBehavior behavior, int address, long data) {
        h.setInput(2, LogicVector.fromUnsignedLong(address, 3));
        h.setInput(3, byteValue(data));
        h.setInput(4, LogicVector.ONE);
        h.setInput(5, LogicVector.ONE);
        behavior.evaluate(h);
        h.setInput(5, LogicVector.ZERO);
        behavior.evaluate(h);
    }

    private static LogicVector byteValue(long value) {
        return LogicVector.fromUnsignedLong(value, 8);
    }
}
