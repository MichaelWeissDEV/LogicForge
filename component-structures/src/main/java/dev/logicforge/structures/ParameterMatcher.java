package dev.logicforge.structures;

import dev.logicforge.circuit.component.ParameterSpec;
import dev.logicforge.circuit.component.ParameterValues;
import java.util.Objects;

/** A constraint over the effective parameters of a concrete component instance. */
@FunctionalInterface
public interface ParameterMatcher {

    boolean matches(ParameterValues parameters);

    default ParameterMatcher and(ParameterMatcher other) {
        Objects.requireNonNull(other, "other");
        return parameters -> matches(parameters) && other.matches(parameters);
    }

    static ParameterMatcher any() {
        return parameters -> true;
    }

    /** Matches the coerced value, including a parameter spec's default when it is omitted. */
    static <T> ParameterMatcher equalTo(ParameterSpec<T> parameter, T expected) {
        Objects.requireNonNull(parameter, "parameter");
        return parameters -> Objects.equals(parameters.get(parameter), expected);
    }
}
