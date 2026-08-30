package dev.logicforge.structures;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.library.LibraryParameters;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructuralImplementationRegistryTest {

    private final StructuralImplementationRegistry registry =
            StructuralImplementationRegistry.standard();

    @Test
    void matchesAluByItsActualDefinitionAndWidth() {
        assertAvailable("arithmetic.alu", values().with(LibraryParameters.WIDTH, 8));
        assertUnavailable("arithmetic.alu", values().with(LibraryParameters.WIDTH, 16));
        assertTrue(registry.allFor("arithmetic.alu8").isEmpty());
    }

    @Test
    void matchesRegisterFileOnlyAtEightByEight() {
        assertAvailable("memory.register_file", values()
                .with(LibraryParameters.WIDTH, 8)
                .with(LibraryParameters.REGISTER_COUNT, 8));
        assertUnavailable("memory.register_file", values()
                .with(LibraryParameters.WIDTH, 16)
                .with(LibraryParameters.REGISTER_COUNT, 8));
    }

    @Test
    void matchesOnlyRisingEdgeDff() {
        assertAvailable("sequential.d_ff", values()
                .with(LibraryParameters.CLOCK_EDGE, "rising"));
        assertUnavailable("sequential.d_ff", values()
                .with(LibraryParameters.CLOCK_EDGE, "falling"));
    }

    @Test
    void muxAndRegisterConstraintsDoNotOverclaim() {
        // Widths 1, 3, 4, 8 and 16 are registered gate-level mux implementations (see
        // StructuralMuxTest for the equivalence proof behind each); anything else is not.
        assertAvailable("routing.mux2", values().with(LibraryParameters.WIDTH, 1));
        assertAvailable("routing.mux2", values().with(LibraryParameters.WIDTH, 8));
        assertUnavailable("routing.mux2", values().with(LibraryParameters.WIDTH, 5));
        assertAvailable("sequential.register", values()
                .with(LibraryParameters.WIDTH, 8)
                .with(LibraryParameters.CLOCK_EDGE, "rising"));
        assertUnavailable("sequential.register", values()
                .with(LibraryParameters.WIDTH, 8)
                .with(LibraryParameters.CLOCK_EDGE, "falling"));
    }

    @Test
    void rejectsSyntheticOrUnknownTargets() {
        assertThrows(IllegalArgumentException.class, () -> registry.register(
                new StructuralImplementationDescriptor("arithmetic.alu8",
                        ImplementationLevel.GATE, ParameterMatcher.any(),
                        StructuralCircuitFactory::alu8Project, "bad", "", List.of())));
    }

    private void assertAvailable(String id, ParameterValues values) {
        assertTrue(registry.find(id, values, ImplementationLevel.GATE).isPresent());
    }

    private void assertUnavailable(String id, ParameterValues values) {
        assertFalse(registry.find(id, values, ImplementationLevel.GATE).isPresent());
    }

    private static ParameterValues values() {
        return ParameterValues.empty();
    }
}
