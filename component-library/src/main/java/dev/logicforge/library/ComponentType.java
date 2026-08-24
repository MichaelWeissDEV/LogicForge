package dev.logicforge.library;

import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.simulation.ComponentBehavior;
import java.util.function.Function;

/**
 * A registry entry: what a component is ({@link ComponentDefinition}) plus how it behaves
 * during simulation. How it is drawn is registered separately in the UI module, so no
 * single class has to know all three.
 */
public record ComponentType(
        ComponentDefinition definition,
        Function<ParameterValues, ComponentBehavior> behaviorFactory) {

    public String id() {
        return definition.id();
    }

    public String displayName() {
        return definition.displayName();
    }

    /** The behaviour for an instance configured with {@code values}. */
    public ComponentBehavior behaviorFor(ParameterValues values) {
        return behaviorFactory.apply(values);
    }

    /** Convenience for components whose behaviour does not depend on parameters. */
    public static ComponentType of(ComponentDefinition definition, ComponentBehavior behavior) {
        return new ComponentType(definition, values -> behavior);
    }
}
