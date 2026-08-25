package dev.logicforge.library;

import dev.logicforge.circuit.component.ParameterSpec;
import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.component.PortDirection;
import dev.logicforge.circuit.component.PortSpec;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.CircuitSize;
import dev.logicforge.circuit.geometry.PortSide;
import dev.logicforge.logic.BitWidth;
import java.util.ArrayList;
import java.util.List;

/**
 * The handful of port arrangements the standard library needs. Sharing them keeps every
 * gate on the same grid and the same visual rhythm.
 */
public final class PortLayouts {

    /** Body size of the small square components: sources and outputs. */
    public static final CircuitSize SMALL_BODY = new CircuitSize(32, 32);

    /** Body size of the single-input driver symbols. */
    public static final CircuitSize UNARY_BODY = new CircuitSize(40, 40);

    /** Width of the multi-input gate symbols. */
    public static final double GATE_WIDTH = 56;

    private PortLayouts() {
    }

    /** A component with one output on the right: constants, switches, buttons. */
    public static PortLayout source() {
        return fixed(SMALL_BODY, List.of(outputPort("OUT", SMALL_BODY)));
    }

    /** A component with one input on the left: LEDs, probes, output pins. */
    public static PortLayout sink() {
        return fixed(SMALL_BODY, List.of(inputPort("IN", SMALL_BODY, 0)));
    }

    /** One input on the left, one output on the right: buffer and inverter. */
    public static PortLayout unary() {
        return fixed(UNARY_BODY, List.of(
                inputPort("A", UNARY_BODY, 0),
                outputPort("Y", UNARY_BODY)));
    }

    /** Data in on the left, enable from below, output on the right. */
    public static PortLayout triState() {
        return fixed(UNARY_BODY, List.of(
                inputPort("A", UNARY_BODY, 0),
                new PortSpec("ENABLE", PortDirection.INPUT, dev.logicforge.logic.BitWidth.ONE,
                        new CircuitPoint(0, UNARY_BODY.halfHeight() + PortLayout.PORT_STUB),
                        PortSide.BOTTOM, "Drives the output when active, otherwise the output floats"),
                outputPort("Y", UNARY_BODY)));
    }

    /**
     * {@code IN0..INn-1} evenly spaced on the left, {@code OUT} centred on the right. The
     * body grows with the number of inputs; port names stay the same, so widening a gate
     * keeps the wires that are already attached.
     */
    public static PortLayout gate(ParameterSpec.IntegerParameter inputCount) {
        return new PortLayout() {

            @Override
            public List<PortSpec> ports(ParameterValues values) {
                int inputs = values.getInt(inputCount);
                CircuitSize body = bodySize(values);
                List<PortSpec> ports = new ArrayList<>(inputs + 1);
                for (int i = 0; i < inputs; i++) {
                    double y = (i - (inputs - 1) / 2.0) * PORT_SPACING;
                    ports.add(new PortSpec("IN" + i, PortDirection.INPUT,
                            dev.logicforge.logic.BitWidth.ONE,
                            new CircuitPoint(-body.halfWidth() - PORT_STUB, y), PortSide.LEFT, ""));
                }
                ports.add(outputPort("OUT", body));
                return ports;
            }

            @Override
            public CircuitSize bodySize(ParameterValues values) {
                int inputs = values.getInt(inputCount);
                return new CircuitSize(GATE_WIDTH, Math.max(48, inputs * PORT_SPACING + PORT_SPACING));
            }
        };
    }

    /** One named, possibly multi-bit, port for {@link #box}. */
    public record PortDef(String name, BitWidth width, String description) {

        public PortDef(String name) {
            this(name, BitWidth.ONE, "");
        }

        public PortDef(String name, BitWidth width) {
            this(name, width, "");
        }
    }

    /**
     * A fixed-width box with a given set of inputs evenly spaced on the left and outputs
     * evenly spaced on the right — the layout latches, flip-flops, registers and the other
     * boxy ICs share. Port widths may differ from 1 bit, so a bus (DATA, COUNT, ...) is
     * declared the same way as a single-bit control line (CLK, EN, ...).
     */
    public static PortLayout box(List<PortDef> inputs, List<PortDef> outputs, double width) {
        return new PortLayout() {

            @Override
            public List<PortSpec> ports(ParameterValues values) {
                CircuitSize body = bodySize(values);
                List<PortSpec> ports = new ArrayList<>(inputs.size() + outputs.size());
                for (int i = 0; i < inputs.size(); i++) {
                    double y = (i - (inputs.size() - 1) / 2.0) * PORT_SPACING;
                    PortDef def = inputs.get(i);
                    ports.add(new PortSpec(def.name(), PortDirection.INPUT, def.width(),
                            new CircuitPoint(-body.halfWidth() - PORT_STUB, y), PortSide.LEFT,
                            def.description()));
                }
                for (int i = 0; i < outputs.size(); i++) {
                    double y = (i - (outputs.size() - 1) / 2.0) * PORT_SPACING;
                    PortDef def = outputs.get(i);
                    ports.add(new PortSpec(def.name(), PortDirection.OUTPUT, def.width(),
                            new CircuitPoint(body.halfWidth() + PORT_STUB, y), PortSide.RIGHT,
                            def.description()));
                }
                return ports;
            }

            @Override
            public CircuitSize bodySize(ParameterValues values) {
                int rows = Math.max(inputs.size(), outputs.size());
                return new CircuitSize(width, Math.max(48, rows * PORT_SPACING + PORT_SPACING));
            }
        };
    }

    /** {@link #box} for the common case where every port is a single bit. */
    public static PortLayout box(List<String> inputNames, List<String> outputNames) {
        return box(
                inputNames.stream().map(PortDef::new).toList(),
                outputNames.stream().map(PortDef::new).toList(),
                GATE_WIDTH);
    }

    private static PortSpec inputPort(String name, CircuitSize body, double y) {
        return PortSpec.of(name, PortDirection.INPUT,
                new CircuitPoint(-body.halfWidth() - PortLayout.PORT_STUB, y), PortSide.LEFT);
    }

    private static PortSpec outputPort(String name, CircuitSize body) {
        return PortSpec.of(name, PortDirection.OUTPUT,
                new CircuitPoint(body.halfWidth() + PortLayout.PORT_STUB, 0), PortSide.RIGHT);
    }

    private static PortLayout fixed(CircuitSize body, List<PortSpec> ports) {
        return new PortLayout() {

            @Override
            public List<PortSpec> ports(ParameterValues values) {
                return ports;
            }

            @Override
            public CircuitSize bodySize(ParameterValues values) {
                return body;
            }
        };
    }
}
