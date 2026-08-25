package dev.logicforge.simulation;

import dev.logicforge.logic.BitWidth;

/** Maps one logical behavior input port to its compiled electrical net or scalar bit nets. */
public sealed interface CompiledInputBinding
        permits CompiledInputBinding.Whole, CompiledInputBinding.Bits {

    int width();

    /** Fast path for a logical port represented by one vector net. */
    record Whole(int netId, BitWidth bitWidth) implements CompiledInputBinding {
        public Whole {
            if (netId < 0 || bitWidth == null) {
                throw new IllegalArgumentException("A whole input binding needs a net and width");
            }
        }

        @Override
        public int width() {
            return bitWidth.bits();
        }
    }

    /** A logical bus assembled from one scalar net per bit, least-significant bit first. */
    record Bits(int[] netIds) implements CompiledInputBinding {
        public Bits {
            if (netIds == null || netIds.length == 0) {
                throw new IllegalArgumentException("A bit input binding needs at least one net");
            }
            netIds = netIds.clone();
            for (int netId : netIds) {
                if (netId < 0) {
                    throw new IllegalArgumentException("Input net ids must be non-negative");
                }
            }
        }

        @Override
        public int[] netIds() {
            return netIds.clone();
        }

        @Override
        public int width() {
            return netIds.length;
        }
    }
}
