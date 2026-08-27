package dev.logicforge.compiler;

import dev.logicforge.circuit.chip.ChipDefinition;
import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.geometry.CircuitPoint;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Deterministically expands one physical package into ordinary logical runtime units. */
public final class ChipInstanceExpander {

    public ExpandedChip expand(ChipInstance instance, ChipDefinition definition) {
        if (!instance.chipDefinitionId().equals(definition.metadata().partNumber())) {
            throw new IllegalArgumentException("Chip instance and definition ids differ");
        }
        Map<String, ComponentInstance> units = new LinkedHashMap<>();
        int index = 0;
        for (var unit : definition.logicalUnits()) {
            UUID id = UUID.nameUUIDFromBytes((instance.id() + "/" + unit.name())
                    .getBytes(StandardCharsets.UTF_8));
            ComponentInstance component = ComponentInstance.create(unit.componentDefinitionId(),
                            new CircuitPoint(instance.position().x() + index * 40,
                                    instance.position().y()), unit.parameters())
                    .withId(id)
                    .withLabel(instance.referenceDesignator() + unit.name())
                    .withSemanticRole("chip." + instance.id() + ".unit." + unit.name());
            units.put(unit.name(), component);
            index++;
        }
        Map<Integer, PortEndpoint> pins = new LinkedHashMap<>();
        for (var mapping : definition.logicalPinMappings()) {
            ComponentInstance unit = units.get(mapping.unitName());
            PortReference port = new PortReference(unit.id(), mapping.portName());
            PortEndpoint endpoint = mapping.bitIndex() < 0
                    ? PortEndpoint.whole(port)
                    : PortEndpoint.bit(port, mapping.bitIndex());
            pins.put(mapping.physicalPinNumber(), endpoint);
        }
        return new ExpandedChip(instance.id(), List.copyOf(units.values()), Map.copyOf(pins));
    }

    public record ExpandedChip(UUID packageInstanceId, List<ComponentInstance> logicalUnits,
                               Map<Integer, PortEndpoint> signalPins) {
        public ExpandedChip {
            logicalUnits = List.copyOf(new ArrayList<>(logicalUnits));
            signalPins = Map.copyOf(signalPins);
        }
    }
}
