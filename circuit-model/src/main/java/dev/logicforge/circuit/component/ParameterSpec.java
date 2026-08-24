package dev.logicforge.circuit.component;

import java.util.List;

/**
 * Declaration of one configurable property of a component definition.
 *
 * <p>The inspector renders editors purely from these specs, so adding a component with new
 * properties needs no UI code. Values are stored as plain {@link Integer},
 * {@link Boolean} or {@link String} objects so they map straight onto JSON.
 */
public sealed interface ParameterSpec<T> {

    /** Stable key used in the project file. */
    String key();

    /** Human readable name shown in the inspector. */
    String displayName();

    T defaultValue();

    /** Converts a raw value (e.g. read from a project file) into a valid value. */
    T coerce(Object raw);

    record IntegerParameter(String key, String displayName, Integer defaultValue, int min, int max)
            implements ParameterSpec<Integer> {

        public IntegerParameter {
            if (min > max) {
                throw new IllegalArgumentException("min > max for parameter " + key);
            }
            if (defaultValue < min || defaultValue > max) {
                throw new IllegalArgumentException("Default out of range for parameter " + key);
            }
        }

        @Override
        public Integer coerce(Object raw) {
            int value = switch (raw) {
                case null -> defaultValue;
                case Number number -> number.intValue();
                case String text -> parse(text);
                default -> throw new IllegalArgumentException(
                        "Parameter " + key + " expects a number, got " + raw.getClass().getSimpleName());
            };
            return Math.clamp(value, min, max);
        }

        private int parse(String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException failure) {
                return defaultValue;
            }
        }
    }

    record BooleanParameter(String key, String displayName, Boolean defaultValue)
            implements ParameterSpec<Boolean> {

        @Override
        public Boolean coerce(Object raw) {
            return switch (raw) {
                case null -> defaultValue;
                case Boolean value -> value;
                case String text -> Boolean.parseBoolean(text.trim());
                case Number number -> number.intValue() != 0;
                default -> defaultValue;
            };
        }
    }

    record StringParameter(String key, String displayName, String defaultValue)
            implements ParameterSpec<String> {

        @Override
        public String coerce(Object raw) {
            return raw == null ? defaultValue : raw.toString();
        }
    }

    /** A choice between a fixed set of options, stored by its option key. */
    record EnumParameter(String key, String displayName, String defaultValue, List<String> options)
            implements ParameterSpec<String> {

        public EnumParameter {
            options = List.copyOf(options);
            if (!options.contains(defaultValue)) {
                throw new IllegalArgumentException("Default " + defaultValue + " is not an option of " + key);
            }
        }

        @Override
        public String coerce(Object raw) {
            if (raw == null) {
                return defaultValue;
            }
            String text = raw.toString();
            return options.contains(text) ? text : defaultValue;
        }
    }
}
