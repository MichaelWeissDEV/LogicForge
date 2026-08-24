package dev.logicforge.circuit.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.Rotation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CircuitDocumentTest {

    private final CircuitDocument document = new CircuitDocument();

    private ComponentInstance place(double x, double y) {
        ComponentInstance instance = ComponentInstance.create("test.gate", new CircuitPoint(x, y),
                ParameterValues.empty());
        document.addComponent(instance);
        return instance;
    }

    @Test
    void componentsKeepTheirIdentityWhenEdited() {
        ComponentInstance gate = place(100, 100);
        ComponentInstance moved = gate.movedBy(16, 0).withRotation(Rotation.DEG_90);
        document.replaceComponent(moved);

        assertEquals(gate.id(), moved.id());
        assertEquals(new CircuitPoint(116, 100), document.requireComponent(gate.id()).position());
        assertEquals(Rotation.DEG_90, document.requireComponent(gate.id()).rotation());
        assertEquals(1, document.componentCount());
    }

    @Test
    void removingAComponentAlsoRemovesItsWires() {
        ComponentInstance source = place(0, 0);
        ComponentInstance sink = place(100, 0);
        ComponentInstance untouched = place(200, 200);
        document.addConnection(Connection.create(
                new PortReference(source.id(), "OUT"), new PortReference(sink.id(), "IN0")));
        document.addConnection(Connection.create(
                new PortReference(sink.id(), "OUT"), new PortReference(untouched.id(), "IN0")));

        List<Connection> detached = document.removeComponent(sink.id());

        assertEquals(2, detached.size(), "both wires touched the removed component");
        assertEquals(0, document.connectionCount(), "no wire may dangle");
        assertEquals(2, document.componentCount());
    }

    @Test
    void wiresToUnknownComponentsAreRejected() {
        ComponentInstance gate = place(0, 0);
        assertThrows(IllegalStateException.class, () -> document.addConnection(Connection.create(
                new PortReference(gate.id(), "OUT"), new PortReference(UUID.randomUUID(), "IN0"))));
    }

    @Test
    void aWireCannotConnectAPortToItself() {
        ComponentInstance gate = place(0, 0);
        PortReference port = new PortReference(gate.id(), "OUT");
        assertThrows(IllegalArgumentException.class, () -> Connection.create(port, port));
    }

    @Test
    void wiresCanBeLookedUpByPortAndComponent() {
        ComponentInstance source = place(0, 0);
        ComponentInstance sink = place(100, 0);
        PortReference out = new PortReference(source.id(), "OUT");
        PortReference in = new PortReference(sink.id(), "IN0");
        document.addConnection(Connection.create(out, in));

        assertEquals(1, document.connectionsAt(out).size());
        assertEquals(1, document.connectionsOf(sink.id()).size());
        assertTrue(document.isConnected(in, out), "connections are undirected for lookup");
        assertTrue(document.connectionsAt(new PortReference(sink.id(), "IN1")).isEmpty());
    }

    @Test
    void onlyParameterChangesCountAsTopologyChanges() {
        List<CircuitChange> changes = new ArrayList<>();
        document.addListener((doc, change) -> changes.add(change));

        ComponentInstance gate = place(0, 0);
        document.replaceComponent(gate.movedBy(8, 8));
        document.replaceComponent(gate.withParameters(
                ParameterValues.empty().with(TestDefinitions.INPUTS, 3)));

        assertEquals(List.of(CircuitChange.Kind.COMPONENT_ADDED, CircuitChange.Kind.COMPONENT_MOVED,
                        CircuitChange.Kind.COMPONENT_RECONFIGURED),
                changes.stream().map(CircuitChange::kind).toList());
        assertTrue(changes.get(0).affectsTopology());
        assertFalse(changes.get(1).affectsTopology(), "moving must not force a recompile");
        assertTrue(changes.get(2).affectsTopology());
    }

    @Test
    void rewiringMustNotBeDisguisedAsRerouting() {
        ComponentInstance source = place(0, 0);
        ComponentInstance sink = place(100, 0);
        Connection wire = Connection.create(
                new PortReference(source.id(), "OUT"), new PortReference(sink.id(), "IN0"));
        document.addConnection(wire);

        document.replaceConnection(wire.withWaypoints(List.of(new CircuitPoint(50, 30))));
        assertEquals(1, document.connection(wire.id()).orElseThrow().waypoints().size());

        Connection rewired = new Connection(wire.id(), wire.from(),
                new PortReference(sink.id(), "IN1"), List.of());
        assertThrows(IllegalArgumentException.class, () -> document.replaceConnection(rewired));
    }

    @Test
    void copiesAreIndependentButStructurallyEqual() {
        ComponentInstance gate = place(0, 0);
        CircuitDocument copy = document.copy();
        assertTrue(document.structurallyEquals(copy));

        copy.removeComponent(gate.id());
        assertFalse(document.structurallyEquals(copy));
        assertEquals(1, document.componentCount());
    }
}
