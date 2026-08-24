package dev.logicforge.simulation;

/**
 * The simulation behaviour of one kind of component — kept separate from its
 * {@code ComponentDefinition} (what it is) and its renderer (how it looks).
 *
 * <p>A behaviour is shared by all instances of a component, so it must not keep instance
 * data in fields; anything that must survive between evaluations belongs in the
 * {@link ComponentRuntimeState} it creates.
 */
public interface ComponentBehavior {

    /**
     * Recomputes the outputs. Called whenever an input net changed, when the component's
     * own state was changed from the outside, and once for every component on reset.
     */
    void evaluate(ComponentContext context);

    /** Creates the state object for one instance. Stateless components keep the default. */
    default ComponentRuntimeState createState() {
        return ComponentRuntimeState.STATELESS;
    }
}
