package dev.logicforge.library.behavior;

import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;

/**
 * A component that only displays what it reads — an LED, a probe, an output pin. It has no
 * outputs, so evaluating it does nothing; the editor reads the value straight off the net
 * the component is attached to.
 */
public final class SinkBehavior implements ComponentBehavior {

    public static final SinkBehavior INSTANCE = new SinkBehavior();

    private SinkBehavior() {
    }

    @Override
    public void evaluate(ComponentContext context) {
        // Nothing to drive: the value is observed, not produced.
    }
}
