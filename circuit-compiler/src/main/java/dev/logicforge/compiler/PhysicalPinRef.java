package dev.logicforge.compiler;

import java.util.Objects;
import java.util.UUID;

/** Stable source identity of one physical package pin in a concrete runtime instance. */
public record PhysicalPinRef(RuntimeInstancePath parentPath, UUID chipInstanceId,
                             int pinNumber) {
    public PhysicalPinRef {
        Objects.requireNonNull(parentPath, "parentPath");
        Objects.requireNonNull(chipInstanceId, "chipInstanceId");
        if (pinNumber < 1) {
            throw new IllegalArgumentException("Pin number must be positive");
        }
    }
}
