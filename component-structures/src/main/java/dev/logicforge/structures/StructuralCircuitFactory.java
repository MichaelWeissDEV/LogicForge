package dev.logicforge.structures;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitMetadata;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.Connection;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.SubcircuitSupport;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.LibraryParameters;
import java.util.List;

/** Builds canonical reference circuits entirely from ordinary gates and subcircuits. */
public final class StructuralCircuitFactory {

    public static final String HALF_ADDER = "STRUCT_HALF_ADDER";
    public static final String FULL_ADDER = "STRUCT_FULL_ADDER";
    public static final String MUX2 = "STRUCT_MUX2";
    public static final String DECODER_2_TO_4 = "STRUCT_DECODER_2_TO_4";
    public static final String DECODER_3_TO_8 = "STRUCT_DECODER_3_TO_8";
    public static final String SR_LATCH_NOR = "STRUCT_SR_LATCH_NOR";
    public static final String D_LATCH = "STRUCT_D_LATCH";
    public static final String DFF = "STRUCT_DFF";
    public static final String D_LATCH_RESET = "STRUCT_D_LATCH_RESET";
    public static final String DFF_RESET = "STRUCT_DFF_RESET";
    public static final String REGISTER8 = "STRUCT_REGISTER8";
    public static final String REGISTER_FILE_8X8 = "STRUCT_REGISTER_FILE_8X8";
    public static final String REGISTER_FILE_8X8_RESET = "STRUCT_REGISTER_FILE_8X8_RESET";
    public static final String ALU8 = "STRUCT_ALU8";
    public static final String LOADABLE_COUNTER16 = "STRUCT_LOADABLE_COUNTER16";
    public static final String MODULO_COUNTER3 = "STRUCT_MODULO_COUNTER3";

    private StructuralCircuitFactory() {
    }

    public static CircuitProject halfAdderProject() {
        CircuitProject project = baseProject("struct-half-adder");
        project.putCircuit(halfAdderCircuit());
        return project;
    }

    public static CircuitProject fullAdderProject() {
        CircuitProject project = baseProject("struct-full-adder");
        project.putCircuit(halfAdderCircuit());
        project.putCircuit(fullAdderCircuit());
        return project;
    }

    public static CircuitProject rippleAdderProject(int width) {
        requireRippleWidth(width);
        CircuitProject project = baseProject("ripple-adder-" + width);
        project.putCircuit(halfAdderCircuit());
        project.putCircuit(fullAdderCircuit());
        project.putCircuit(rippleAdderCircuit(width));
        return project;
    }

    public static CircuitProject mux2Project() {
        CircuitProject project = baseProject("struct-mux2");
        project.putCircuit(mux2Circuit());
        return project;
    }

    public static CircuitProject decoder2To4Project() {
        CircuitProject project = baseProject("struct-decoder-2-to-4");
        project.putCircuit(decoder2To4Circuit());
        return project;
    }

    public static CircuitProject dFlipFlopProject() {
        CircuitProject project = baseProject("struct-dff");
        project.putCircuit(srLatchNorCircuit());
        project.putCircuit(dLatchCircuit());
        project.putCircuit(dFlipFlopCircuit());
        return project;
    }

    public static CircuitProject resettableDFlipFlopProject() {
        CircuitProject project = baseProject("struct-dff-reset");
        project.putCircuit(srLatchNorCircuit());
        project.putCircuit(resettableDLatchCircuit());
        project.putCircuit(resettableDFlipFlopCircuit());
        return project;
    }

    public static CircuitProject register8Project() {
        return structuralRegister(8, false, 0);
    }

    /** Builds a supported-width register exclusively from structural mux and DFF cells. */
    public static CircuitProject structuralRegister(int width, boolean resetSupport,
                                                    long resetValue) {
        requireRegisterWidth(width);
        long mask = width == 64 ? -1L : (1L << width) - 1;
        if (resetValue < 0 || (resetValue & ~mask) != 0) {
            throw new IllegalArgumentException("Reset value does not fit Register" + width);
        }
        CircuitProject project = resetSupport
                ? resettableDFlipFlopProject() : dFlipFlopProject();
        project.setName("struct-register" + width);
        project.putCircuit(mux2Circuit());
        project.putCircuit(registerCircuit(width, resetSupport, resetValue));
        return project;
    }

    public static String structuralRegisterName(int width, boolean resetSupport, long resetValue) {
        requireRegisterWidth(width);
        if (!resetSupport && width == 8) {
            return REGISTER8;
        }
        return "STRUCT_REGISTER" + width + (resetSupport
                ? "_RESET_" + Long.toUnsignedString(resetValue, 16).toUpperCase() : "");
    }

    /** 16-bit structural loadable counter; RESET may initialize values such as BFFF. */
    public static CircuitProject loadableCounter16Project(int resetValue) {
        if ((resetValue & ~0xffff) != 0) {
            throw new IllegalArgumentException("Counter reset value must fit 16 bits");
        }
        CircuitProject project = structuralRegister(16, true, resetValue);
        mergeChildren(project, rippleAdderProject(16));
        project.putCircuit(busMuxCircuit(16));
        project.putCircuit(loadableCounter16Circuit(resetValue));
        project.setName("struct-loadable-counter16");
        return project;
    }

    public static String loadableCounter16Name(int resetValue) {
        if ((resetValue & 0xffff) == 0) {
            return LOADABLE_COUNTER16;
        }
        return LOADABLE_COUNTER16 + "_RESET_"
                + Integer.toUnsignedString(resetValue & 0xffff, 16).toUpperCase();
    }

    public static CircuitProject moduloCounter3Project() {
        CircuitProject project = structuralRegister(3, true, 0);
        mergeChildren(project, rippleAdderProject(3));
        project.putCircuit(busMuxCircuit(3));
        project.putCircuit(moduloCounter3Circuit());
        project.setName("struct-modulo-counter3");
        return project;
    }

    public static CircuitProject alu8Project() {
        CircuitProject project = rippleAdderProject(8);
        project.setName("struct-alu8");
        project.putCircuit(mux2Circuit());
        project.putCircuit(decoder3To8Circuit());
        project.putCircuit(alu8Circuit());
        return project;
    }

    public static CircuitProject registerFile8x8Project() {
        CircuitProject project = register8Project();
        project.setName("struct-register-file-8x8");
        project.putCircuit(decoder3To8Circuit());
        project.putCircuit(registerFile8x8Circuit(false));
        return project;
    }

    public static CircuitProject resettableRegisterFile8x8Project() {
        CircuitProject project = structuralRegister(8, true, 0);
        project.setName("struct-register-file-8x8-reset");
        project.putCircuit(decoder3To8Circuit());
        project.putCircuit(registerFile8x8Circuit(true));
        return project;
    }

    public static String rippleAdderName(int width) {
        requireRippleWidth(width);
        return "RIPPLE_ADDER" + width;
    }

    private static CircuitProject baseProject(String name) {
        return CircuitProject.empty(name);
    }

    private static CircuitDocument halfAdderCircuit() {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument circuit = document(HALF_ADDER,
                "Gate-level half adder: XOR produces SUM and AND produces CARRY");
        ComponentInstance a = input(circuit, registry, -180, -30, "A", 1);
        ComponentInstance b = input(circuit, registry, -180, 50, "B", 1);
        ComponentInstance xor = component(circuit, registry, "logic.xor", -20, -40, "SUM_XOR");
        ComponentInstance and = component(circuit, registry, "logic.and", -20, 60, "CARRY_AND");
        ComponentInstance sum = output(circuit, registry, 160, -40, "SUM", 1);
        ComponentInstance carry = output(circuit, registry, 160, 60, "CARRY", 1);
        wire(circuit, a, "OUT", xor, "IN0");
        wire(circuit, b, "OUT", xor, "IN1");
        wire(circuit, a, "OUT", and, "IN0");
        wire(circuit, b, "OUT", and, "IN1");
        wire(circuit, xor, "OUT", sum, "IN");
        wire(circuit, and, "OUT", carry, "IN");
        return circuit;
    }

    private static CircuitDocument fullAdderCircuit() {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument circuit = document(FULL_ADDER,
                "Hierarchical full adder built from two structural half adders and OR");
        ComponentInstance a = input(circuit, registry, -240, -80, "A", 1);
        ComponentInstance b = input(circuit, registry, -240, 0, "B", 1);
        ComponentInstance cin = input(circuit, registry, -240, 80, "CIN", 1);
        ComponentInstance first = subcircuit(circuit, HALF_ADDER, -80, -40, "HALF_ADDER_1");
        ComponentInstance second = subcircuit(circuit, HALF_ADDER, 70, -40, "HALF_ADDER_2");
        ComponentInstance carryOr = component(circuit, registry, "logic.or", 80, 100, "CARRY_OR");
        ComponentInstance sum = output(circuit, registry, 260, -40, "SUM", 1);
        ComponentInstance cout = output(circuit, registry, 260, 100, "COUT", 1);
        wire(circuit, a, "OUT", first, "A");
        wire(circuit, b, "OUT", first, "B");
        wire(circuit, first, "SUM", second, "A");
        wire(circuit, cin, "OUT", second, "B");
        wire(circuit, second, "SUM", sum, "IN");
        wire(circuit, first, "CARRY", carryOr, "IN0");
        wire(circuit, second, "CARRY", carryOr, "IN1");
        wire(circuit, carryOr, "OUT", cout, "IN");
        return circuit;
    }

    private static CircuitDocument rippleAdderCircuit(int width) {
        ComponentRegistry registry = ComponentRegistry.standard();
        String name = rippleAdderName(width);
        CircuitDocument circuit = document(name,
                width + "-bit ripple-carry adder with explicit full-adder stages");
        ComponentInstance a = input(circuit, registry, -360, -80, "A", width);
        ComponentInstance b = input(circuit, registry, -360, 0, "B", width);
        ComponentInstance cin = input(circuit, registry, -360, 80, "CIN", 1);
        ComponentInstance sum = output(circuit, registry, 360, -80, "SUM", width);
        ComponentInstance cout = output(circuit, registry, 360, 80, "COUT", 1);
        ParameterValues routingParameters = registry.require("routing.splitter")
                .definition().defaultParameters().with(LibraryParameters.WIDTH, width);
        ComponentInstance aBits = component(circuit, registry, "routing.splitter", -300, -80,
                routingParameters, "A_BITS");
        ComponentInstance bBits = component(circuit, registry, "routing.splitter", -300, 0,
                routingParameters, "B_BITS");
        ComponentInstance sumBits = component(circuit, registry, "routing.joiner", 300, -80,
                registry.require("routing.joiner").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, width), "SUM_BITS");
        wire(circuit, a, "OUT", aBits, "BUS");
        wire(circuit, b, "OUT", bBits, "BUS");
        wire(circuit, sumBits, "BUS", sum, "IN");

        ComponentInstance previous = null;
        for (int bit = 0; bit < width; bit++) {
            ComponentInstance stage = subcircuit(circuit, FULL_ADDER,
                    -240 + bit * 70.0, 0, "FULL_ADDER_" + bit);
            wire(circuit, aBits, "BIT" + bit, stage, "A");
            wire(circuit, bBits, "BIT" + bit, stage, "B");
            if (previous == null) {
                wire(circuit, cin, "OUT", stage, "CIN");
            } else {
                wire(circuit, previous, "COUT", stage, "CIN");
            }
            wire(circuit, stage, "SUM", sumBits, "BIT" + bit);
            previous = stage;
        }
        wire(circuit, previous, "COUT", cout, "IN");
        return circuit;
    }

    private static CircuitDocument mux2Circuit() {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument circuit = document(MUX2,
                "Gate-level 2:1 multiplexer: (!S & A) | (S & B)");
        ComponentInstance a = input(circuit, registry, -220, -70, "A", 1);
        ComponentInstance b = input(circuit, registry, -220, 0, "B", 1);
        ComponentInstance s = input(circuit, registry, -220, 70, "S", 1);
        ComponentInstance notS = component(circuit, registry, "logic.not", -100, 70, "NOT_S");
        ComponentInstance selectA = component(circuit, registry, "logic.and", 0, -50, "SELECT_A");
        ComponentInstance selectB = component(circuit, registry, "logic.and", 0, 50, "SELECT_B");
        ComponentInstance combine = component(circuit, registry, "logic.or", 110, 0, "COMBINE");
        ComponentInstance y = output(circuit, registry, 240, 0, "Y", 1);
        wire(circuit, s, "OUT", notS, "A");
        wire(circuit, a, "OUT", selectA, "IN0");
        wire(circuit, notS, "Y", selectA, "IN1");
        wire(circuit, b, "OUT", selectB, "IN0");
        wire(circuit, s, "OUT", selectB, "IN1");
        wire(circuit, selectA, "OUT", combine, "IN0");
        wire(circuit, selectB, "OUT", combine, "IN1");
        wire(circuit, combine, "OUT", y, "IN");
        return circuit;
    }

    private static CircuitDocument decoder2To4Circuit() {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument circuit = document(DECODER_2_TO_4,
                "Gate-level 2-to-4 decoder using inverted and true select terms");
        ComponentInstance address = input(circuit, registry, -260, 0, "A", 2);
        ComponentInstance outputs = output(circuit, registry, 260, 0, "Y", 4);
        ComponentInstance split = component(circuit, registry, "routing.splitter", -180, 0,
                registry.require("routing.splitter").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 2), "ADDRESS_BITS");
        ComponentInstance join = component(circuit, registry, "routing.joiner", 180, 0,
                registry.require("routing.joiner").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 4), "OUTPUT_BITS");
        ComponentInstance not0 = component(circuit, registry, "logic.not", -100, -50, "NOT_A0");
        ComponentInstance not1 = component(circuit, registry, "logic.not", -100, 50, "NOT_A1");
        wire(circuit, address, "OUT", split, "BUS");
        wire(circuit, split, "BIT0", not0, "A");
        wire(circuit, split, "BIT1", not1, "A");
        for (int value = 0; value < 4; value++) {
            ComponentInstance term = component(circuit, registry, "logic.and", 40,
                    -90 + value * 60.0, "DECODE_" + value);
            wire(circuit, (value & 1) == 0 ? not0 : split,
                    (value & 1) == 0 ? "Y" : "BIT0", term, "IN0");
            wire(circuit, (value & 2) == 0 ? not1 : split,
                    (value & 2) == 0 ? "Y" : "BIT1", term, "IN1");
            wire(circuit, term, "OUT", join, "BIT" + value);
        }
        wire(circuit, join, "BUS", outputs, "IN");
        return circuit;
    }

    private static CircuitDocument decoder3To8Circuit() {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument circuit = document(DECODER_3_TO_8,
                "Gate-level 3-to-8 decoder using inverters and 3-input product terms");
        ComponentInstance address = input(circuit, registry, -300, 0, "A", 3);
        ComponentInstance outputs = output(circuit, registry, 300, 0, "Y", 8);
        ComponentInstance split = component(circuit, registry, "routing.splitter", -220, 0,
                registry.require("routing.splitter").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 3), "ADDRESS_BITS");
        ComponentInstance join = component(circuit, registry, "routing.joiner", 220, 0,
                registry.require("routing.joiner").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8), "OUTPUT_BITS");
        ComponentInstance[] inverted = new ComponentInstance[3];
        wire(circuit, address, "OUT", split, "BUS");
        for (int bit = 0; bit < 3; bit++) {
            inverted[bit] = component(circuit, registry, "logic.not", -130,
                    -80 + bit * 80.0, "NOT_A" + bit);
            wire(circuit, split, "BIT" + bit, inverted[bit], "A");
        }
        for (int value = 0; value < 8; value++) {
            ComponentInstance term = component(circuit, registry, "logic.and", 50,
                    -210 + value * 60.0,
                    registry.require("logic.and").definition().defaultParameters()
                            .with(LibraryParameters.INPUT_COUNT, 3), "DECODE_" + value);
            for (int bit = 0; bit < 3; bit++) {
                boolean high = (value & (1 << bit)) != 0;
                wire(circuit, high ? split : inverted[bit], high ? "BIT" + bit : "Y",
                        term, "IN" + bit);
            }
            wire(circuit, term, "OUT", join, "BIT" + value);
        }
        wire(circuit, join, "BUS", outputs, "IN");
        return circuit;
    }

    private static CircuitDocument srLatchNorCircuit() {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument circuit = document(SR_LATCH_NOR,
                "Cross-coupled NOR SR latch with active-high S and R");
        ComponentInstance s = input(circuit, registry, -220, -50, "S", 1);
        ComponentInstance r = input(circuit, registry, -220, 50, "R", 1);
        ComponentInstance qGate = component(circuit, registry, "logic.nor", 0, 50, "Q_NOR");
        ComponentInstance qBarGate = component(circuit, registry, "logic.nor", 0, -50, "Q_BAR_NOR");
        ComponentInstance q = output(circuit, registry, 220, 50, "Q", 1);
        ComponentInstance qBar = output(circuit, registry, 220, -50, "Q_BAR", 1);
        wire(circuit, r, "OUT", qGate, "IN0");
        wire(circuit, qBarGate, "OUT", qGate, "IN1");
        wire(circuit, s, "OUT", qBarGate, "IN0");
        wire(circuit, qGate, "OUT", qBarGate, "IN1");
        wire(circuit, qGate, "OUT", q, "IN");
        wire(circuit, qBarGate, "OUT", qBar, "IN");
        return circuit;
    }

    private static CircuitDocument dLatchCircuit() {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument circuit = document(D_LATCH,
                "Structural active-high D latch feeding a NOR SR latch");
        ComponentInstance d = input(circuit, registry, -260, -50, "D", 1);
        ComponentInstance en = input(circuit, registry, -260, 50, "EN", 1);
        ComponentInstance notD = component(circuit, registry, "logic.not", -150, 0, "NOT_D");
        ComponentInstance setGate = component(circuit, registry, "logic.and", -60, -60, "SET_GATE");
        ComponentInstance resetGate = component(circuit, registry, "logic.and", -60, 60, "RESET_GATE");
        ComponentInstance sr = subcircuit(circuit, SR_LATCH_NOR, 70, 0, "SR_LATCH");
        ComponentInstance q = output(circuit, registry, 260, -40, "Q", 1);
        ComponentInstance qBar = output(circuit, registry, 260, 40, "Q_BAR", 1);
        wire(circuit, d, "OUT", notD, "A");
        wire(circuit, d, "OUT", setGate, "IN0");
        wire(circuit, en, "OUT", setGate, "IN1");
        wire(circuit, notD, "Y", resetGate, "IN0");
        wire(circuit, en, "OUT", resetGate, "IN1");
        wire(circuit, setGate, "OUT", sr, "S");
        wire(circuit, resetGate, "OUT", sr, "R");
        wire(circuit, sr, "Q", q, "IN");
        wire(circuit, sr, "Q_BAR", qBar, "IN");
        return circuit;
    }

    private static CircuitDocument dFlipFlopCircuit() {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument circuit = document(DFF,
                "Rising-edge master-slave D flip-flop built from two D latches");
        ComponentInstance d = input(circuit, registry, -280, -50, "D", 1);
        ComponentInstance clk = input(circuit, registry, -280, 50, "CLK", 1);
        ComponentInstance notClk = component(circuit, registry, "logic.not", -170, 60, "NOT_CLK");
        ComponentInstance master = subcircuit(circuit, D_LATCH, -60, 0, "MASTER_LATCH");
        ComponentInstance slave = subcircuit(circuit, D_LATCH, 100, 0, "SLAVE_LATCH");
        ComponentInstance q = output(circuit, registry, 280, -40, "Q", 1);
        ComponentInstance qBar = output(circuit, registry, 280, 40, "Q_BAR", 1);
        wire(circuit, clk, "OUT", notClk, "A");
        wire(circuit, d, "OUT", master, "D");
        wire(circuit, notClk, "Y", master, "EN");
        wire(circuit, master, "Q", slave, "D");
        wire(circuit, clk, "OUT", slave, "EN");
        wire(circuit, slave, "Q", q, "IN");
        wire(circuit, slave, "Q_BAR", qBar, "IN");
        return circuit;
    }

    private static CircuitDocument resettableDLatchCircuit() {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument circuit = document(D_LATCH_RESET,
                "Structural D latch with asynchronous active-high reset");
        ComponentInstance d = input(circuit, registry, -280, -80, "D", 1);
        ComponentInstance enable = input(circuit, registry, -280, 0, "EN", 1);
        ComponentInstance reset = input(circuit, registry, -280, 80, "RESET", 1);
        ComponentInstance notD = component(circuit, registry, "logic.not", -200, -80, "NOT_D");
        ComponentInstance notReset = component(circuit, registry, "logic.not", -200, 80,
                "NOT_RESET");
        ComponentInstance set = component(circuit, registry, "logic.and", -100, -60,
                registry.require("logic.and").definition().defaultParameters()
                        .with(LibraryParameters.INPUT_COUNT, 3), "SET_GATE");
        ComponentInstance dataReset = component(circuit, registry, "logic.and", -100, 40,
                "DATA_RESET_GATE");
        ComponentInstance resetOr = component(circuit, registry, "logic.or", -20, 60,
                "ASYNC_RESET_OR");
        ComponentInstance latch = subcircuit(circuit, SR_LATCH_NOR, 80, 0, "SR_LATCH");
        ComponentInstance q = output(circuit, registry, 260, -40, "Q", 1);
        ComponentInstance qBar = output(circuit, registry, 260, 40, "Q_BAR", 1);
        wire(circuit, d, "OUT", notD, "A");
        wire(circuit, reset, "OUT", notReset, "A");
        wire(circuit, d, "OUT", set, "IN0");
        wire(circuit, enable, "OUT", set, "IN1");
        wire(circuit, notReset, "Y", set, "IN2");
        wire(circuit, notD, "Y", dataReset, "IN0");
        wire(circuit, enable, "OUT", dataReset, "IN1");
        wire(circuit, dataReset, "OUT", resetOr, "IN0");
        wire(circuit, reset, "OUT", resetOr, "IN1");
        wire(circuit, set, "OUT", latch, "S");
        wire(circuit, resetOr, "OUT", latch, "R");
        wire(circuit, latch, "Q", q, "IN");
        wire(circuit, latch, "Q_BAR", qBar, "IN");
        return circuit;
    }

    private static CircuitDocument resettableDFlipFlopCircuit() {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument circuit = document(DFF_RESET,
                "Resettable master-slave DFF built from resettable structural latches");
        ComponentInstance d = input(circuit, registry, -280, -50, "D", 1);
        ComponentInstance clk = input(circuit, registry, -280, 20, "CLK", 1);
        ComponentInstance reset = input(circuit, registry, -280, 90, "RESET", 1);
        ComponentInstance notClk = component(circuit, registry, "logic.not", -180, 40,
                "NOT_CLK");
        ComponentInstance master = subcircuit(circuit, D_LATCH_RESET, -60, 0,
                "MASTER_LATCH");
        ComponentInstance slave = subcircuit(circuit, D_LATCH_RESET, 100, 0,
                "SLAVE_LATCH");
        ComponentInstance q = output(circuit, registry, 280, -40, "Q", 1);
        ComponentInstance qBar = output(circuit, registry, 280, 40, "Q_BAR", 1);
        wire(circuit, clk, "OUT", notClk, "A");
        wire(circuit, d, "OUT", master, "D");
        wire(circuit, notClk, "Y", master, "EN");
        wire(circuit, reset, "OUT", master, "RESET");
        wire(circuit, master, "Q", slave, "D");
        wire(circuit, clk, "OUT", slave, "EN");
        wire(circuit, reset, "OUT", slave, "RESET");
        wire(circuit, slave, "Q", q, "IN");
        wire(circuit, slave, "Q_BAR", qBar, "IN");
        return circuit;
    }

    private static CircuitDocument registerCircuit(int width, boolean resetSupport,
                                                   long resetValue) {
        ComponentRegistry registry = ComponentRegistry.standard();
        String name = structuralRegisterName(width, resetSupport, resetValue);
        CircuitDocument circuit = document(name,
                width + " structural D flip-flops, each fed by a structural load multiplexer");
        ComponentInstance data = input(circuit, registry, -360, -80, "DATA", width);
        ComponentInstance load = input(circuit, registry, -360, 0, "LOAD", 1);
        ComponentInstance clk = input(circuit, registry, -360, 80, "CLK", 1);
        ComponentInstance reset = resetSupport
                ? input(circuit, registry, -360, 140, "RESET", 1) : null;
        ComponentInstance q = output(circuit, registry, 360, -80, "Q", width);
        ComponentInstance dataBits = component(circuit, registry, "routing.splitter", -280, -80,
                registry.require("routing.splitter").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, width), "DATA_BITS");
        ComponentInstance qBits = component(circuit, registry, "routing.joiner", 280, -80,
                registry.require("routing.joiner").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, width), "Q_BITS");
        wire(circuit, data, "OUT", dataBits, "BUS");
        wire(circuit, qBits, "BUS", q, "IN");
        for (int bit = 0; bit < width; bit++) {
            ComponentInstance mux = subcircuit(circuit, MUX2, -100 + bit * 40.0,
                    -20, "LOAD_MUX_" + bit);
            ComponentInstance dff = subcircuit(circuit, resetSupport ? DFF_RESET : DFF,
                    -100 + bit * 40.0,
                    100, "DFF_" + bit);
            boolean resetOne = ((resetValue >>> bit) & 1) != 0;
            String logicalQ = resetOne ? "Q_BAR" : "Q";
            wire(circuit, dff, logicalQ, mux, "A");
            wire(circuit, dataBits, "BIT" + bit, mux, "B");
            wire(circuit, load, "OUT", mux, "S");
            if (resetOne) {
                ComponentInstance invertStored = component(circuit, registry, "logic.not",
                        -40 + bit * 40.0, 50, "RESET_ONE_STORED_NOT_" + bit);
                wire(circuit, mux, "Y", invertStored, "A");
                wire(circuit, invertStored, "Y", dff, "D");
            } else {
                wire(circuit, mux, "Y", dff, "D");
            }
            wire(circuit, clk, "OUT", dff, "CLK");
            if (resetSupport) {
                wire(circuit, reset, "OUT", dff, "RESET");
            }
            wire(circuit, dff, logicalQ, qBits, "BIT" + bit);
        }
        return circuit;
    }

    private static CircuitDocument busMuxCircuit(int width) {
        ComponentRegistry registry = ComponentRegistry.standard();
        String name = "STRUCT_MUX" + width;
        CircuitDocument circuit = document(name, width + "-bit mux made from scalar gate muxes");
        ComponentInstance a = input(circuit, registry, -320, -80, "A", width);
        ComponentInstance b = input(circuit, registry, -320, 0, "B", width);
        ComponentInstance select = input(circuit, registry, -320, 80, "S", 1);
        ComponentInstance y = output(circuit, registry, 320, -40, "Y", width);
        ComponentInstance aBits = component(circuit, registry, "routing.splitter", -240, -80,
                registry.require("routing.splitter").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, width), "A_BITS");
        ComponentInstance bBits = component(circuit, registry, "routing.splitter", -240, 0,
                registry.require("routing.splitter").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, width), "B_BITS");
        ComponentInstance yBits = component(circuit, registry, "routing.joiner", 240, -40,
                registry.require("routing.joiner").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, width), "Y_BITS");
        wire(circuit, a, "OUT", aBits, "BUS");
        wire(circuit, b, "OUT", bBits, "BUS");
        wire(circuit, yBits, "BUS", y, "IN");
        for (int bit = 0; bit < width; bit++) {
            ComponentInstance mux = subcircuit(circuit, MUX2, -100 + bit * 35.0, 20,
                    "MUX_" + bit);
            wire(circuit, aBits, "BIT" + bit, mux, "A");
            wire(circuit, bBits, "BIT" + bit, mux, "B");
            wire(circuit, select, "OUT", mux, "S");
            wire(circuit, mux, "Y", yBits, "BIT" + bit);
        }
        return circuit;
    }

    private static CircuitDocument loadableCounter16Circuit(int resetValue) {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument circuit = document(loadableCounter16Name(resetValue),
                "16-bit loadable counter made from Register16, RippleAdder16 and muxes");
        ComponentInstance clk = input(circuit, registry, -420, -160, "CLK", 1);
        ComponentInstance reset = input(circuit, registry, -420, -100, "RESET", 1);
        ComponentInstance load = input(circuit, registry, -420, -40, "LOAD", 1);
        ComponentInstance enable = input(circuit, registry, -420, 20, "ENABLE", 1);
        ComponentInstance data = input(circuit, registry, -420, 100, "DATA", 16);
        ComponentInstance count = output(circuit, registry, 420, -80, "COUNT", 16);
        String registerName = structuralRegisterName(16, true, resetValue);
        ComponentInstance storage = subcircuit(circuit, registerName, 160, -80, "REGISTER16");
        ComponentInstance adder = subcircuit(circuit, rippleAdderName(16), -160, -80,
                "INCREMENTER16");
        ComponentInstance enableMux = subcircuit(circuit, "STRUCT_MUX16", -20, -40,
                "ENABLE_MUX");
        ComponentInstance loadMux = subcircuit(circuit, "STRUCT_MUX16", 70, -40,
                "LOAD_MUX");
        ComponentInstance one = component(circuit, registry, "routing.bus_constant", -300, 0,
                registry.require("routing.bus_constant").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 16)
                        .with(LibraryParameters.BUS_CONSTANT_VALUE, "1"), "ONE");
        ComponentInstance zero = component(circuit, registry, "source.zero", -300, 60, "CIN_ZERO");
        ComponentInstance loadTie = component(circuit, registry, "source.one", 80, 80,
                "REGISTER_LOAD_TIE");
        wire(circuit, storage, "Q", adder, "A");
        wire(circuit, one, "OUT", adder, "B");
        wire(circuit, zero, "OUT", adder, "CIN");
        wire(circuit, storage, "Q", enableMux, "A");
        wire(circuit, adder, "SUM", enableMux, "B");
        wire(circuit, enable, "OUT", enableMux, "S");
        wire(circuit, enableMux, "Y", loadMux, "A");
        wire(circuit, data, "OUT", loadMux, "B");
        wire(circuit, load, "OUT", loadMux, "S");
        wire(circuit, loadMux, "Y", storage, "DATA");
        wire(circuit, loadTie, "OUT", storage, "LOAD");
        wire(circuit, clk, "OUT", storage, "CLK");
        wire(circuit, reset, "OUT", storage, "RESET");
        wire(circuit, storage, "Q", count, "IN");
        return circuit;
    }

    private static CircuitDocument moduloCounter3Circuit() {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument circuit = document(MODULO_COUNTER3,
                "Three-bit modulo-8 counter made from Register3 and RippleAdder3");
        ComponentInstance clk = input(circuit, registry, -360, -100, "CLK", 1);
        ComponentInstance reset = input(circuit, registry, -360, -40, "RESET", 1);
        ComponentInstance enable = input(circuit, registry, -360, 20, "ENABLE", 1);
        ComponentInstance count = output(circuit, registry, 360, -60, "COUNT", 3);
        ComponentInstance storage = subcircuit(circuit,
                structuralRegisterName(3, true, 0), 120, -60, "REGISTER3");
        ComponentInstance adder = subcircuit(circuit, rippleAdderName(3), -140, -60,
                "INCREMENTER3");
        ComponentInstance mux = subcircuit(circuit, "STRUCT_MUX3", 0, -20, "ENABLE_MUX");
        ComponentInstance one = component(circuit, registry, "routing.bus_constant", -280, 20,
                registry.require("routing.bus_constant").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 3)
                        .with(LibraryParameters.BUS_CONSTANT_VALUE, "1"), "ONE");
        ComponentInstance zero = component(circuit, registry, "source.zero", -280, 70, "CIN_ZERO");
        ComponentInstance loadTie = component(circuit, registry, "source.one", 40, 80,
                "REGISTER_LOAD_TIE");
        wire(circuit, storage, "Q", adder, "A");
        wire(circuit, one, "OUT", adder, "B");
        wire(circuit, zero, "OUT", adder, "CIN");
        wire(circuit, storage, "Q", mux, "A");
        wire(circuit, adder, "SUM", mux, "B");
        wire(circuit, enable, "OUT", mux, "S");
        wire(circuit, mux, "Y", storage, "DATA");
        wire(circuit, loadTie, "OUT", storage, "LOAD");
        wire(circuit, clk, "OUT", storage, "CLK");
        wire(circuit, reset, "OUT", storage, "RESET");
        wire(circuit, storage, "Q", count, "IN");
        return circuit;
    }

    private static CircuitDocument alu8Circuit() {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument circuit = document(ALU8,
                "Gate-level 8-bit ALU: ADD, SUB, AND, OR, XOR, NOT, SHL and SHR");
        ComponentInstance a = input(circuit, registry, -520, -160, "A", 8);
        ComponentInstance b = input(circuit, registry, -520, -80, "B", 8);
        ComponentInstance op = input(circuit, registry, -520, 0, "OP", 3);
        ComponentInstance result = output(circuit, registry, 560, -160, "RESULT", 8);
        ComponentInstance zeroFlag = output(circuit, registry, 560, -60, "Z", 1);
        ComponentInstance negativeFlag = output(circuit, registry, 560, 0, "N", 1);
        ComponentInstance carryFlag = output(circuit, registry, 560, 60, "C", 1);
        ComponentInstance overflowFlag = output(circuit, registry, 560, 120, "V", 1);

        ComponentInstance aBits = component(circuit, registry, "routing.splitter", -440, -160,
                registry.require("routing.splitter").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8), "A_BITS");
        ComponentInstance bBits = component(circuit, registry, "routing.splitter", -440, -80,
                registry.require("routing.splitter").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8), "B_BITS");
        ComponentInstance opBits = component(circuit, registry, "routing.splitter", -440, 0,
                registry.require("routing.splitter").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 3), "OP_BITS");
        ComponentInstance decoder = subcircuit(circuit, DECODER_3_TO_8, -330, 40, "OP_DECODER");
        ComponentInstance decodedBits = component(circuit, registry, "routing.splitter", -240, 40,
                registry.require("routing.splitter").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8), "DECODED_OP_BITS");
        wire(circuit, a, "OUT", aBits, "BUS");
        wire(circuit, b, "OUT", bBits, "BUS");
        wire(circuit, op, "OUT", opBits, "BUS");
        wire(circuit, op, "OUT", decoder, "A");
        wire(circuit, decoder, "Y", decodedBits, "BUS");

        ComponentInstance adjustedB = component(circuit, registry, "routing.joiner", -150, -80,
                registry.require("routing.joiner").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8), "ADJUSTED_B");
        ComponentInstance[] bXorSub = new ComponentInstance[8];
        for (int bit = 0; bit < 8; bit++) {
            bXorSub[bit] = component(circuit, registry, "logic.xor", -250 + bit * 35.0,
                    -120, "B_XOR_SUB_" + bit);
            wire(circuit, bBits, "BIT" + bit, bXorSub[bit], "IN0");
            wire(circuit, decodedBits, "BIT1", bXorSub[bit], "IN1");
            wire(circuit, bXorSub[bit], "OUT", adjustedB, "BIT" + bit);
        }
        ComponentInstance adder = subcircuit(circuit, rippleAdderName(8), -40, -200,
                "RIPPLE_ADDER8");
        wire(circuit, a, "OUT", adder, "A");
        wire(circuit, adjustedB, "BUS", adder, "B");
        wire(circuit, decodedBits, "BIT1", adder, "CIN");
        ComponentInstance adderBits = component(circuit, registry, "routing.splitter", 60, -200,
                registry.require("routing.splitter").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8), "ADDER_RESULT_BITS");
        wire(circuit, adder, "SUM", adderBits, "BUS");

        ComponentInstance resultJoin = component(circuit, registry, "routing.joiner", 400, -160,
                registry.require("routing.joiner").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8), "RESULT_BITS");
        ComponentInstance resultSplit = component(circuit, registry, "routing.splitter", 470, -160,
                registry.require("routing.splitter").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8), "RESULT_FLAGS_BITS");
        wire(circuit, resultJoin, "BUS", result, "IN");
        wire(circuit, resultJoin, "BUS", resultSplit, "BUS");
        ComponentInstance logicZero = component(circuit, registry, "source.zero", -80, 220,
                "LOGIC_ZERO");

        for (int bit = 0; bit < 8; bit++) {
            ComponentInstance and = component(circuit, registry, "logic.and", 40,
                    -80 + bit * 25.0, "AND_" + bit);
            ComponentInstance or = component(circuit, registry, "logic.or", 80,
                    -80 + bit * 25.0, "OR_" + bit);
            ComponentInstance xor = component(circuit, registry, "logic.xor", 120,
                    -80 + bit * 25.0, "XOR_" + bit);
            ComponentInstance not = component(circuit, registry, "logic.not", 160,
                    -80 + bit * 25.0, "NOT_" + bit);
            wire(circuit, aBits, "BIT" + bit, and, "IN0");
            wire(circuit, bBits, "BIT" + bit, and, "IN1");
            wire(circuit, aBits, "BIT" + bit, or, "IN0");
            wire(circuit, bBits, "BIT" + bit, or, "IN1");
            wire(circuit, aBits, "BIT" + bit, xor, "IN0");
            wire(circuit, bBits, "BIT" + bit, xor, "IN1");
            wire(circuit, aBits, "BIT" + bit, not, "A");

            Signal shl = bit == 0 ? new Signal(logicZero, "OUT")
                    : new Signal(aBits, "BIT" + (bit - 1));
            Signal shr = bit == 7 ? new Signal(logicZero, "OUT")
                    : new Signal(aBits, "BIT" + (bit + 1));
            Signal selected = selectEight(circuit, bit,
                    new Signal(adderBits, "BIT" + bit), new Signal(adderBits, "BIT" + bit),
                    new Signal(and, "OUT"), new Signal(or, "OUT"),
                    new Signal(xor, "OUT"), new Signal(not, "Y"), shl, shr,
                    opBits, -20 + bit * 45.0, 300);
            wire(circuit, selected.component(), selected.port(), resultJoin, "BIT" + bit);
        }

        Signal selectedCarry = selectEight(circuit, 8,
                new Signal(adder, "COUT"), new Signal(adder, "COUT"),
                new Signal(logicZero, "OUT"), new Signal(logicZero, "OUT"),
                new Signal(logicZero, "OUT"), new Signal(logicZero, "OUT"),
                new Signal(aBits, "BIT7"), new Signal(aBits, "BIT0"),
                opBits, 360, 300);
        wire(circuit, selectedCarry.component(), selectedCarry.port(), carryFlag, "IN");
        wire(circuit, resultSplit, "BIT7", negativeFlag, "IN");

        ComponentInstance anyResult = component(circuit, registry, "logic.or", 420, 20,
                registry.require("logic.or").definition().defaultParameters()
                        .with(LibraryParameters.INPUT_COUNT, 8), "RESULT_OR");
        ComponentInstance resultNot = component(circuit, registry, "logic.not", 480, -40,
                "RESULT_ZERO_NOT");
        for (int bit = 0; bit < 8; bit++) {
            wire(circuit, resultSplit, "BIT" + bit, anyResult, "IN" + bit);
        }
        wire(circuit, anyResult, "OUT", resultNot, "A");
        wire(circuit, resultNot, "Y", zeroFlag, "IN");

        ComponentInstance signRelation = component(circuit, registry, "logic.xor", 300, 100,
                "A_XOR_ADJUSTED_B_SIGN");
        ComponentInstance equalSigns = component(circuit, registry, "logic.not", 350, 100,
                "EQUAL_ADJUSTED_SIGNS");
        ComponentInstance resultSignChanged = component(circuit, registry, "logic.xor", 300, 150,
                "A_XOR_RESULT_SIGN");
        ComponentInstance rawOverflow = component(circuit, registry, "logic.and", 400, 130,
                "RAW_OVERFLOW");
        ComponentInstance arithmeticOp = component(circuit, registry, "logic.nor", 400, 190,
                "ARITHMETIC_OP");
        ComponentInstance gatedOverflow = component(circuit, registry, "logic.and", 480, 150,
                "GATED_OVERFLOW");
        wire(circuit, aBits, "BIT7", signRelation, "IN0");
        wire(circuit, bXorSub[7], "OUT", signRelation, "IN1");
        wire(circuit, signRelation, "OUT", equalSigns, "A");
        wire(circuit, aBits, "BIT7", resultSignChanged, "IN0");
        wire(circuit, resultSplit, "BIT7", resultSignChanged, "IN1");
        wire(circuit, equalSigns, "Y", rawOverflow, "IN0");
        wire(circuit, resultSignChanged, "OUT", rawOverflow, "IN1");
        wire(circuit, opBits, "BIT1", arithmeticOp, "IN0");
        wire(circuit, opBits, "BIT2", arithmeticOp, "IN1");
        wire(circuit, rawOverflow, "OUT", gatedOverflow, "IN0");
        wire(circuit, arithmeticOp, "OUT", gatedOverflow, "IN1");
        wire(circuit, gatedOverflow, "OUT", overflowFlag, "IN");
        return circuit;
    }

    private static CircuitDocument registerFile8x8Circuit(boolean resetSupport) {
        ComponentRegistry registry = ComponentRegistry.standard();
        CircuitDocument circuit = document(resetSupport
                        ? REGISTER_FILE_8X8_RESET : REGISTER_FILE_8X8,
                "Eight structural Register8 children, a gate decoder and two read mux networks");
        ComponentInstance rdAddrA = input(circuit, registry, -520, -180, "RD_ADDR_A", 3);
        ComponentInstance rdAddrB = input(circuit, registry, -520, -120, "RD_ADDR_B", 3);
        ComponentInstance wrAddr = input(circuit, registry, -520, -60, "WR_ADDR", 3);
        ComponentInstance wrData = input(circuit, registry, -520, 0, "WR_DATA", 8);
        ComponentInstance wrEn = input(circuit, registry, -520, 60, "WR_EN", 1);
        ComponentInstance clk = input(circuit, registry, -520, 120, "CLK", 1);
        ComponentInstance reset = resetSupport
                ? input(circuit, registry, -520, 170, "RESET", 1) : null;
        ComponentInstance rdDataA = output(circuit, registry, 560, -100, "RD_DATA_A", 8);
        ComponentInstance rdDataB = output(circuit, registry, 560, 20, "RD_DATA_B", 8);

        ComponentInstance decoder = subcircuit(circuit, DECODER_3_TO_8, -400, -20,
                "WRITE_DECODER");
        ComponentInstance decoded = component(circuit, registry, "routing.splitter", -320, -20,
                registry.require("routing.splitter").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8), "WRITE_SELECT_BITS");
        ComponentInstance readBitsA = component(circuit, registry, "routing.splitter", -400, -180,
                registry.require("routing.splitter").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 3), "READ_A_BITS");
        ComponentInstance readBitsB = component(circuit, registry, "routing.splitter", -400, -120,
                registry.require("routing.splitter").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 3), "READ_B_BITS");
        ComponentInstance resultA = component(circuit, registry, "routing.joiner", 440, -100,
                registry.require("routing.joiner").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8), "READ_A_RESULT");
        ComponentInstance resultB = component(circuit, registry, "routing.joiner", 440, 20,
                registry.require("routing.joiner").definition().defaultParameters()
                        .with(LibraryParameters.WIDTH, 8), "READ_B_RESULT");
        wire(circuit, wrAddr, "OUT", decoder, "A");
        wire(circuit, decoder, "Y", decoded, "BUS");
        wire(circuit, rdAddrA, "OUT", readBitsA, "BUS");
        wire(circuit, rdAddrB, "OUT", readBitsB, "BUS");
        wire(circuit, resultA, "BUS", rdDataA, "IN");
        wire(circuit, resultB, "BUS", rdDataB, "IN");

        ComponentInstance[] registerBits = new ComponentInstance[8];
        for (int register = 0; register < 8; register++) {
            ComponentInstance load = component(circuit, registry, "logic.and", -240,
                    -160 + register * 45.0, "WRITE_ENABLE_" + register);
            ComponentInstance storage = subcircuit(circuit, resetSupport
                            ? structuralRegisterName(8, true, 0) : REGISTER8, -100,
                    -160 + register * 45.0, "REGISTER_" + register);
            registerBits[register] = component(circuit, registry, "routing.splitter", 20,
                    -160 + register * 45.0,
                    registry.require("routing.splitter").definition().defaultParameters()
                            .with(LibraryParameters.WIDTH, 8), "REGISTER_BITS_" + register);
            wire(circuit, decoded, "BIT" + register, load, "IN0");
            wire(circuit, wrEn, "OUT", load, "IN1");
            wire(circuit, wrData, "OUT", storage, "DATA");
            wire(circuit, load, "OUT", storage, "LOAD");
            wire(circuit, clk, "OUT", storage, "CLK");
            if (resetSupport) {
                wire(circuit, reset, "OUT", storage, "RESET");
            }
            wire(circuit, storage, "Q", registerBits[register], "BUS");
        }

        for (int bit = 0; bit < 8; bit++) {
            Signal[] choices = new Signal[8];
            for (int register = 0; register < 8; register++) {
                choices[register] = new Signal(registerBits[register], "BIT" + bit);
            }
            Signal selectedA = selectEight(circuit, 20 + bit,
                    choices[0], choices[1], choices[2], choices[3],
                    choices[4], choices[5], choices[6], choices[7],
                    readBitsA, 120 + bit * 35.0, 300);
            Signal selectedB = selectEight(circuit, 40 + bit,
                    choices[0], choices[1], choices[2], choices[3],
                    choices[4], choices[5], choices[6], choices[7],
                    readBitsB, 120 + bit * 35.0, 500);
            wire(circuit, selectedA.component(), selectedA.port(), resultA, "BIT" + bit);
            wire(circuit, selectedB.component(), selectedB.port(), resultB, "BIT" + bit);
        }
        return circuit;
    }

    private static Signal selectEight(CircuitDocument circuit, int index,
                                      Signal in0, Signal in1, Signal in2, Signal in3,
                                      Signal in4, Signal in5, Signal in6, Signal in7,
                                      ComponentInstance opBits, double x, double y) {
        Signal p0 = muxSignal(circuit, in0, in1, opBits, "BIT0", x, y,
                "RESULT_MUX_" + index + "_0");
        Signal p1 = muxSignal(circuit, in2, in3, opBits, "BIT0", x, y + 35,
                "RESULT_MUX_" + index + "_1");
        Signal p2 = muxSignal(circuit, in4, in5, opBits, "BIT0", x, y + 70,
                "RESULT_MUX_" + index + "_2");
        Signal p3 = muxSignal(circuit, in6, in7, opBits, "BIT0", x, y + 105,
                "RESULT_MUX_" + index + "_3");
        Signal q0 = muxSignal(circuit, p0, p1, opBits, "BIT1", x + 45, y + 20,
                "RESULT_MUX_" + index + "_4");
        Signal q1 = muxSignal(circuit, p2, p3, opBits, "BIT1", x + 45, y + 85,
                "RESULT_MUX_" + index + "_5");
        return muxSignal(circuit, q0, q1, opBits, "BIT2", x + 90, y + 50,
                "RESULT_MUX_" + index + "_6");
    }

    private static Signal muxSignal(CircuitDocument circuit, Signal a, Signal b,
                                    ComponentInstance select, String selectPort,
                                    double x, double y, String label) {
        ComponentInstance mux = subcircuit(circuit, MUX2, x, y, label);
        wire(circuit, a.component(), a.port(), mux, "A");
        wire(circuit, b.component(), b.port(), mux, "B");
        wire(circuit, select, selectPort, mux, "S");
        return new Signal(mux, "Y");
    }

    private static void requireRippleWidth(int width) {
        if (width != 3 && width != 4 && width != 8 && width != 16) {
            throw new IllegalArgumentException("Canonical ripple-adder width must be 3, 4, 8 or 16");
        }
    }

    private static void requireRegisterWidth(int width) {
        if (width != 1 && width != 3 && width != 4 && width != 8 && width != 16) {
            throw new IllegalArgumentException("Structural register width must be 1, 3, 4, 8 or 16");
        }
    }

    private static void mergeChildren(CircuitProject target, CircuitProject source) {
        source.circuits().stream()
                .filter(circuit -> !CircuitProject.MAIN_CIRCUIT.equals(circuit.metadata().name()))
                .forEach(target::putCircuit);
    }

    private static CircuitDocument document(String name, String description) {
        return new CircuitDocument(new CircuitMetadata(name, description));
    }

    private static ComponentInstance input(CircuitDocument circuit, ComponentRegistry registry,
                                           double x, double y, String name, int width) {
        return interfaceComponent(circuit, registry, SubcircuitSupport.INPUT_DEFINITION_ID,
                x, y, name, width);
    }

    private static ComponentInstance output(CircuitDocument circuit, ComponentRegistry registry,
                                            double x, double y, String name, int width) {
        return interfaceComponent(circuit, registry, SubcircuitSupport.OUTPUT_DEFINITION_ID,
                x, y, name, width);
    }

    private static ComponentInstance interfaceComponent(CircuitDocument circuit,
                                                        ComponentRegistry registry,
                                                        String definitionId, double x, double y,
                                                        String name, int width) {
        ParameterValues parameters = ParameterValues.defaultsOf(List.of(
                        SubcircuitSupport.INTERFACE_NAME, SubcircuitSupport.INTERFACE_WIDTH))
                .with(SubcircuitSupport.INTERFACE_NAME, name)
                .with(SubcircuitSupport.INTERFACE_WIDTH, width);
        return component(circuit, registry, definitionId, x, y, parameters, name + "_PORT");
    }

    private static ComponentInstance component(CircuitDocument circuit, ComponentRegistry registry,
                                               String definitionId, double x, double y, String label) {
        return component(circuit, registry, definitionId, x, y,
                registry.require(definitionId).definition().defaultParameters(), label);
    }

    private static ComponentInstance component(CircuitDocument circuit, ComponentRegistry registry,
                                               String definitionId, double x, double y,
                                               ParameterValues parameters, String label) {
        ComponentInstance instance = ComponentInstance.create(
                definitionId, new CircuitPoint(x, y), parameters).withLabel(label);
        circuit.addComponent(instance);
        return instance;
    }

    private static ComponentInstance subcircuit(CircuitDocument circuit, String child,
                                                double x, double y, String label) {
        ComponentInstance instance = SubcircuitSupport.instantiate(child, new CircuitPoint(x, y))
                .withLabel(label);
        circuit.addComponent(instance);
        return instance;
    }

    private static void wire(CircuitDocument circuit, ComponentInstance from, String fromPort,
                             ComponentInstance to, String toPort) {
        circuit.addConnection(Connection.create(new PortReference(from.id(), fromPort),
                new PortReference(to.id(), toPort)));
    }

    private static void wireBitToPort(CircuitDocument circuit, ComponentInstance from,
                                      String fromPort, int bit, ComponentInstance to,
                                      String toPort) {
        circuit.addConnection(Connection.create(
                PortEndpoint.bit(new PortReference(from.id(), fromPort), bit),
                PortEndpoint.whole(new PortReference(to.id(), toPort))));
    }

    private static void wirePortToBit(CircuitDocument circuit, ComponentInstance from,
                                      String fromPort, ComponentInstance to, String toPort,
                                      int bit) {
        circuit.addConnection(Connection.create(
                PortEndpoint.whole(new PortReference(from.id(), fromPort)),
                PortEndpoint.bit(new PortReference(to.id(), toPort), bit)));
    }

    private record Signal(ComponentInstance component, String port) {
    }
}
