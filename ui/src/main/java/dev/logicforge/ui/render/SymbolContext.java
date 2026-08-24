package dev.logicforge.ui.render;

import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.geometry.CircuitSize;
import dev.logicforge.logic.LogicState;
import java.util.function.Function;
import javafx.scene.paint.Color;

/**
 * What a symbol painter needs: the component, its size, and a way to ask for the live
 * value on one of its ports.
 *
 * <p>The painter draws in component-local coordinates — origin at the centre of the body,
 * unrotated. The canvas has already applied position and rotation, using exactly the same
 * transform the model uses for port positions, so symbols and wires can never drift apart.
 */
public record SymbolContext(
        ComponentInstance instance,
        ComponentDefinition definition,
        CircuitSize body,
        boolean selected,
        boolean hovered,
        Function<String, LogicState> portValues) {

    public double halfWidth() {
        return body.halfWidth();
    }

    public double halfHeight() {
        return body.halfHeight();
    }

    /** The value on one of this component's ports, {@code Z} while nothing is compiled. */
    public LogicState value(String portName) {
        return portValues.apply(portName);
    }

    public Color stroke() {
        if (selected) {
            return Theme.SELECTION;
        }
        return hovered ? Theme.COMPONENT_STROKE_HOVER : Theme.COMPONENT_STROKE;
    }
}
