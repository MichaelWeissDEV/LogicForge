package dev.logicforge.circuit.document;

import java.util.Objects;
import java.util.UUID;

/** An electrical terminal that a wire can attach to. */
public sealed interface ElectricalEndpoint {

    record ComponentEndpoint(PortEndpoint port) implements ElectricalEndpoint {
        public ComponentEndpoint {
            Objects.requireNonNull(port, "port");
        }
    }

    record ChipPinEndpoint(UUID chipInstanceId, int physicalPinNumber) implements ElectricalEndpoint {
        public ChipPinEndpoint {
            Objects.requireNonNull(chipInstanceId, "chipInstanceId");
            if (physicalPinNumber < 1) {
                throw new IllegalArgumentException("Pin number must be positive");
            }
        }
    }
}
