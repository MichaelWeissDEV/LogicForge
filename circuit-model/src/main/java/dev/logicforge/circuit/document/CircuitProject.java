package dev.logicforge.circuit.document;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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

    public Optional<CircuitDocument> circuit(String circuitName) {
        return Optional.ofNullable(circuits.get(circuitName));
    }

    public Collection<CircuitDocument> circuits() {
        return Collections.unmodifiableCollection(circuits.values());
    }

    /** The circuit the editor opens. */
    public CircuitDocument mainCircuit() {
        CircuitDocument main = circuits.get(MAIN_CIRCUIT);
        if (main == null) {
            throw new IllegalStateException("Project has no '" + MAIN_CIRCUIT + "' circuit");
        }
        return main;
    }
}
