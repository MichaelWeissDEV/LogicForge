package dev.logicforge.compiler;

import java.util.Map;
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
}
