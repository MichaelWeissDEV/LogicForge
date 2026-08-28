package dev.logicforge.compiler;

import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import dev.logicforge.circuit.chip.ChipInstance;

/** Maps synthetic UUIDs of expanded logical units back to the physical ChipInstance. */
public record ChipSourceMap(Map<UUID, ChipInstance> syntheticToPhysical) {
    public ChipSourceMap {
        syntheticToPhysical = Map.copyOf(syntheticToPhysical);
    }

    public Optional<ChipInstance> physicalChipOf(UUID syntheticUnitId) {
        return Optional.ofNullable(syntheticToPhysical.get(syntheticUnitId));
    }
}
