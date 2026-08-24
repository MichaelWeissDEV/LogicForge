package dev.logicforge.app;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitMetadata;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.format.ProjectFormat;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.LibraryParameters;
import java.nio.file.Path;

/**
 * Writes the example circuits that ship with LogicForge.
 *
 * <p>They are produced by the application's own save path rather than written by hand, so
 * they can never drift out of step with the project format. Run with
 * {@code ./gradlew :app:examples}.
 */
public final class ExampleGenerator {

    private static final ComponentRegistry REGISTRY = ComponentRegistry.standard();

    private ExampleGenerator() {
    }

    public static void main(String[] args) {
        Path directory = Path.of(args.length > 0 ? args[0] : "examples");
        write(directory, "NOT", inverter());
        write(directory, "AND", twoInputGate("logic.and", "AND gate driven by two switches"));
        write(directory, "XOR", twoInputGate("logic.xor", "XOR gate driven by two switches"));
        write(directory, "Half-Adder", halfAdder());
        write(directory, "Tri-State-Bus", triStateBus());
    }

    private static void write(Path directory, String name, CircuitDocument circuit) {
        Path file = directory.resolve(name + "." + ProjectFormat.EXTENSION);
        ProjectFormat.save(CircuitProject.of(name, circuit), file);
        System.out.println("Wrote " + file.toAbsolutePath());
    }

    /** A switch, an inverter and an LED. */
    private static CircuitDocument inverter() {
        CircuitDocument circuit = circuit("Inverter: the LED is on while the switch is off");
        ComponentInstance input = add(circuit, "source.toggle", 96, 160, "A");
        ComponentInstance not = add(circuit, "logic.not", 264, 160, "");
        ComponentInstance led = add(circuit, "output.led", 424, 160, "Y");
        wire(circuit, input, "OUT", not, "A");
        wire(circuit, not, "Y", led, "IN");
        return circuit;
    }

    private static CircuitDocument twoInputGate(String definitionId, String description) {
        CircuitDocument circuit = circuit(description);
        ComponentInstance a = add(circuit, "source.toggle", 96, 112, "A");
        ComponentInstance b = add(circuit, "source.toggle", 96, 224, "B");
        ComponentInstance gate = add(circuit, definitionId, 288, 168, "");
        ComponentInstance led = add(circuit, "output.led", 456, 168, "Y");
        wire(circuit, a, "OUT", gate, "IN0");
        wire(circuit, b, "OUT", gate, "IN1");
        wire(circuit, gate, "OUT", led, "IN");
        return circuit;
    }

    /** Sum from an XOR, carry from an AND. */
    private static CircuitDocument halfAdder() {
        CircuitDocument circuit = circuit("Half adder: SUM = A xor B, CARRY = A and B");
        ComponentInstance a = add(circuit, "source.toggle", 96, 120, "A");
        ComponentInstance b = add(circuit, "source.toggle", 96, 264, "B");
        ComponentInstance xor = add(circuit, "logic.xor", 320, 120, "SUM_XOR");
        ComponentInstance and = add(circuit, "logic.and", 320, 288, "CARRY_AND");
        ComponentInstance sum = add(circuit, "output.led", 520, 120, "SUM");
        ComponentInstance carry = add(circuit, "output.led", 520, 288, "CARRY");
        wire(circuit, a, "OUT", xor, "IN0");
        wire(circuit, b, "OUT", xor, "IN1");
        wire(circuit, a, "OUT", and, "IN0");
        wire(circuit, b, "OUT", and, "IN1");
        wire(circuit, xor, "OUT", sum, "IN");
        wire(circuit, and, "OUT", carry, "IN");
        return circuit;
    }

    /**
     * Two tri-state buffers sharing one net: with both disabled the net floats (Z), with
     * one enabled it carries that value, with both enabled and disagreeing it becomes X.
     */
    private static CircuitDocument triStateBus() {
        CircuitDocument circuit = circuit("Two drivers on one bus: Z when idle, X when they disagree");
        ComponentInstance low = add(circuit, "source.zero", 96, 96, "");
        ComponentInstance high = add(circuit, "source.one", 96, 296, "");
        ComponentInstance enableA = add(circuit, "source.toggle", 96, 176, "ENABLE_A");
        ComponentInstance enableB = add(circuit, "source.toggle", 96, 376, "ENABLE_B");
        ComponentInstance driverA = add(circuit, "logic.tristate", 320, 120, "DRIVER_A");
        ComponentInstance driverB = add(circuit, "logic.tristate", 320, 320, "DRIVER_B");
        ComponentInstance probe = add(circuit, "output.probe", 520, 216, "BUS");
        wire(circuit, low, "OUT", driverA, "A");
        wire(circuit, enableA, "OUT", driverA, "ENABLE");
        wire(circuit, high, "OUT", driverB, "A");
        wire(circuit, enableB, "OUT", driverB, "ENABLE");
        wire(circuit, driverA, "Y", probe, "IN");
        wire(circuit, driverB, "Y", probe, "IN");
        return circuit;
    }

    private static CircuitDocument circuit(String description) {
        return new CircuitDocument(new CircuitMetadata(CircuitProject.MAIN_CIRCUIT, description));
    }

    private static ComponentInstance add(CircuitDocument circuit, String definitionId, double x,
                                         double y, String label) {
        ParameterValues parameters = REGISTRY.require(definitionId).definition().defaultParameters();
        if (definitionId.equals("output.led") && label.equals("CARRY")) {
            parameters = parameters.with(LibraryParameters.LED_COLOR, "amber");
        }
        ComponentInstance instance = ComponentInstance
                .create(definitionId, new CircuitPoint(x, y), parameters).withLabel(label);
        circuit.addComponent(instance);
        return instance;
    }

    private static void wire(CircuitDocument circuit, ComponentInstance from, String fromPort,
                             ComponentInstance to, String toPort) {
        circuit.addConnection(Connection.create(new PortReference(from.id(), fromPort),
                new PortReference(to.id(), toPort)));
    }
}
