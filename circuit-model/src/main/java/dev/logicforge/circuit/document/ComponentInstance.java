package dev.logicforge.circuit.document;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.Rotation;
import java.util.UUID;

/**
 * One component placed on a circuit: a reference to its definition plus everything the
 * user configured about this particular copy.
 *
 * <p>Instances are immutable; editing produces a new instance with the same {@link #id()},
 * which keeps undo/redo and change notification simple. The identity is a UUID and stays
 * stable across saving and loading — compact integer ids exist only inside the compiled
 * circuit.
 */
public record ComponentInstance(
        UUID id,
        String definitionId,
        CircuitPoint position,
        Rotation rotation,
        ParameterValues parameters,
        String label) {

    public ComponentInstance {
        if (id == null || definitionId == null || position == null || rotation == null) {
            throw new IllegalArgumentException("Incomplete component instance");
        }
        parameters = parameters == null ? ParameterValues.empty() : parameters;
        label = label == null ? "" : label;
    }

    /** A new instance with a fresh id. {@code position} is the centre of the body. */
    public static ComponentInstance create(String definitionId, CircuitPoint position,
                                           ParameterValues parameters) {
        return new ComponentInstance(UUID.randomUUID(), definitionId, position, Rotation.DEG_0,
                parameters, "");
    }

    public ComponentInstance withPosition(CircuitPoint newPosition) {
        return new ComponentInstance(id, definitionId, newPosition, rotation, parameters, label);
    }

    public ComponentInstance movedBy(double dx, double dy) {
        return withPosition(position.plus(dx, dy));
    }

    public ComponentInstance withRotation(Rotation newRotation) {
        return new ComponentInstance(id, definitionId, position, newRotation, parameters, label);
    }

    public ComponentInstance withParameters(ParameterValues newParameters) {
        return new ComponentInstance(id, definitionId, position, rotation, newParameters, label);
    }

    public ComponentInstance withLabel(String newLabel) {
        return new ComponentInstance(id, definitionId, position, rotation, parameters, newLabel);
    }

    public ComponentInstance withId(UUID newId) {
        return new ComponentInstance(newId, definitionId, position, rotation, parameters, label);
    }
}
