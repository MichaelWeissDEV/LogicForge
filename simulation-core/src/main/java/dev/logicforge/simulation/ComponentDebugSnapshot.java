package dev.logicforge.simulation;

import dev.logicforge.logic.LogicVector;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Generic, read-only component introspection consumed by inspectors and debuggers. */
public record ComponentDebugSnapshot(
        Map<String, LogicVector> namedValues,
        List<LogicVector> registers,
        MemorySnapshot memory,
        Map<String, Long> counters) {

    public static final ComponentDebugSnapshot EMPTY =
            new ComponentDebugSnapshot(Map.of(), List.of(), null, Map.of());

    public ComponentDebugSnapshot {
        namedValues = namedValues == null ? Map.of() : Map.copyOf(namedValues);
        registers = registers == null ? List.of() : List.copyOf(registers);
        counters = counters == null ? Map.of() : Map.copyOf(counters);
    }

    public Optional<MemorySnapshot> memoryOptional() {
        return Optional.ofNullable(memory);
    }

    public boolean isEmpty() {
        return namedValues.isEmpty() && registers.isEmpty() && memory == null && counters.isEmpty();
    }
}
