package dev.logicforge.circuit.chip;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Ordered catalog of physical chip definitions keyed by part number. */
public final class ChipRegistry {
    private final Map<String, ChipDefinition> definitions = new LinkedHashMap<>();

    public void register(ChipDefinition definition) {
        String id = definition.metadata().partNumber();
        if (definitions.putIfAbsent(id, definition) != null) {
            throw new IllegalArgumentException("Duplicate chip definition " + id);
        }
    }

    public Optional<ChipDefinition> find(String partNumber) {
        return Optional.ofNullable(definitions.get(partNumber));
    }

    public ChipDefinition require(String partNumber) {
        return find(partNumber).orElseThrow(() -> new IllegalArgumentException(
                "Unknown chip definition " + partNumber));
    }

    public Collection<ChipDefinition> definitions() {
        return java.util.List.copyOf(definitions.values());
    }
}
