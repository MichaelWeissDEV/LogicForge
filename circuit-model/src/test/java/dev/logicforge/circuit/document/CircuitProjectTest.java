package dev.logicforge.circuit.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.geometry.CircuitPoint;
import org.junit.jupiter.api.Test;

class CircuitProjectTest {

    @Test
    void renameUpdatesEverySubcircuitInstanceAtomically() {
        CircuitProject project = CircuitProject.empty("rename");
        project.addCircuit("ALU8");
        CircuitDocument wrapper = project.addCircuit("CPU");
        ComponentInstance inMain = SubcircuitSupport.instantiate("ALU8", new CircuitPoint(0, 0));
        ComponentInstance inWrapper = SubcircuitSupport.instantiate("ALU8", new CircuitPoint(80, 0));
        project.mainCircuit().addComponent(inMain);
        wrapper.addComponent(inWrapper);

        project.renameCircuit("ALU8", "ALU_CORE");

        assertEquals(java.util.List.of("main", "ALU_CORE", "CPU"), project.circuitNames());
        assertEquals("subcircuit:ALU_CORE",
                project.mainCircuit().requireComponent(inMain.id()).definitionId());
        assertEquals("subcircuit:ALU_CORE", wrapper.requireComponent(inWrapper.id()).definitionId());
    }

    @Test
    void mainCannotBeRenamedOrDeleted() {
        CircuitProject project = CircuitProject.empty("safe");
        assertThrows(IllegalArgumentException.class, () -> project.renameCircuit("main", "root"));
        assertThrows(IllegalArgumentException.class, () -> project.removeCircuit("main"));
    }
}
