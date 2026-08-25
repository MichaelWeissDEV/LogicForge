package dev.logicforge.circuit.geometry;

/**
 * Shared schematic grid dimensions used by model geometry and the UI viewport.
 *
 * <p>Keeping the base spacing below the UI layer lets presentation-aware component
 * geometry remain the single source of truth for rendering, hit testing and routing.
 */
public final class CircuitGrid {

    public static final double SPACING = 8;

    private CircuitGrid() {
    }
}
