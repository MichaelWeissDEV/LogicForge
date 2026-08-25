package dev.logicforge.ui.render;

import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.component.PortDirection;
import dev.logicforge.circuit.component.PortSpec;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * A small schematic of one component's pins — the body as a labelled box, inputs on the
 * left, outputs (and INOUT ports) on the right — built directly from its
 * {@link ComponentDefinition#ports}, never from a separate hardcoded pin list. Used by the
 * inspector; has no dependency on the circuit editor or the simulation.
 */
public final class PinLayoutView extends Canvas {

    private static final double ROW_HEIGHT = 20;
    private static final double MARGIN_TOP = 12;
    private static final double PIN_STUB = 14;
    private static final double LABEL_MARGIN = 4;

    public PinLayoutView(ComponentDefinition definition, ParameterValues parameters, double width) {
        super(width, 10);
        List<PortSpec> ports = definition.ports(parameters);
        List<PortSpec> inputs = new ArrayList<>();
        List<PortSpec> outputs = new ArrayList<>();
        for (PortSpec port : ports) {
            if (port.direction() == PortDirection.INPUT) {
                inputs.add(port);
            } else {
                // OUTPUT and INOUT both get a connection point on the right for now.
                outputs.add(port);
            }
        }
        int rows = Math.max(1, Math.max(inputs.size(), outputs.size()));
        double height = MARGIN_TOP * 2 + rows * ROW_HEIGHT;
        setHeight(height);
        paint(width, height, inputs, outputs, definition.displayName());
    }

    private void paint(double width, double height, List<PortSpec> inputs, List<PortSpec> outputs, String name) {
        GraphicsContext g = getGraphicsContext2D();
        g.clearRect(0, 0, width, height);

        double boxLeft = width * 0.28;
        double boxRight = width * 0.72;
        double boxTop = MARGIN_TOP;
        double boxBottom = height - MARGIN_TOP;

        g.setFill(Theme.COMPONENT_FILL);
        g.setStroke(Theme.COMPONENT_STROKE);
        g.setLineWidth(1.4);
        g.fillRect(boxLeft, boxTop, boxRight - boxLeft, boxBottom - boxTop);
        g.strokeRect(boxLeft, boxTop, boxRight - boxLeft, boxBottom - boxTop);

        g.setFont(Font.font(9));
        g.setFill(Theme.TEXT_SECONDARY);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(name, (boxLeft + boxRight) / 2, (boxTop + boxBottom) / 2, boxRight - boxLeft - 6);

        drawPins(g, inputs, boxLeft, boxTop, boxBottom, true);
        drawPins(g, outputs, boxRight, boxTop, boxBottom, false);
    }

    private void drawPins(GraphicsContext g, List<PortSpec> ports, double boxEdgeX, double boxTop, double boxBottom,
                          boolean leftSide) {
        if (ports.isEmpty()) {
            return;
        }
        double rowHeight = (boxBottom - boxTop) / ports.size();
        g.setFont(Font.font(9));
        g.setTextBaseline(VPos.CENTER);
        g.setLineWidth(1.2);
        g.setStroke(Theme.PORT);

        for (int i = 0; i < ports.size(); i++) {
            PortSpec port = ports.get(i);
            double y = boxTop + rowHeight * (i + 0.5);
            double stubEnd = leftSide ? boxEdgeX - PIN_STUB : boxEdgeX + PIN_STUB;
            g.strokeLine(boxEdgeX, y, stubEnd, y);
            g.setFill(Theme.PORT);
            g.fillOval(stubEnd - (leftSide ? 2 : -2) - 2, y - 2, 4, 4);

            String label = port.width().isSingleBit() ? port.name() : port.name() + "[" + (port.width().bits() - 1) + ":0]";
            g.setFill(Theme.TEXT_PRIMARY);
            g.setTextAlign(leftSide ? TextAlignment.RIGHT : TextAlignment.LEFT);
            double textX = leftSide ? stubEnd - LABEL_MARGIN : stubEnd + LABEL_MARGIN;
            g.fillText(label, textX, y);
        }
    }
}
