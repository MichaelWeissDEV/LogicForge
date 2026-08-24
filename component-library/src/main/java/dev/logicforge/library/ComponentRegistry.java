package dev.logicforge.library;

import dev.logicforge.circuit.component.ComponentCategory;
import dev.logicforge.circuit.component.ComponentDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The one place that knows which components exist.
 *
 * <p>The palette, the inspector, the compiler and the project loader all ask the registry;
 * none of them keeps a list of its own. Adding a component means registering it here.
 */
public final class ComponentRegistry {

    private final Map<String, ComponentType> types = new LinkedHashMap<>();

    /** The registry holding every built-in component. */
    public static ComponentRegistry standard() {
        return StandardLibrary.createRegistry();
    }

    public void register(ComponentType type) {
        ComponentType previous = types.putIfAbsent(type.id(), type);
        if (previous != null) {
            throw new IllegalStateException("Component id " + type.id() + " is already registered");
        }
    }

    public Optional<ComponentType> find(String id) {
        return Optional.ofNullable(types.get(id));
    }

    public ComponentType require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown component: " + id));
    }

    public Optional<ComponentDefinition> definition(String id) {
        return find(id).map(ComponentType::definition);
    }

    public boolean contains(String id) {
        return types.containsKey(id);
    }

    public int size() {
        return types.size();
    }

    /** All components, in registration order. */
    public List<ComponentType> all() {
        return List.copyOf(types.values());
    }

    public List<ComponentType> byCategory(ComponentCategory category) {
        return types.values().stream()
                .filter(type -> type.definition().category() == category)
                .toList();
    }

    /** Only the categories that actually contain something, in declaration order. */
    public List<ComponentCategory> populatedCategories() {
        List<ComponentCategory> categories = new ArrayList<>();
        for (ComponentCategory category : ComponentCategory.values()) {
            if (!byCategory(category).isEmpty()) {
                categories.add(category);
            }
        }
        return categories;
    }

    /**
     * Palette search over display names, ids and keywords. Names that start with the query
     * come first; everything else keeps registration order.
     */
    public List<ComponentType> search(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return all();
        }
        List<ComponentType> leading = new ArrayList<>();
        List<ComponentType> matching = new ArrayList<>();
        for (ComponentType type : types.values()) {
            ComponentDefinition definition = type.definition();
            String name = definition.displayName().toLowerCase(Locale.ROOT);
            if (name.startsWith(needle)) {
                leading.add(type);
            } else if (name.contains(needle)
                    || definition.id().toLowerCase(Locale.ROOT).contains(needle)
                    || definition.searchKeywords().stream()
                            .anyMatch(keyword -> keyword.toLowerCase(Locale.ROOT).contains(needle))) {
                matching.add(type);
            }
        }
        leading.addAll(matching);
        return leading;
    }
}
