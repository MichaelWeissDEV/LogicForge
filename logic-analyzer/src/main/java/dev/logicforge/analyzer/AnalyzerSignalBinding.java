package dev.logicforge.analyzer;

import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.Simulation;
import java.util.Arrays;
import java.util.List;

/**
 * Which runtime net or nets a watched analyzer signal actually reads from. A logical signal
 * a user watches — {@code DATA[7:0]}, {@code DATA[3]}, {@code DATA[7:4]} — does not always
 * correspond to a single net: on a bit-mode port (every bit its own independent net, see
 * {@code CircuitCompiler}'s per-port {@code bitMode} tracking) a whole or range read spans
 * several. This module has no dependency on the compiler or the UI — it is the analyzer's
 * own, minimal runtime-binding concept; the UI is responsible for converting a compiler
 * {@code ResolvedSignal} into one of these before handing it to {@link SignalRecorder}.
 */
public sealed interface AnalyzerSignalBinding {

    int width();

    LogicVector read(Simulation simulation);

    /** Every underlying runtime net this signal reads from, in no particular order. */
    List<Integer> netIds();

    /** A slice of one net: {@code width} bits starting {@code offset} bits up from its LSB. */
    record Vector(int netId, int offset, int width) implements AnalyzerSignalBinding {

        public Vector {
            if (width <= 0 || offset < 0) {
                throw new IllegalArgumentException(
                        "Invalid slice [" + offset + ", " + (offset + width) + ")");
            }
        }

        public static Vector whole(int netId, int netWidth) {
            return new Vector(netId, 0, netWidth);
        }

        @Override
        public LogicVector read(Simulation simulation) {
            LogicVector value = simulation.readNet(netId);
            return offset == 0 && width == value.width() ? value : value.slice(offset, width);
        }

        @Override
        public List<Integer> netIds() {
            return List.of(netId);
        }
    }

    /** A single independent 1-bit net: one bit of a bit-mode port, or any scalar signal. */
    record Scalar(int netId) implements AnalyzerSignalBinding {

        @Override
        public int width() {
            return 1;
        }

        @Override
        public LogicVector read(Simulation simulation) {
            return simulation.readNet(netId);
        }

        @Override
        public List<Integer> netIds() {
            return List.of(netId);
        }
    }

    /**
     * Several independent 1-bit nets reconstructed into one vector, LSB-first: a range or
     * whole read of a bit-mode port, where no single net carries the whole value.
     */
    record Bits(int[] nets) implements AnalyzerSignalBinding {

        public Bits {
            if (nets.length == 0) {
                throw new IllegalArgumentException("A Bits binding needs at least one net");
            }
            nets = nets.clone();
        }

        /** Defensive copy — the canonical constructor clones on the way in. */
        @Override
        public int[] nets() {
            return nets.clone();
        }

        @Override
        public int width() {
            return nets.length;
        }

        @Override
        public LogicVector read(Simulation simulation) {
            LogicState[] bits = new LogicState[nets.length];
            for (int i = 0; i < nets.length; i++) {
                LogicVector value = simulation.readNet(nets[i]);
                bits[i] = value.width() == 1 ? value.singleBit() : value.getBit(0);
            }
            return LogicVector.ofLsbFirst(bits);
        }

        @Override
        public List<Integer> netIds() {
            return Arrays.stream(nets).boxed().toList();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Bits bits && Arrays.equals(nets, bits.nets);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(nets);
        }

        @Override
        public String toString() {
            return "Bits" + Arrays.toString(nets);
        }
    }
}
