package dev.logicforge.compiler;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** Runtime ids keyed by stable slash-separated hierarchical instance/endpoint paths. */
public record HierarchySourceMap(
        Map<String, Integer> componentIdByPath,
        Map<String, Integer> netIdByEndpointPath) {

    public static final HierarchySourceMap EMPTY = new HierarchySourceMap(Map.of(), Map.of());

    public HierarchySourceMap {
        componentIdByPath = Map.copyOf(componentIdByPath);
        netIdByEndpointPath = Map.copyOf(netIdByEndpointPath);
    }

    public OptionalInt componentId(String path) {
        Integer id = componentIdByPath.get(path);
        return id == null ? OptionalInt.empty() : OptionalInt.of(id);
    }

    public OptionalInt netId(String endpointPath) {
        Integer id = netIdByEndpointPath.get(endpointPath);
        return id == null ? OptionalInt.empty() : OptionalInt.of(id);
    }

    /** Canonical or friendly path for a runtime component id, if one is known. */
    public Optional<String> componentPath(int runtimeId) {
        return componentIdByPath.entrySet().stream()
                .filter(entry -> entry.getValue() == runtimeId)
                .map(Map.Entry::getKey).sorted().findFirst();
    }

    /** Every hierarchical endpoint path resolving to a runtime net. */
    public List<String> endpointPaths(int runtimeNetId) {
        return netIdByEndpointPath.entrySet().stream()
                .filter(entry -> entry.getValue() == runtimeNetId)
                .map(Map.Entry::getKey).sorted().toList();
    }

    /** Finds paths ending in a component/port suffix, useful for direct definition views. */
    public List<String> endpointPathsEndingWith(String suffix) {
        return netIdByEndpointPath.keySet().stream().filter(path -> path.endsWith(suffix))
                .sorted().toList();
    }
}
