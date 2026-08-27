package dev.logicforge.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StandardDocumentationTest {
    @Test
    void foundationalComponentsExposeStructuredTruthTables() {
        ComponentRegistry registry = ComponentRegistry.standard();
        for (String id : new String[]{"logic.not", "logic.and", "logic.nand", "logic.or",
                "logic.nor", "logic.xor", "logic.xnor", "arithmetic.half_adder",
                "arithmetic.full_adder", "routing.mux2", "routing.decoder",
                "sequential.sr_latch", "sequential.d_ff"}) {
            var documentation = registry.require(id).definition().documentation();
            assertFalse(documentation.isEmpty(), id);
            assertTrue(documentation.truthTable().isPresent(), id);
        }
        assertEquals(8, registry.require("arithmetic.full_adder").definition().documentation()
                .truthTable().orElseThrow().rows().size());
    }
}
