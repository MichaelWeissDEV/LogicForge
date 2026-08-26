package dev.logicforge.library;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.library.behavior.AddressDecoderBehavior;
import dev.logicforge.library.behavior.BusConcatBehavior;
import dev.logicforge.library.behavior.BusSliceBehavior;
import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import org.junit.jupiter.api.Test;

class ProcessorRoutingBehaviorTest {

    @Test
    void busConcatPlacesLowInputAtBitZero() {
        BusConcatBehavior behavior = new BusConcatBehavior(BitWidth.of(8), BitWidth.of(8));
        BehaviorHarness harness = BehaviorHarness.ofVectors(behavior, 1,
                LogicVector.fromUnsignedLong(0x34, 8),
                LogicVector.fromUnsignedLong(0x12, 8));

        behavior.evaluate(harness);

        assertEquals(LogicVector.fromUnsignedLong(0x1234, 16), harness.outputVector(0));
    }

    @Test
    void busSliceUsesLsbRelativeOffset() {
        BusSliceBehavior behavior = new BusSliceBehavior(BitWidth.of(16), 4, BitWidth.of(8));
        BehaviorHarness harness = BehaviorHarness.ofVectors(behavior, 1,
                LogicVector.fromUnsignedLong(0xabcd, 16));

        behavior.evaluate(harness);

        assertEquals(LogicVector.fromUnsignedLong(0xbc, 8), harness.outputVector(0));
    }

    @Test
    void addressDecoderIgnoresUnmaskedUnknownsAndPropagatesRelevantUnknowns() {
        AddressDecoderBehavior behavior = new AddressDecoderBehavior(BitWidth.of(16),
                LogicVector.fromUnsignedLong(0x2000, 16),
                LogicVector.fromUnsignedLong(0xf000, 16));
        LogicVector matching = LogicVector.fromUnsignedLong(0x2345, 16);
        BehaviorHarness harness = BehaviorHarness.ofVectors(behavior, 1,
                matching.withBit(0, LogicState.UNKNOWN));

        behavior.evaluate(harness);
        assertEquals(LogicVector.ONE, harness.outputVector(0),
                "unknown low address bits are outside the mask");

        harness.setInput(0, matching.withBit(13, LogicState.UNKNOWN));
        behavior.evaluate(harness);
        assertEquals(LogicVector.UNKNOWN, harness.outputVector(0));

        harness.setInput(0, matching.withBit(12, LogicState.ONE)
                .withBit(13, LogicState.UNKNOWN));
        behavior.evaluate(harness);
        assertEquals(LogicVector.ZERO, harness.outputVector(0),
                "a known relevant mismatch dominates another relevant unknown");
    }

    @Test
    void standardRegistryExposesProcessorRoutingComponentsWithConfiguredWidths() {
        ComponentRegistry registry = ComponentRegistry.standard();
        ParameterValues parameters = registry.require("routing.bus_concat").definition()
                .defaultParameters()
                .with(LibraryParameters.LOW_WIDTH, 8)
                .with(LibraryParameters.HIGH_WIDTH, 16);

        var ports = registry.require("routing.bus_concat").definition().ports(parameters);

        assertEquals(24, ports.stream().filter(port -> port.name().equals("OUT"))
                .findFirst().orElseThrow().width().bits());
        registry.require("routing.bus_slice");
        registry.require("routing.address_decoder");
    }
}
