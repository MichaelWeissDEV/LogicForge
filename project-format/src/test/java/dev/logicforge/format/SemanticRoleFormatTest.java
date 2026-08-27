package dev.logicforge.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitMetadata;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.geometry.CircuitPoint;
import org.junit.jupiter.api.Test;

class SemanticRoleFormatTest {

    @Test
    void formatV4PersistsSemanticRoleIndependentlyOfLabel() {
        CircuitDocument main = new CircuitDocument(new CircuitMetadata("main", ""));
        main.addComponent(ComponentInstance.create("source.one", new CircuitPoint(10, 20),
                        ParameterValues.empty())
                .withLabel("editable display text").withSemanticRole("test.stable-role"));
        String json = ProjectFormat.toJson(CircuitProject.of("roles", main));
        CircuitProject loaded = ProjectFormat.fromJson(json, "roles");

        assertTrue(json.contains("\"role\": \"test.stable-role\""));
        ComponentInstance restored = loaded.mainCircuit().components().iterator().next();
        assertEquals("test.stable-role", restored.semanticRole());
        assertEquals("editable display text", restored.label());
    }
}
