package dev.logicforge.ui.render;

import javafx.scene.canvas.GraphicsContext;

/**
 * Draws the body of one kind of component.
 *
 * <p>Renderers are registered per component id and are separate from both the definition
 * and the simulation behaviour, so a new component brings its own drawing code without
 * anything else changing. A renderer never invents geometry: the body size and the port
 * positions come from the definition.
 */
@FunctionalInterface
public interface ComponentRenderer {

    void drawSymbol(GraphicsContext graphics, SymbolContext context);
}
