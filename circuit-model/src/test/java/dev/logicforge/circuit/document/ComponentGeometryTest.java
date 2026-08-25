package dev.logicforge.circuit.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.component.ComponentCategory;
import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.component.PortDirection;
import dev.logicforge.circuit.component.PortSpec;
import dev.logicforge.circuit.geometry.CircuitBounds;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.CircuitSize;
import dev.logicforge.circuit.geometry.PortSide;
import dev.logicforge.circuit.geometry.Rotation;
import dev.logicforge.logic.BitWidth;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComponentGeometryTest {

    private static final ComponentDefinition REGISTER = new ComponentDefinition() {
        @Override public String id() { return "test.register"; }
        @Override public String displayName() { return "Register"; }
        @Override public ComponentCategory category() { return ComponentCategory.SEQUENTIAL; }
        @Override public String description() { return "test register"; }
        @Override public List<dev.logicforge.circuit.component.ParameterSpec<?>> parameters() {
            return List.of();
        }
        @Override public List<PortSpec> ports(ParameterValues values) {
            return List.of(
                    new PortSpec("DATA", PortDirection.INPUT, BitWidth.of(8),
                            new CircuitPoint(-40, -16), PortSide.LEFT, "Data input"),
                    new PortSpec("CLK", PortDirection.INPUT, BitWidth.ONE,
                            new CircuitPoint(-40, 0), PortSide.LEFT, "Clock"),
                    new PortSpec("LOAD", PortDirection.INPUT, BitWidth.ONE,
                            new CircuitPoint(-40, 16), PortSide.LEFT, "Load"),
                    new PortSpec("Q", PortDirection.OUTPUT, BitWidth.of(8),
                            new CircuitPoint(40, 0), PortSide.RIGHT, "Data output"));
        }
        @Override public CircuitSize bodySize(ParameterValues values) {
            return new CircuitSize(64, 64);
        }
    };

    private final ComponentInstance gate = ComponentInstance.create(
            "test.gate", new CircuitPoint(100, 100), ParameterValues.empty());

    @Test
    void unrotatedPortsSitLeftAndRightOfTheBody() {
        List<PlacedPort> ports = ComponentGeometry.ports(gate, TestDefinitions.GATE);
        assertEquals(3, ports.size());
        assertEquals(new CircuitPoint(76, 92), ports.get(0).position());
        assertEquals(new CircuitPoint(76, 108), ports.get(1).position());
        assertEquals(new CircuitPoint(124, 100), ports.get(2).position());
        assertEquals(PortSide.LEFT, ports.get(0).side());
        assertEquals(PortSide.RIGHT, ports.get(2).side());
    }

    @Test
    void rotationMovesPortsAndTheirSidesTogether() {
        ComponentInstance rotated = gate.withRotation(Rotation.DEG_90);
        List<PlacedPort> ports = ComponentGeometry.ports(rotated, TestDefinitions.GATE);

        assertEquals(new CircuitPoint(108, 76), ports.get(0).position());
        assertEquals(new CircuitPoint(92, 76), ports.get(1).position());
        assertEquals(new CircuitPoint(100, 124), ports.get(2).position());
        assertEquals(PortSide.TOP, ports.get(0).side(), "inputs face upwards after a quarter turn");
        assertEquals(PortSide.BOTTOM, ports.get(2).side());
    }

    @Test
    void bodyBoundsFollowRotation() {
        assertEquals(new CircuitBounds(76, 76, 48, 48),
                ComponentGeometry.bodyBounds(gate, TestDefinitions.GATE));

        ComponentInstance wide = gate.withParameters(
                ParameterValues.empty().with(TestDefinitions.INPUTS, 8));
        assertEquals(new CircuitBounds(76, 28, 48, 144),
                ComponentGeometry.bodyBounds(wide, TestDefinitions.GATE));
        assertEquals(new CircuitBounds(28, 76, 144, 48),
                ComponentGeometry.bodyBounds(wide.withRotation(Rotation.DEG_270), TestDefinitions.GATE));
    }

    @Test
    void moreInputsMeansMorePorts() {
        ComponentInstance wide = gate.withParameters(
                ParameterValues.empty().with(TestDefinitions.INPUTS, 4));
        List<PlacedPort> ports = ComponentGeometry.ports(wide, TestDefinitions.GATE);
        assertEquals(5, ports.size());
        assertEquals("IN3", ports.get(3).spec().name());
        assertEquals("OUT", ports.get(4).spec().name());
    }

    @Test
    void portStubsPointAwayFromTheBody() {
        PlacedPort output = ComponentGeometry.port(gate, TestDefinitions.GATE, "OUT").orElseThrow();
        assertEquals(new CircuitPoint(140, 100), output.stubEnd(16));

        PlacedPort input = ComponentGeometry.port(gate, TestDefinitions.GATE, "IN0").orElseThrow();
        assertEquals(new CircuitPoint(60, 92), input.stubEnd(16));
    }

    @Test
    void expandedBusPinsGrowBodyAndShareItsEffectiveGeometry() {
        ComponentInstance expanded = ComponentInstance.create(
                REGISTER.id(), new CircuitPoint(100, 100), ParameterValues.empty())
                .withPortDisplayMode(PortDisplayMode.EXPANDED);

        assertEquals(new CircuitSize(64, 176),
                ComponentGeometry.effectiveBodySize(expanded, REGISTER));
        assertEquals(new CircuitBounds(68, 12, 64, 176),
                ComponentGeometry.bodyBounds(expanded, REGISTER));
        assertEquals(18, ComponentGeometry.ports(expanded, REGISTER).size());
        assertTrue(ComponentGeometry.outerBounds(expanded, REGISTER)
                .contains(ComponentGeometry.bodyBounds(expanded, REGISTER)));
    }

    @Test
    void expandedGeometryRotatesBodyAndPinsTogether() {
        ComponentInstance expanded = ComponentInstance.create(
                REGISTER.id(), new CircuitPoint(100, 100), ParameterValues.empty())
                .withPortDisplayMode(PortDisplayMode.EXPANDED)
                .withRotation(Rotation.DEG_90);

        assertEquals(new CircuitBounds(12, 68, 176, 64),
                ComponentGeometry.bodyBounds(expanded, REGISTER));
        List<PlacedPort> pins = ComponentGeometry.ports(expanded, REGISTER);
        assertEquals(PortSide.TOP, pins.get(0).side());
        assertTrue(pins.stream().allMatch(pin ->
                ComponentGeometry.outerBounds(expanded, REGISTER).contains(pin.position())));
    }
}
