package dev.logicforge.circuit.document;

import java.util.UUID;

/** What just changed in a {@link CircuitDocument}. */
public record CircuitChange(Kind kind, UUID elementId) {

    public enum Kind {

        COMPONENT_ADDED(true),
        COMPONENT_REMOVED(true),
        /** Parameters changed, so ports may have appeared or disappeared. */
        COMPONENT_RECONFIGURED(true),
        /** Component's position changed — the netlist is unaffected. */
        COMPONENT_MOVED(false),
        /** Component's rotation changed — the netlist is unaffected. */
        COMPONENT_ROTATED(false),
        /** Component's label changed — the netlist is unaffected. */
        COMPONENT_RENAMED(false),
        /** Compact/expanded pin presentation changed; electrical topology is untouched. */
        COMPONENT_PRESENTATION(false),
        CONNECTION_ADDED(true),
        CONNECTION_REMOVED(true),
        /** Only the wire's waypoints changed. */
        CONNECTION_ROUTED(false),
        METADATA(false);

        private final boolean affectsTopology;

        Kind(boolean affectsTopology) {
            this.affectsTopology = affectsTopology;
        }

        /**
         * {@code true} if the electrical structure changed and the circuit therefore has
         * to be recompiled. Moving or rotating a component does not.
         */
        public boolean affectsTopology() {
            return affectsTopology;
        }
    }

    public boolean affectsTopology() {
        return kind.affectsTopology();
    }
}
