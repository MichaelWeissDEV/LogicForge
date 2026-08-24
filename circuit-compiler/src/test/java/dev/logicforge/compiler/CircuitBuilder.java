package dev.logicforge.compiler;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.library.ComponentRegistry;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds small circuits by hand, so tests read like the circuits they describe. */
final class CircuitBuilder {

    private final ComponentRegistry registry = ComponentRegistry.standard();
    private final CircuitDocument document = new CircuitDocument();
    private final Map<String, ComponentInstance> byLabel = new LinkedHashMap<>();
    private double nextX = 0;

    ComponentInstance add(String definitionId, String label) {
        return add(definitionId, label, registry.require(definitionId).definition().defaultParameters());
    }

    ComponentInstance add(String definitionId, String label, ParameterValues parameters) {
        nextX += 100;
        ComponentInstance instance = ComponentInstance
                .create(definitionId, new CircuitPoint(nextX, 100), parameters)
                .withLabel(label);
        document.addComponent(instance);
        byLabel.put(label, instance);
        return instance;
    }

    /** Wires {@code fromLabel.fromPort} to {@code toLabel.toPort}. */
    CircuitBuilder wire(String fromLabel, String fromPort, String toLabel, String toPort) {
        document.addConnection(Connection.create(port(fromLabel, fromPort), port(toLabel, toPort)));
        return this;
    }

    PortReference port(String label, String portName) {
        return new PortReference(byLabel.get(label).id(), portName);
    }

    ComponentInstance component(String label) {
        return byLabel.get(label);
    }

    void replace(ComponentInstance instance) {
        document.replaceComponent(instance);
        byLabel.entrySet().stream()
                .filter(entry -> entry.getValue().id().equals(instance.id()))
                .findFirst()
                .ifPresent(entry -> entry.setValue(instance));
    }

    CircuitDocument document() {
        return document;
    }

    ComponentRegistry registry() {
        return registry;
    }

    CompilationResult compile() {
        return new CircuitCompiler(registry).compile(document);
    }
}
