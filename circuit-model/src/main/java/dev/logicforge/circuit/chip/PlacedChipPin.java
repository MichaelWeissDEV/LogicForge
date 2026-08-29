package dev.logicforge.circuit.chip;

import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.PortSide;

/**
 * A physical package pin resolved into world coordinates for one placed {@link ChipInstance}.
 *
 * <p>{@code position} is the connectable tip of the lead — the point wires attach to and
 * hit testing targets. {@code bodyAnchor} is where the lead meets the package body, used by
 * the renderer to draw the stub. Both already include the chip's position and rotation.
 */
public record PlacedChipPin(PackagePin pin, CircuitPoint position, CircuitPoint bodyAnchor, PortSide side) {

    public int number() {
        return pin.number();
    }

    public String name() {
        return pin.name();
    }

    public ElectricalPinType electricalType() {
        return pin.electricalType();
    }
}
