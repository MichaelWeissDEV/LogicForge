package dev.logicforge.circuit.component;

/**
 * Palette category of a component definition.
 *
 * <p>Categories that no component uses yet are still listed here so the model does not
 * have to change when those components arrive — but the palette only shows categories
 * that actually contain something.
 */
public enum ComponentCategory {

    SOURCES("Sources"),
    LOGIC("Logic"),
    OUTPUTS("Outputs"),
    ROUTING("Routing"),
    ARITHMETIC("Arithmetic"),
    SEQUENTIAL("Sequential"),
    MEMORY("Memory"),
    SYSTEM("System"),
    HIERARCHY("Hierarchy");

    private final String displayName;

    ComponentCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
