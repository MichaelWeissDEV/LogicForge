package dev.logicforge.simulation;

import dev.logicforge.logic.LogicVector;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Generic, read-only component introspection consumed by inspectors and debuggers.
 *
 * <p>{@code memory} is a cheap {@link MemoryInfo} rather than a full {@link MemorySnapshot}
 * — this snapshot is built on every request (e.g. an Inspector refresh, which fires on
 * every editor change), so it must not clone a memory's entire contents array just to
 * report its size and last access.
 */
public record ComponentDebugSnapshot(
        Map<String, LogicVector> namedValues,
        List<LogicVector> registers,
        MemoryInfo memory,
        Map<String, Long> counters) {

    public static final ComponentDebugSnapshot EMPTY =
            new ComponentDebugSnapshot(Map.of(), List.of(), null, Map.of());

    public ComponentDebugSnapshot {
        namedValues = namedValues == null ? Map.of() : Map.copyOf(namedValues);
        registers = registers == null ? List.of() : List.copyOf(registers);
        counters = counters == null ? Map.of() : Map.copyOf(counters);
    }

    public Optional<MemoryInfo> memoryOptional() {
        return Optional.ofNullable(memory);
    }

    public boolean isEmpty() {
        return namedValues.isEmpty() && registers.isEmpty() && memory == null && counters.isEmpty();
    }
}
