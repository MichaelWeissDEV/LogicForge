package dev.logicforge.app;

import dev.logicforge.circuit.chip.ChipInstance;
import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitMetadata;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.ElectricalEndpoint;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.compiler.CircuitCompiler;
import dev.logicforge.format.ProjectFormat;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.LibraryParameters;
import dev.logicforge.library.chip.StandardChipLibrary;
import dev.logicforge.processor.lf8.Lf8ComputerFactory;
import dev.logicforge.processor.lf8.Lf8ImplementationMode;
import dev.logicforge.structures.StructuralCircuitFactory;
import java.io.IOException;
import java.nio.file.Files;
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
        write(directory.resolve("logic"), "gates", twoInputGate("logic.and",
                "Two switches demonstrate a basic two-input gate"));
        write(directory.resolve("logic"), "mux", componentDemo("routing.mux2", "MUX",
                defaults("routing.mux2").with(LibraryParameters.WIDTH, 1)));
        write(directory.resolve("logic"), "decoder", promote(
                StructuralCircuitFactory.decoder2To4Project(),
                StructuralCircuitFactory.DECODER_2_TO_4));
        write(directory.resolve("logic"), "sr-latch", promote(
                StructuralCircuitFactory.dFlipFlopProject(),
                StructuralCircuitFactory.SR_LATCH_NOR));
        write(directory.resolve("logic"), "d-latch", promote(
                StructuralCircuitFactory.dFlipFlopProject(), StructuralCircuitFactory.D_LATCH));
        write(directory.resolve("logic"), "dff", promote(
                StructuralCircuitFactory.dFlipFlopProject(), StructuralCircuitFactory.DFF));

        write(directory.resolve("arithmetic"), "half-adder", promote(
                StructuralCircuitFactory.halfAdderProject(), StructuralCircuitFactory.HALF_ADDER));
        write(directory.resolve("arithmetic"), "full-adder", promote(
                StructuralCircuitFactory.fullAdderProject(), StructuralCircuitFactory.FULL_ADDER));
        write(directory.resolve("arithmetic"), "ripple-adder8", promote(
                StructuralCircuitFactory.rippleAdderProject(8),
                StructuralCircuitFactory.rippleAdderName(8)));
        write(directory.resolve("arithmetic"), "alu8", promote(
                StructuralCircuitFactory.alu8Project(), StructuralCircuitFactory.ALU8));

        write(directory.resolve("memory"), "register8", promote(
                StructuralCircuitFactory.register8Project(), StructuralCircuitFactory.REGISTER8));
        write(directory.resolve("memory"), "register-file8x8", promote(
                StructuralCircuitFactory.registerFile8x8Project(),
                StructuralCircuitFactory.REGISTER_FILE_8X8));
        write(directory.resolve("memory"), "ram", componentDemo("memory.ram", "RAM",
                defaults("memory.ram").with(LibraryParameters.ADDRESS_WIDTH, 4)
                        .with(LibraryParameters.WIDTH, 8)));
        write(directory.resolve("memory"), "rom", componentDemo("memory.rom", "ROM",
                defaults("memory.rom").with(LibraryParameters.ADDRESS_WIDTH, 4)
                        .with(LibraryParameters.WIDTH, 8)
                        .with(LibraryParameters.ROM_CONTENTS, "48 65 6c 6c 6f")));

        write(directory.resolve("physical-ic"), "74hc-nand", nand74hc00());
        write(directory.resolve("physical-ic"), "74hc-half-adder", halfAdder74hc());
        write(directory.resolve("physical-ic"), "74hc283-adder", adder74hc283());

        int[] basicProgram = {0x01, 0, 5, 0x01, 1, 3, 0x03, 0, 1,
                0x06, 0, 0x00, 0x80, 0xff};
        write(directory.resolve("lf8"), "lf8-fast",
                Lf8ComputerFactory.create(Lf8ImplementationMode.FAST, basicProgram));
        write(directory.resolve("lf8"), "lf8-structural",
                Lf8ComputerFactory.create(Lf8ImplementationMode.STRUCTURAL, basicProgram));
        write(directory.resolve("lf8"), "lf8-gate-level",
                Lf8ComputerFactory.create(Lf8ImplementationMode.GATE_LEVEL, basicProgram));
    }

    private static void write(Path directory, String name, CircuitProject project) {
        createDirectories(directory);
        Path file = directory.resolve(name + "." + ProjectFormat.EXTENSION);
        ProjectFormat.save(project, file);
        new CircuitCompiler(REGISTRY).compile(ProjectFormat.load(file), CircuitProject.MAIN_CIRCUIT);
        System.out.println("Wrote " + file.toAbsolutePath());
    }

    private static void write(Path directory, String name, CircuitDocument circuit) {
        write(directory, name, CircuitProject.of(name, circuit));
    }

    private static void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not create example directory " + directory,
                    failure);
        }
    }

    /** Makes a structural circuit the visible main page while retaining all of its children. */
    private static CircuitProject promote(CircuitProject source, String circuitName) {
        CircuitDocument sourceMain = source.circuit(circuitName).orElseThrow();
        CircuitDocument main = new CircuitDocument(new CircuitMetadata(CircuitProject.MAIN_CIRCUIT,
                sourceMain.metadata().description()));
        sourceMain.components().forEach(main::addComponent);
        sourceMain.connections().forEach(main::addConnection);
        CircuitProject result = CircuitProject.of(source.name(), main);
        source.circuits().stream()
                .filter(circuit -> !circuit.metadata().name().equals(CircuitProject.MAIN_CIRCUIT))
                .forEach(result::putCircuit);
        return result;
    }

    private static CircuitDocument componentDemo(String definitionId, String label,
                                                 ParameterValues parameters) {
        CircuitDocument circuit = circuit("Interactive " + label + " component example");
        ComponentInstance component = ComponentInstance.create(definitionId,
                new CircuitPoint(280, 180), parameters).withLabel(label);
        circuit.addComponent(component);
        return circuit;
    }

    private static ParameterValues defaults(String definitionId) {
        return REGISTRY.require(definitionId).definition().defaultParameters();
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

    // ------------------------------------------------------- physical IC examples

    private static ChipInstance addChip(CircuitDocument circuit, String partNumber, double x, double y,
                                        String designator) {
        ChipInstance instance = ChipInstance.create(partNumber, new CircuitPoint(x, y), designator);
        circuit.addChip(instance);
        return instance;
    }

    private static void wireToChip(CircuitDocument circuit, ComponentInstance from, String fromPort,
                                   ChipInstance chip, int pinNumber) {
        circuit.addConnection(Connection.create(
                new ElectricalEndpoint.ComponentEndpoint(PortEndpoint.whole(new PortReference(from.id(), fromPort))),
                new ElectricalEndpoint.ChipPinEndpoint(chip.id(), pinNumber)));
    }

    private static void wireFromChip(CircuitDocument circuit, ChipInstance chip, int pinNumber,
                                     ComponentInstance to, String toPort) {
        circuit.addConnection(Connection.create(
                new ElectricalEndpoint.ChipPinEndpoint(chip.id(), pinNumber),
                new ElectricalEndpoint.ComponentEndpoint(PortEndpoint.whole(new PortReference(to.id(), toPort)))));
    }

    /**
     * One gate of a 74HC00 quad NAND, wired directly through its physical pins: pin 1 (1A)
     * and pin 2 (1B) are the inputs, pin 3 (1Y) is the output — as simple a physical-pin
     * demonstration as the chip pipeline allows.
     */
    private static CircuitDocument nand74hc00() {
        CircuitDocument circuit = circuit("74HC00: one NAND gate wired through its physical pins");
        ComponentInstance a = add(circuit, "source.toggle", 96, 112, "A");
        ComponentInstance b = add(circuit, "source.toggle", 96, 224, "B");
        ChipInstance chip = addChip(circuit, "74HC00", 360, 168, "U1");
        ComponentInstance led = add(circuit, "output.led", 620, 168, "Y");
        wireToChip(circuit, a, "OUT", chip, 1); // pin 1 = 1A
        wireToChip(circuit, b, "OUT", chip, 2); // pin 2 = 1B
        wireFromChip(circuit, chip, 3, led, "IN"); // pin 3 = 1Y
        return circuit;
    }

    /**
     * A half adder built from two real 74-series packages instead of behavioral gates:
     * SUM from a 74HC86 (XOR) gate, CARRY from a 74HC08 (AND) gate, both driven by the same
     * two switches through their own physical pins — a chip-to-chip-adjacent wiring pattern.
     */
    private static CircuitDocument halfAdder74hc() {
        CircuitDocument circuit = circuit("74HC86 XOR + 74HC08 AND: a half adder from real packages");
        ComponentInstance a = add(circuit, "source.toggle", 96, 96, "A");
        ComponentInstance b = add(circuit, "source.toggle", 96, 320, "B");
        ChipInstance xor = addChip(circuit, "74HC86", 380, 120, "U1");
        ChipInstance and = addChip(circuit, "74HC08", 380, 320, "U2");
        ComponentInstance sum = add(circuit, "output.led", 660, 120, "SUM");
        ComponentInstance carry = add(circuit, "output.led", 660, 320, "CARRY");

        wireToChip(circuit, a, "OUT", xor, 1); // U1 pin 1 = 1A
        wireToChip(circuit, b, "OUT", xor, 2); // U1 pin 2 = 1B
        wireFromChip(circuit, xor, 3, sum, "IN"); // U1 pin 3 = 1Y

        wireToChip(circuit, a, "OUT", and, 1); // U2 pin 1 = 1A
        wireToChip(circuit, b, "OUT", and, 2); // U2 pin 2 = 1B
        wireFromChip(circuit, and, 3, carry, "IN"); // U2 pin 3 = 1Y
        return circuit;
    }

    /**
     * A 4-bit adder built entirely from one 74HC283 package: four A/B switch pairs, a carry
     *-in switch, and five LEDs for SUM0..SUM3 and COUT — every one of them wired to a named
     * physical pin, so the pinout is trivial to read straight off the schematic.
     */
    private static CircuitDocument adder74hc283() {
        CircuitDocument circuit = circuit("74HC283: 4-bit binary adder wired pin-for-pin");
        ChipInstance chip = addChip(circuit, "74HC283", 420, 260, "U1");

        // Pin numbers from the 74HC283 pinout registered in StandardChipLibrary: bit 0 is
        // A1/B1/S1 (pins 5/6/4), bit 1 is A2/B2/S2 (pins 3/2/1), bit 2 is A3/B3/S3
        // (pins 14/15/13), bit 3 is A4/B4/S4 (pins 12/11/10); CIN is pin 7, COUT is pin 9.
        int[] aPins = {5, 3, 14, 12};
        int[] bPins = {6, 2, 15, 11};
        int[] sumPins = {4, 1, 13, 10};

        double leftX = 96;
        for (int bit = 0; bit < 4; bit++) {
            double y = 40 + bit * 100;
            ComponentInstance switchA = add(circuit, "source.toggle", leftX, y, "A" + bit);
            ComponentInstance switchB = add(circuit, "source.toggle", leftX, y + 40, "B" + bit);
            wireToChip(circuit, switchA, "OUT", chip, aPins[bit]);
            wireToChip(circuit, switchB, "OUT", chip, bPins[bit]);
        }
        ComponentInstance cin = add(circuit, "source.toggle", leftX, 480, "CIN");
        wireToChip(circuit, cin, "OUT", chip, 7);

        double rightX = 760;
        for (int bit = 0; bit < 4; bit++) {
            double y = 40 + bit * 100;
            ComponentInstance sumLed = add(circuit, "output.led", rightX, y + 20, "SUM" + bit);
            wireFromChip(circuit, chip, sumPins[bit], sumLed, "IN");
        }
        ComponentInstance cout = add(circuit, "output.led", rightX, 480, "COUT");
        wireFromChip(circuit, chip, 9, cout, "IN");
        return circuit;
    }
}
