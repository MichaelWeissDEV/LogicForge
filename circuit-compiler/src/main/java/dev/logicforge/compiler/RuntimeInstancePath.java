package dev.logicforge.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Stable identity of one concrete circuit instance in a flattened runtime hierarchy. */
public record RuntimeInstancePath(String rootCircuitName, List<UUID> instanceIds) {

    public RuntimeInstancePath {
        if (rootCircuitName == null || rootCircuitName.isBlank()
                || rootCircuitName.contains("/")) {
            throw new IllegalArgumentException("A runtime path needs a simple root circuit name");
        }
        instanceIds = List.copyOf(Objects.requireNonNull(instanceIds, "instanceIds"));
    }

    public static RuntimeInstancePath root(String circuitName) {
        return new RuntimeInstancePath(circuitName, List.of());
    }

    public static RuntimeInstancePath parse(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Runtime instance path cannot be blank");
        }
        String[] segments = path.split("/", -1);
        List<UUID> ids = new ArrayList<>();
        for (int index = 1; index < segments.length; index++) {
            try {
                ids.add(UUID.fromString(segments[index]));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Invalid runtime instance path segment: "
                        + segments[index], invalid);
            }
        }
        return new RuntimeInstancePath(segments[0], ids);
    }

    public RuntimeInstancePath child(UUID instanceId) {
        List<UUID> ids = new ArrayList<>(instanceIds);
        ids.add(Objects.requireNonNull(instanceId, "instanceId"));
        return new RuntimeInstancePath(rootCircuitName, ids);
    }

    public Optional<RuntimeInstancePath> parent() {
        if (instanceIds.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RuntimeInstancePath(rootCircuitName,
                instanceIds.subList(0, instanceIds.size() - 1)));
    }

    public Optional<UUID> leafInstanceId() {
        return instanceIds.isEmpty() ? Optional.empty()
                : Optional.of(instanceIds.get(instanceIds.size() - 1));
    }

    @Override
    public String toString() {
        return instanceIds.stream().map(UUID::toString)
                .collect(java.util.stream.Collectors.joining("/", rootCircuitName + "/", ""))
                .replaceFirst("/$", "");
    }
}
