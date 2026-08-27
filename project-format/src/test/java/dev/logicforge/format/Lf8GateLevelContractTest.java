package dev.logicforge.format;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.compiler.CircuitCompiler;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.processor.lf8.Lf8ComputerFactory;
import dev.logicforge.processor.lf8.Lf8ImplementationMode;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Enforces the documented gate-level CPU leaf contract on the flattened runtime. */
class Lf8GateLevelContractTest {
    private static final Set<String> ALLOWED_LEAVES = Set.of(
            "logic.not", "logic.and", "logic.nand", "logic.or", "logic.nor",
            "logic.xor", "logic.xnor", "logic.tristate", "routing.tristate_n",
            "source.zero", "source.one", "routing.bus_constant",
            "routing.splitter", "routing.joiner", "routing.bus_concat", "routing.bus_slice",
            "memory.rom", "output.probe",
            "hierarchy.input", "hierarchy.output", "hierarchy.inout");

    @Test
    void flattenedCpuContainsOnlyGateWiringAndBehavioralMicrocodeMemoryLeaves() {
        CircuitProject project = Lf8ComputerFactory.create(Lf8ImplementationMode.GATE_LEVEL,
                0xff);
        var compilation = new CircuitCompiler(ComponentRegistry.standard())
                .compile(project, CircuitProject.MAIN_CIRCUIT);
        var cpu = project.mainCircuit().components().stream()
                .filter(component -> "CPU".equals(component.label())).findFirst().orElseThrow();
        String cpuPrefix = "main/" + cpu.id() + "/";
        List<String> offenders = compilation.hierarchySourceMap().componentIdByPath().entrySet()
                .stream().filter(entry -> entry.getKey().startsWith(cpuPrefix))
                .map(entry -> new Leaf(entry.getKey(), compilation.circuit()
                        .component(entry.getValue()).definitionId()))
                .filter(leaf -> !ALLOWED_LEAVES.contains(leaf.definitionId()))
                .sorted(Comparator.comparing(Leaf::path))
                .map(leaf -> leaf.path() + " -> " + leaf.definitionId()).toList();
        assertTrue(offenders.isEmpty(), () -> "Forbidden GATE_LEVEL CPU leaves:\n"
                + String.join("\n", offenders));
    }

    private record Leaf(String path, String definitionId) {
    }
}
