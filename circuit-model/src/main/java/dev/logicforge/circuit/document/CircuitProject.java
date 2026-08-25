package dev.logicforge.circuit.document;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

/**
 * A project holds one or more circuits. Version 0.1 always contains exactly one circuit
 * named {@value #MAIN_CIRCUIT}; the container exists so that subcircuits can be added later
 * without changing the file format's shape.
 */
public final class CircuitProject {

    public static final String MAIN_CIRCUIT = "main";

    private final Map<String, CircuitDocument> circuits = new LinkedHashMap<>();
    private String name;

    public CircuitProject(String name) {
        this.name = name == null || name.isBlank() ? "untitled" : name;
    }

    /** A project containing a single, empty {@code main} circuit. */
    public static CircuitProject empty(String name) {
        CircuitProject project = new CircuitProject(name);
        project.putCircuit(new CircuitDocument(new CircuitMetadata(MAIN_CIRCUIT, "")));
        return project;
    }

    public static CircuitProject of(String name, CircuitDocument mainCircuit) {
        CircuitProject project = new CircuitProject(name);
        project.putCircuit(mainCircuit);
        return project;
    }

    public String name() {
        return name;
    }

    public void setName(String newName) {
        this.name = newName;
    }

    public void putCircuit(CircuitDocument circuit) {
        circuits.put(circuit.metadata().name(), circuit);
    }

    /** Adds a new, uniquely named circuit. */
    public CircuitDocument addCircuit(String circuitName) {
        String name = requireUsableName(circuitName);
        if (circuits.containsKey(name)) {
            throw new IllegalArgumentException("Circuit '" + name + "' already exists");
        }
        CircuitDocument circuit = new CircuitDocument(new CircuitMetadata(name, ""));
        circuits.put(name, circuit);
        return circuit;
    }

    /**
     * Atomically renames a child circuit and every instance definition that refers to it.
     */
    public void renameCircuit(String oldName, String newName) {
        if (MAIN_CIRCUIT.equals(oldName)) {
            throw new IllegalArgumentException("The main circuit cannot be renamed");
        }
        String target = requireUsableName(newName);
        CircuitDocument renamed = circuits.get(oldName);
        if (renamed == null) {
            throw new IllegalArgumentException("Unknown circuit '" + oldName + "'");
        }
        if (!oldName.equals(target) && circuits.containsKey(target)) {
            throw new IllegalArgumentException("Circuit '" + target + "' already exists");
        }
        if (oldName.equals(target)) {
            return;
        }

        String oldDefinition = SubcircuitSupport.definitionId(oldName);
        String newDefinition = SubcircuitSupport.definitionId(target);
        List<Map.Entry<CircuitDocument, ComponentInstance>> references = new ArrayList<>();
        for (CircuitDocument circuit : circuits.values()) {
            for (ComponentInstance component : circuit.components()) {
                if (component.definitionId().equals(oldDefinition)) {
                    references.add(Map.entry(circuit, component));
                }
            }
        }

        LinkedHashMap<String, CircuitDocument> reordered = new LinkedHashMap<>();
        for (Map.Entry<String, CircuitDocument> entry : circuits.entrySet()) {
            if (entry.getKey().equals(oldName)) {
                renamed.setMetadata(renamed.metadata().withName(target));
                reordered.put(target, renamed);
            } else {
                reordered.put(entry.getKey(), entry.getValue());
            }
        }
        circuits.clear();
        circuits.putAll(reordered);
        references.forEach(reference -> reference.getKey().replaceComponent(
                reference.getValue().withDefinitionId(newDefinition)));
    }

    /** Removes a child definition. Existing instances remain so validation can report them. */
    public CircuitDocument removeCircuit(String circuitName) {
        if (MAIN_CIRCUIT.equals(circuitName)) {
            throw new IllegalArgumentException("The main circuit cannot be deleted");
        }
        CircuitDocument removed = circuits.remove(circuitName);
        if (removed == null) {
            throw new IllegalArgumentException("Unknown circuit '" + circuitName + "'");
        }
        return removed;
    }

    public Optional<CircuitDocument> circuit(String circuitName) {
        return Optional.ofNullable(circuits.get(circuitName));
    }

    public Collection<CircuitDocument> circuits() {
        return Collections.unmodifiableCollection(circuits.values());
    }

    public List<String> circuitNames() {
        return List.copyOf(circuits.keySet());
    }

    /** The circuit the editor opens. */
    public CircuitDocument mainCircuit() {
        CircuitDocument main = circuits.get(MAIN_CIRCUIT);
        if (main == null) {
            throw new IllegalStateException("Project has no '" + MAIN_CIRCUIT + "' circuit");
        }
        return main;
    }

    private static String requireUsableName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("Circuit name cannot be blank");
        }
        if (name.contains("/") || name.contains(":")) {
            throw new IllegalArgumentException("Circuit name cannot contain '/' or ':'");
        }
        return name;
    }
}
