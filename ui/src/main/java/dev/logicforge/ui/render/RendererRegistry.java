package dev.logicforge.ui.render;

import dev.logicforge.logic.LogicState;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps component ids to the painter that draws them.
 *
 * <p>Kept in the UI module on purpose: the circuit model and the simulator have no opinion
 * about how a component looks. Anything not registered falls back to a plain labelled box,
 * so an unknown component is still visible on the canvas instead of disappearing.
 */
public final class RendererRegistry {

    private final Map<String, ComponentRenderer> renderers = new HashMap<>();
    private final ComponentRenderer fallback = new FallbackRenderer();

    public static RendererRegistry standard() {
        RendererRegistry registry = new RendererRegistry();

        registry.register("source.zero", new SourceRenderer(SourceRenderer.Kind.CONSTANT, LogicState.ZERO));
        registry.register("source.one", new SourceRenderer(SourceRenderer.Kind.CONSTANT, LogicState.ONE));
        registry.register("source.unknown",
                new SourceRenderer(SourceRenderer.Kind.CONSTANT, LogicState.UNKNOWN));
        registry.register("source.highz",
                new SourceRenderer(SourceRenderer.Kind.CONSTANT, LogicState.HIGH_IMPEDANCE));
        registry.register("source.toggle", new SourceRenderer(SourceRenderer.Kind.TOGGLE, null));
        registry.register("source.button", new SourceRenderer(SourceRenderer.Kind.BUTTON, null));
        registry.register("source.clock", new ClockRenderer());

        registry.register("logic.buffer", new DriverRenderer(false, false));
        registry.register("logic.not", new DriverRenderer(true, false));
        registry.register("logic.tristate", new DriverRenderer(false, true));
        registry.register("logic.tristate.inverting", new DriverRenderer(true, true));

        registry.register("logic.and", new GateRenderer(GateRenderer.Outline.AND, false));
        registry.register("logic.nand", new GateRenderer(GateRenderer.Outline.AND, true));
        registry.register("logic.or", new GateRenderer(GateRenderer.Outline.OR, false));
        registry.register("logic.nor", new GateRenderer(GateRenderer.Outline.OR, true));
        registry.register("logic.xor", new GateRenderer(GateRenderer.Outline.XOR, false));
        registry.register("logic.xnor", new GateRenderer(GateRenderer.Outline.XOR, true));

        registry.register("output.led", new OutputRenderer(OutputRenderer.Kind.LED));
        registry.register("output.probe", new OutputRenderer(OutputRenderer.Kind.PROBE));
        registry.register("output.pin", new OutputRenderer(OutputRenderer.Kind.PIN));

        return registry;
    }

    public void register(String definitionId, ComponentRenderer renderer) {
        renderers.put(definitionId, renderer);
    }

    public ComponentRenderer rendererFor(String definitionId) {
        return renderers.getOrDefault(definitionId, fallback);
    }

    /** A plain box, so a component without a symbol is still visible and selectable. */
    private static final class FallbackRenderer implements ComponentRenderer {

        @Override
        public void drawSymbol(javafx.scene.canvas.GraphicsContext graphics, SymbolContext context) {
            double halfWidth = context.halfWidth();
            double halfHeight = context.halfHeight();
            graphics.setFill(Theme.COMPONENT_FILL);
            graphics.setStroke(context.stroke());
            graphics.setLineWidth(Theme.BODY_STROKE);
            graphics.fillRect(-halfWidth, -halfHeight, halfWidth * 2, halfHeight * 2);
            graphics.strokeRect(-halfWidth, -halfHeight, halfWidth * 2, halfHeight * 2);
        }
    }
}
