package dev.logicforge.circuit.component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * An immutable set of parameter values for one component instance.
 *
 * <p>Values are kept in key order so that saving a project twice produces byte-identical
 * output.
 */
public final class ParameterValues {

    private static final ParameterValues EMPTY = new ParameterValues(Map.of());

    private final Map<String, Object> values;

    private ParameterValues(Map<String, Object> values) {
        Map<String, Object> normalized = new TreeMap<>();
        values.forEach((key, value) -> normalized.put(key, normalize(value)));
        this.values = Collections.unmodifiableMap(normalized);
    }

    /**
     * Stores numbers in one canonical shape. JSON has a single number type, so a value
     * written as {@code 4} comes back as a double; normalising here keeps a loaded project
     * equal to the one that was saved.
     */
    private static Object normalize(Object value) {
        if (value instanceof Number number) {
            double asDouble = number.doubleValue();
            if (asDouble == Math.rint(asDouble) && !Double.isInfinite(asDouble)) {
                return (int) asDouble;
            }
            return asDouble;
        }
        return value;
    }

    public static ParameterValues empty() {
        return EMPTY;
    }

    public static ParameterValues of(Map<String, Object> values) {
        return values.isEmpty() ? EMPTY : new ParameterValues(values);
    }

    /** The default values of all given specs. */
    public static ParameterValues defaultsOf(Collection<ParameterSpec<?>> specs) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        for (ParameterSpec<?> spec : specs) {
            defaults.put(spec.key(), spec.defaultValue());
        }
        return of(defaults);
    }

    /** This set of values with one entry replaced. */
    public ParameterValues with(String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(values);
        copy.put(key, value);
        return new ParameterValues(copy);
    }

    public <T> ParameterValues with(ParameterSpec<T> spec, T value) {
        return with(spec.key(), value);
    }

    /** The value of {@code spec}, coerced into range, falling back to its default. */
    public <T> T get(ParameterSpec<T> spec) {
        return spec.coerce(values.get(spec.key()));
    }

    public int getInt(ParameterSpec.IntegerParameter spec) {
        return get(spec);
    }

    public boolean getBoolean(ParameterSpec.BooleanParameter spec) {
        return get(spec);
    }

    /** Raw storage view, for the project format only. */
    public Map<String, Object> asMap() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ParameterValues parameters && values.equals(parameters.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
