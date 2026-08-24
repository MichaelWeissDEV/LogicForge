package dev.logicforge.library;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.component.PortSpec;
import dev.logicforge.circuit.geometry.CircuitSize;
import java.util.List;

/**
 * Computes the ports and body size of a component from its parameters.
 *
 * <p>Ports carry their own geometry, so this is where a gate grows taller when the user
 * asks for more inputs. Everything is expressed in the unrotated component frame with the
 * origin at the body centre; {@code ComponentGeometry} applies the rotation.
 */
public interface PortLayout {

    /** Distance between two neighbouring ports. */
    double PORT_SPACING = 16;

    /** Length of the little stub between the body edge and the connection point. */
    double PORT_STUB = 8;

    List<PortSpec> ports(ParameterValues values);

    CircuitSize bodySize(ParameterValues values);
}
