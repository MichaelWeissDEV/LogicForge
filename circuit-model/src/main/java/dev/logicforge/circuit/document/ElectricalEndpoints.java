package dev.logicforge.circuit.document;

import java.util.Optional;

/** Common, type-safe queries for electrical endpoints. */
public final class ElectricalEndpoints {
    private ElectricalEndpoints() {
    }

    public static Optional<PortEndpoint> componentPort(ElectricalEndpoint endpoint) {
        return endpoint instanceof ElectricalEndpoint.ComponentEndpoint component
                ? Optional.of(component.port()) : Optional.empty();
    }

    public static Optional<ElectricalEndpoint.ChipPinEndpoint> chipPin(ElectricalEndpoint endpoint) {
        return endpoint instanceof ElectricalEndpoint.ChipPinEndpoint chip ? Optional.of(chip) : Optional.empty();
    }

    public static boolean isComponentEndpoint(ElectricalEndpoint endpoint) {
        return endpoint instanceof ElectricalEndpoint.ComponentEndpoint;
    }

    public static boolean isChipEndpoint(ElectricalEndpoint endpoint) {
        return endpoint instanceof ElectricalEndpoint.ChipPinEndpoint;
    }
}
