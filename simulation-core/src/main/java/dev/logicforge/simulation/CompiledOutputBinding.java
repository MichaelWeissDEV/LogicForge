package dev.logicforge.simulation;

/** Maps one logical behavior output port to its compiled nets and owned drivers. */
public sealed interface CompiledOutputBinding
        permits CompiledOutputBinding.Whole, CompiledOutputBinding.Bits {

    int width();

    /** Fast path for a logical port driving one vector net. */
    record Whole(int netId, int driverId, int width) implements CompiledOutputBinding {
        public Whole {
            if (netId < 0 || driverId < -1 || width < 1) {
                throw new IllegalArgumentException("Invalid whole output binding");
            }
        }
    }

    /** A logical bus split across scalar nets and drivers, least-significant bit first. */
    record Bits(int[] netIds, int[] driverIds) implements CompiledOutputBinding {
        public Bits {
            if (netIds == null || driverIds == null || netIds.length == 0
                    || netIds.length != driverIds.length) {
                throw new IllegalArgumentException("Bit output nets and drivers must have equal non-zero length");
            }
            netIds = netIds.clone();
            driverIds = driverIds.clone();
            for (int i = 0; i < netIds.length; i++) {
                if (netIds[i] < 0 || driverIds[i] < -1) {
                    throw new IllegalArgumentException("Invalid bit output binding");
                }
            }
        }

        @Override
        public int[] netIds() {
            return netIds.clone();
        }

        @Override
        public int[] driverIds() {
            return driverIds.clone();
        }

        @Override
        public int width() {
            return netIds.length;
        }
    }
}
