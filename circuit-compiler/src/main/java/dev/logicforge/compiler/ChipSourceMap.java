package dev.logicforge.compiler;

import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.document.PortEndpoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Bidirectional source mapping between a visible physical package and its expanded runtime
 * logical units. Package identity is always UUID-based; labels are intentionally absent.
 */
public final class ChipSourceMap {

    private final Map<UUID, ChipInstance> physicalByLogicalUnit;
    private final Map<UUID, List<UUID>> logicalUnitsByPhysicalChip;
    private final Map<PhysicalPinRef, PortEndpoint> logicalEndpointByPhysicalPin;
    private final Map<PortEndpoint, PhysicalPinRef> physicalPinByLogicalEndpoint;
    private final Map<PhysicalPinRef, Integer> netByPhysicalPin;

    public ChipSourceMap(Map<UUID, ChipInstance> physicalByLogicalUnit) {
        this(physicalByLogicalUnit, Map.of(), Map.of());
    }

    public ChipSourceMap(Map<UUID, ChipInstance> physicalByLogicalUnit,
                         Map<PhysicalPinRef, PortEndpoint> logicalEndpointByPhysicalPin,
                         Map<PhysicalPinRef, Integer> netByPhysicalPin) {
        this.physicalByLogicalUnit = Map.copyOf(physicalByLogicalUnit);
        Map<UUID, List<UUID>> units = new LinkedHashMap<>();
        physicalByLogicalUnit.forEach((logicalUnit, physical) -> units
                .computeIfAbsent(physical.id(), ignored -> new ArrayList<>()).add(logicalUnit));
        units.replaceAll((ignored, logicalUnits) -> List.copyOf(logicalUnits));
        this.logicalUnitsByPhysicalChip = Map.copyOf(units);
        this.logicalEndpointByPhysicalPin = Map.copyOf(logicalEndpointByPhysicalPin);
        Map<PortEndpoint, PhysicalPinRef> pins = new LinkedHashMap<>();
        logicalEndpointByPhysicalPin.forEach((physicalPin, logicalEndpoint) -> {
            PhysicalPinRef previous = pins.put(logicalEndpoint, physicalPin);
            if (previous != null && !previous.equals(physicalPin)) {
                throw new IllegalArgumentException("Logical endpoint maps to multiple physical pins: "
                        + logicalEndpoint);
            }
        });
        this.physicalPinByLogicalEndpoint = Map.copyOf(pins);
        this.netByPhysicalPin = Map.copyOf(netByPhysicalPin);
    }

    public Optional<ChipInstance> physicalChipOf(UUID runtimeLogicalUnit) {
        return Optional.ofNullable(physicalByLogicalUnit.get(runtimeLogicalUnit));
    }

    public List<UUID> logicalUnitsOf(UUID physicalChip) {
        return logicalUnitsByPhysicalChip.getOrDefault(physicalChip, List.of());
    }

    public Optional<PortEndpoint> logicalEndpointForPin(UUID physicalChip, int physicalPin) {
        return matchingPin(physicalChip, physicalPin)
                .map(logicalEndpointByPhysicalPin::get);
    }

    /** Hierarchy-aware variant; prefer this where a chip can occur below the root circuit. */
    public Optional<PortEndpoint> logicalEndpointFor(PhysicalPinRef physicalPin) {
        return Optional.ofNullable(logicalEndpointByPhysicalPin.get(physicalPin));
    }

    public OptionalInt netForPin(UUID physicalChip, int physicalPin) {
        return matchingPin(physicalChip, physicalPin).map(netByPhysicalPin::get)
                .map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }

    /** Hierarchy-aware variant; prefer this where a chip can occur below the root circuit. */
    public OptionalInt netFor(PhysicalPinRef physicalPin) {
        Integer net = netByPhysicalPin.get(physicalPin);
        return net == null ? OptionalInt.empty() : OptionalInt.of(net);
    }

    public Optional<PhysicalPinRef> physicalPinFor(PortEndpoint logicalEndpoint) {
        return Optional.ofNullable(physicalPinByLogicalEndpoint.get(logicalEndpoint));
    }

    private Optional<PhysicalPinRef> matchingPin(UUID chipInstanceId, int pinNumber) {
        return logicalEndpointByPhysicalPin.keySet().stream()
                .filter(pin -> pin.chipInstanceId().equals(chipInstanceId)
                        && pin.pinNumber() == pinNumber)
                .findFirst();
    }
}
