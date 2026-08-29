package dev.logicforge.circuit.document;

import dev.logicforge.circuit.chip.ChipDefinition;
import dev.logicforge.circuit.chip.ChipGeometry;
import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.chip.ElectricalPinType;
import dev.logicforge.circuit.chip.PlacedChipPin;
import dev.logicforge.circuit.component.ComponentDefinition;
import java.util.Optional;
import java.util.function.Function;

/**
 * Resolves any {@link ElectricalEndpoint} — a component port or a physical chip pin — into
 * world coordinates.
 *
 * <p>This is the single place that knows how to turn either variant of the sealed
 * {@code ElectricalEndpoint} into a placed position. The wire router, hit testing and the
 * renderer all call it, so a wire touching a chip pin is drawn, hit-tested and routed
 * exactly like one touching a component port — never through a second, parallel resolution
 * path.
 */
public final class ElectricalEndpointGeometry {

    private ElectricalEndpointGeometry() {
    }

    public static Optional<PlacedElectricalEndpoint> resolve(CircuitDocument document,
            ElectricalEndpoint endpoint, Function<String, Optional<ComponentDefinition>> componentDefinitions,
            Function<String, Optional<ChipDefinition>> chipDefinitions) {
        if (endpoint instanceof ElectricalEndpoint.ComponentEndpoint component) {
            return resolveComponentPort(document, component.port(), componentDefinitions)
                    .map(placed -> new PlacedElectricalEndpoint(endpoint, placed.position(), placed.side(),
                            placed.connectable(), placed.displayName()));
        }
        if (endpoint instanceof ElectricalEndpoint.ChipPinEndpoint chipPin) {
            return resolveChipPin(document, chipPin, chipDefinitions);
        }
        return Optional.empty();
    }

    private static Optional<PlacedPort> resolveComponentPort(CircuitDocument document, PortEndpoint port,
            Function<String, Optional<ComponentDefinition>> componentDefinitions) {
        return document.component(port.componentId()).flatMap(instance ->
                componentDefinitions.apply(instance.definitionId()).flatMap(definition ->
                        ComponentGeometry.endpoint(instance, definition, port, document)));
    }

    private static Optional<PlacedElectricalEndpoint> resolveChipPin(CircuitDocument document,
            ElectricalEndpoint.ChipPinEndpoint chipPin,
            Function<String, Optional<ChipDefinition>> chipDefinitions) {
        Optional<ChipInstance> chip = document.chip(chipPin.chipInstanceId());
        if (chip.isEmpty()) {
            return Optional.empty();
        }
        Optional<ChipDefinition> definition = chipDefinitions.apply(chip.get().chipDefinitionId());
        if (definition.isEmpty()) {
            return Optional.empty();
        }
        Optional<PlacedChipPin> pin = ChipGeometry.pin(chip.get(), definition.get().packageDefinition(),
                chipPin.physicalPinNumber());
        return pin.map(placed -> new PlacedElectricalEndpoint(chipPin, placed.position(), placed.side(),
                placed.electricalType() == ElectricalPinType.SIGNAL,
                chip.get().referenceDesignator() + "." + placed.name()));
    }
}
