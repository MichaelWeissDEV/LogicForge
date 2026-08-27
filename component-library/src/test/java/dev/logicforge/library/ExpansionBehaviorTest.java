package dev.logicforge.library;

import static dev.logicforge.logic.LogicState.HIGH_IMPEDANCE;
import static dev.logicforge.logic.LogicState.ONE;
import static dev.logicforge.logic.LogicState.UNKNOWN;
import static dev.logicforge.logic.LogicState.ZERO;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.logicforge.library.behavior.BusTransformBehavior;
import dev.logicforge.library.behavior.BusTransceiverBehavior;
import dev.logicforge.library.behavior.CharacterOutputBehavior;
import dev.logicforge.library.behavior.GpioBehavior;
import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicVector;
import org.junit.jupiter.api.Test;

class ExpansionBehaviorTest {

    @Test
    void busTransformsPreserveFourStateBitsAndOrdering() {
        var zeroExtend = new BusTransformBehavior(BusTransformBehavior.Kind.ZERO_EXTEND, 4, 8);
        var harness = BehaviorHarness.ofVectors(zeroExtend, 1, LogicVector.of("1X01"));
        zeroExtend.evaluate(harness);
        assertEquals(LogicVector.of("00001X01"), harness.outputVector(0));

        var signExtend = new BusTransformBehavior(BusTransformBehavior.Kind.SIGN_EXTEND, 4, 8);
        harness = BehaviorHarness.ofVectors(signExtend, 1, LogicVector.of("1X01"));
        signExtend.evaluate(harness);
        assertEquals(LogicVector.of("11111X01"), harness.outputVector(0));

        var reverse = new BusTransformBehavior(BusTransformBehavior.Kind.BIT_REVERSE, 4, 4);
        harness = BehaviorHarness.ofVectors(reverse, 1, LogicVector.of("10XZ"));
        reverse.evaluate(harness);
        assertEquals(LogicVector.of("XX01"), harness.outputVector(0),
                "Z is a gate input and therefore becomes X");
    }

    @Test
    void transceiverHasCorrectDirectionEnableAndUnknownSemantics() {
        BusTransceiverBehavior behavior = new BusTransceiverBehavior(BitWidth.of(4));
        BehaviorHarness harness = BehaviorHarness.ofVectors(behavior, 2,
                bit(ONE), bit(ONE), value(0xa), value(0x3));
        behavior.evaluate(harness);
        assertEquals(LogicVector.repeat(HIGH_IMPEDANCE, 4), harness.outputVector(0));
        assertEquals(value(0xa), harness.outputVector(1));

        harness.setInput(0, ZERO);
        behavior.evaluate(harness);
        assertEquals(value(0x3), harness.outputVector(0));
        assertEquals(LogicVector.repeat(HIGH_IMPEDANCE, 4), harness.outputVector(1));

        harness.setInput(1, ZERO);
        behavior.evaluate(harness);
        assertEquals(LogicVector.repeat(HIGH_IMPEDANCE, 4), harness.outputVector(0));
        assertEquals(LogicVector.repeat(HIGH_IMPEDANCE, 4), harness.outputVector(1));

        harness.setInput(1, UNKNOWN);
        behavior.evaluate(harness);
        assertEquals(LogicVector.repeat(UNKNOWN, 4), harness.outputVector(0));
        assertEquals(LogicVector.repeat(HIGH_IMPEDANCE, 4), harness.outputVector(1),
                "ENABLE=X,DIR=0 leaves the B side unconditionally released");

        harness.setInput(0, ONE);
        harness.setInput(2, LogicVector.repeat(HIGH_IMPEDANCE, 4));
        behavior.evaluate(harness);
        assertEquals(LogicVector.repeat(HIGH_IMPEDANCE, 4), harness.outputVector(0),
                "ENABLE=X,DIR=1 leaves the A side unconditionally released");
        assertEquals(LogicVector.repeat(HIGH_IMPEDANCE, 4), harness.outputVector(1),
                "merging Z with an already-Z source preserves Z");
    }

    @Test
    void gpioDrivesOutputBitsAndReadsPins() {
        GpioBehavior behavior = new GpioBehavior(BitWidth.of(4));
        BehaviorHarness harness = BehaviorHarness.ofVectors(behavior, 2,
                value(0b1010), value(0b1100), value(0b0110));
        behavior.evaluate(harness);
        assertEquals(LogicVector.of("10ZZ"), harness.outputVector(0));
        assertEquals(value(0b0110), harness.outputVector(1));
    }

    @Test
    void characterOutputBuffersTextAndSnapshotsIt() {
        CharacterOutputBehavior behavior = new CharacterOutputBehavior(4);
        BehaviorHarness harness = BehaviorHarness.ofVectors(behavior, 0,
                LogicVector.fromUnsignedLong('H', 8), bit(ONE), bit(ONE), bit(ZERO), bit(ZERO));
        behavior.evaluate(harness);
        harness.setInput(3, ONE);
        behavior.evaluate(harness);
        assertEquals("H", behavior.debugSnapshot(harness.state()).textValues().get("TEXT"));
        harness.setInput(4, ONE);
        behavior.evaluate(harness);
        assertEquals("", behavior.debugSnapshot(harness.state()).textValues().get("TEXT"));

        harness.setInput(4, ZERO);
        harness.setInput(3, ZERO);
        harness.setInput(0, LogicVector.fromUnsignedLong('X', 8));
        behavior.evaluate(harness);
        harness.setInput(3, ONE);
        behavior.evaluate(harness);
        harness.setInput(4, UNKNOWN);
        behavior.evaluate(harness);
        harness.setInput(4, ZERO);
        behavior.evaluate(harness);
        assertEquals("<unknown>", behavior.debugSnapshot(harness.state()).textValues().get("TEXT"),
                "an ambiguous reset must not resurrect a known text buffer");
    }

    @Test
    void registryContainsTheCompletedReusableFamilies() {
        ComponentRegistry registry = ComponentRegistry.standard();
        for (String id : new String[]{
                "routing.zero_extend", "routing.sign_extend", "routing.truncate",
                "routing.bit_reverse", "routing.byte_swap", "routing.bus_transceiver",
                "routing.bus_isolator", "routing.byte_lane_splitter", "routing.open_drain",
                "routing.pullup_bus", "routing.pulldown_bus", "arithmetic.barrel_shifter",
                "arithmetic.carry_lookahead_adder", "arithmetic.multiplier_unsigned",
                "arithmetic.absolute", "system.gpio", "system.character_output"}) {
            registry.require(id);
        }
    }

    private static LogicVector bit(dev.logicforge.logic.LogicState state) {
        return LogicVector.single(state);
    }

    private static LogicVector value(int value) {
        return LogicVector.fromUnsignedLong(value, 4);
    }
}
