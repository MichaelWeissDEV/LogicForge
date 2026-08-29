package dev.logicforge.ui.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.ElectricalEndpoint;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.compiler.ResolvedSignal;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.LibraryParameters;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.ui.command.AddComponentCommand;
import dev.logicforge.ui.command.ConnectCommand;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A port is never partially "whole" and partially "bit-mode" — the compiler puts a whole
 * port into bit mode (every bit its own net) the moment any connection touches a bit or a
 * range of it (see {@code CircuitCompiler}'s {@code bitMode} tracking), and rejects mixing
 * the two on the same port. {@link ResolvedSignal} exists because that means a RANGE
 * endpoint, or even a WHOLE read of a bit-mode port, can genuinely span several independent
 * runtime nets — there is no single net that "is" {@code DATA[7:4]} once any of DATA's bits
 * were wired individually. These tests exercise both sides of that split directly, without
 * going through hierarchy (that is covered separately by HierarchyRuntimeContextTest).
 */
class SignalBindingTest {

    private final CircuitEditor editor = new CircuitEditor(ComponentRegistry.standard());

    @Test
    void wholeModePortResolvesToOneContiguousVectorNet() {
        ComponentInstance source = busConstant("A5", 8);

        Optional<ResolvedSignal> signal = editor.signalAt(
                PortEndpoint.whole(new PortReference(source.id(), "OUT")));

        assertTrue(signal.isPresent());
        assertTrue(signal.get() instanceof ResolvedSignal.VectorNet);
        assertTrue(signal.get().isContiguousVectorNet());
        assertEquals(8, signal.get().width());
        assertEquals(1, signal.get().netIds().size());
        assertEquals(LogicVector.fromUnsignedLong(0xA5, 8),
                editor.valueAt(PortEndpoint.whole(new PortReference(source.id(), "OUT"))).orElseThrow());
    }

    /** Wiring individual bits of OUT forces the whole port into bit mode. */
    @Test
    void bitModeRangeSpansIndependentNets() {
        ComponentInstance source = busConstant("A5", 8); // 1010_0101
        ComponentInstance probe = add("routing.bus_probe", 200, 0,
                editor.definition("routing.bus_probe").orElseThrow().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8));
        for (int bit = 0; bit < 8; bit++) {
            connectBits(source, "OUT", bit, probe, "IN", bit);
        }

        PortReference out = new PortReference(source.id(), "OUT");
        Optional<ResolvedSignal> signal = editor.signalAt(PortEndpoint.range(out, 7, 4));

        assertTrue(signal.isPresent());
        assertTrue(signal.get() instanceof ResolvedSignal.BitVector,
                "a bit-mode port's range spans independent nets, not one");
        assertFalse(signal.get().isContiguousVectorNet());
        assertEquals(4, signal.get().width());
        assertEquals(4, signal.get().netIds().size());
        assertEquals(4, java.util.Set.copyOf(signal.get().netIds()).size(), "4 genuinely distinct nets");
        assertEquals(LogicVector.fromUnsignedLong(0xA, 4),
                editor.valueAt(PortEndpoint.range(out, 7, 4)).orElseThrow(), "bits 7:4 of 1010_0101 = 1010");
    }

    /** Same bit-mode port, a range whose LSB is not 0 — checks LSB-relative offset handling. */
    @Test
    void bitModeRangeWithShiftedLsbReadsTheCorrectBits() {
        ComponentInstance source = busConstant("A5", 8); // 1010_0101
        ComponentInstance probe = add("routing.bus_probe", 200, 0,
                editor.definition("routing.bus_probe").orElseThrow().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8));
        for (int bit = 0; bit < 8; bit++) {
            connectBits(source, "OUT", bit, probe, "IN", bit);
        }

        PortReference out = new PortReference(source.id(), "OUT");
        // bits 5,4,3,2 of 1010_0101 are 1,0,0,1 -> 0x9
        LogicVector value = editor.valueAt(PortEndpoint.range(out, 5, 2)).orElseThrow();

        assertEquals(LogicVector.fromUnsignedLong(0x9, 4), value);
    }

    @Test
    void bitModeSingleBitIsAScalarNet() {
        ComponentInstance source = busConstant("A5", 8);
        ComponentInstance probe = add("routing.bus_probe", 200, 0,
                editor.definition("routing.bus_probe").orElseThrow().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8));
        for (int bit = 0; bit < 8; bit++) {
            connectBits(source, "OUT", bit, probe, "IN", bit);
        }

        Optional<ResolvedSignal> signal = editor.signalAt(
                PortEndpoint.bit(new PortReference(source.id(), "OUT"), 7));

        assertTrue(signal.isPresent());
        assertTrue(signal.get() instanceof ResolvedSignal.ScalarNet);
        assertEquals(1, signal.get().width());
    }

    /**
     * The connection itself is between two RANGE endpoints (the {@code A[7:4] -> B[3:0]}
     * shape): its width and value must reflect all 4 bits, not just the first net a naive
     * single-net lookup would have found.
     */
    @Test
    void rangeConnectionReportsFullWidthAndValueNotJustTheFirstNet() {
        ComponentInstance source = busConstant("A5", 8); // 1010_0101 -> [7:4] = 1010 = 0xA
        ComponentInstance probe = add("routing.bus_probe", 200, 0,
                editor.definition("routing.bus_probe").orElseThrow().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8));
        UUID connectionId = UUID.randomUUID();
        editor.execute(new ConnectCommand(editor.document(), new Connection(connectionId,
                new ElectricalEndpoint.ComponentEndpoint(
                        PortEndpoint.range(new PortReference(source.id(), "OUT"), 7, 4)),
                new ElectricalEndpoint.ComponentEndpoint(
                        PortEndpoint.range(new PortReference(probe.id(), "IN"), 3, 0)),
                java.util.List.of())));

        assertEquals(4, editor.connectionWidth(connectionId));
        assertEquals(LogicVector.fromUnsignedLong(0xA, 4),
                editor.valueOfConnection(connectionId).orElseThrow());
    }

    /**
     * {@link ResolvedSignal.BitVector} clones its array on the way in; it must also clone
     * on the way out, or a caller mutating the array returned by {@code nets()} would
     * corrupt the record's internal state for every subsequent read.
     */
    @Test
    void bitVectorNetsAccessorIsDefensivelyCopied() {
        ResolvedSignal.BitVector signal = new ResolvedSignal.BitVector(new int[] {1, 2, 3});

        int[] exposed = signal.nets();
        exposed[0] = 999;

        assertEquals(1, signal.nets()[0], "mutating the returned array must not affect the record");
        assertEquals(java.util.List.of(1, 2, 3), signal.netIds());
    }

    // ------------------------------------------------------------------

    private ComponentInstance busConstant(String hexValue, int width) {
        ParameterValues parameters = editor.definition("routing.bus_constant").orElseThrow()
                .defaultParameters()
                .with(LibraryParameters.WIDTH, width)
                .with(LibraryParameters.BUS_CONSTANT_VALUE, hexValue);
        return add("routing.bus_constant", 0, 0, parameters);
    }

    private void connectBits(ComponentInstance from, String fromPort, int fromBit,
                             ComponentInstance to, String toPort, int toBit) {
        editor.execute(new ConnectCommand(editor.document(), Connection.create(
                PortEndpoint.bit(new PortReference(from.id(), fromPort), fromBit),
                PortEndpoint.bit(new PortReference(to.id(), toPort), toBit))));
    }

    private ComponentInstance add(String definitionId, double x, double y, ParameterValues parameters) {
        ComponentInstance instance = ComponentInstance.create(definitionId, new CircuitPoint(x, y), parameters);
        editor.execute(new AddComponentCommand(editor.document(), instance));
        return instance;
    }
}
