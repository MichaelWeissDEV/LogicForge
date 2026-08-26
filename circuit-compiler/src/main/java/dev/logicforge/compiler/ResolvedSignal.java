package dev.logicforge.compiler;

import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.Simulation;
import java.util.Arrays;
import java.util.List;

/**
 * A resolved runtime binding for a port endpoint: which net or nets actually carry its
 * value. Necessary because a single endpoint no longer necessarily maps to a single net —
 * a component only ever has one net per port <em>while every connection to it is
 * whole-port</em>; the moment any connection touches a bit or a range, the compiler puts
 * that whole port into "bit mode" and gives every one of its bits an independent net (see
 * {@code CircuitCompiler}'s per-port {@code bitMode} tracking). A RANGE endpoint on a
 * bit-mode port therefore genuinely spans several independent nets, and reconstructing its
 * value means reading each of them — there is no single net that "is" {@code DATA[7:4]}.
 *
 * <p>Even on a whole-mode port, a BIT or RANGE endpoint is not its own net either: it is a
 * sub-slice of the port's one net. {@link VectorNet} carries that slice so callers stop
 * re-deriving it by hand.
 */
public sealed interface ResolvedSignal {

    int width();

    LogicVector read(Simulation simulation);

    /** Every underlying runtime net this signal reads from, in no particular order. */
    List<Integer> netIds();

    /**
     * {@code true} for a single contiguous slice of one net (the common, cheap case:
     * {@link VectorNet} or {@link ScalarNet}); {@code false} for a {@link BitVector}
     * reconstructed bit by bit from independent nets.
     */
    boolean isContiguousVectorNet();

    boolean hasDriverConflict(Simulation simulation);

    /**
     * A slice of one net: {@code width} bits starting {@code offset} bits up from that
     * net's own LSB. {@code netWidth} is the underlying net's own width, so the whole-net
     * case ({@code offset == 0 && width == netWidth}) is distinguishable from a genuine
     * sub-slice without a second lookup.
     */
    record VectorNet(int netId, int netWidth, int offset, int width) implements ResolvedSignal {

        public VectorNet {
            if (width <= 0 || offset < 0 || offset + width > netWidth) {
                throw new IllegalArgumentException(
                        "Invalid slice [" + offset + ", " + (offset + width) + ") of a " + netWidth + "-bit net");
            }
        }

        public static VectorNet whole(int netId, int netWidth) {
            return new VectorNet(netId, netWidth, 0, netWidth);
        }

        @Override
        public LogicVector read(Simulation simulation) {
            LogicVector value = simulation.readNet(netId);
            return offset == 0 && width == netWidth ? value : value.slice(offset, width);
        }

        @Override
        public List<Integer> netIds() {
            return List.of(netId);
        }

        @Override
        public boolean isContiguousVectorNet() {
            return true;
        }

        @Override
        public boolean hasDriverConflict(Simulation simulation) {
            return simulation.hasDriverConflict(netId);
        }
    }

    /** A single independent 1-bit net: one bit of a bit-mode port, or any scalar signal. */
    record ScalarNet(int netId) implements ResolvedSignal {

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

        @Override
        public boolean isContiguousVectorNet() {
            return true;
        }

        @Override
        public boolean hasDriverConflict(Simulation simulation) {
            return simulation.hasDriverConflict(netId);
        }
    }

    /**
     * Several independent 1-bit nets reconstructed into one vector, LSB-first: a range or
     * whole read of a bit-mode port, where no single net carries the whole value.
     */
    record BitVector(int[] nets) implements ResolvedSignal {

        public BitVector {
            if (nets.length == 0) {
                throw new IllegalArgumentException("A BitVector needs at least one net");
            }
            nets = nets.clone();
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
        public boolean isContiguousVectorNet() {
            return false;
        }

        @Override
        public boolean hasDriverConflict(Simulation simulation) {
            for (int netId : nets) {
                if (simulation.hasDriverConflict(netId)) {
                    return true;
                }
            }
            return false;
        }
    }
}
