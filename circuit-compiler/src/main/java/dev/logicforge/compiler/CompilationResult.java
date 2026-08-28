package dev.logicforge.compiler;

import dev.logicforge.simulation.CompiledCircuit;
import java.util.List;
import java.util.OptionalInt;

/** A compiled circuit together with its source map and everything the compiler noticed. */
public record CompilationResult(
        CompiledCircuit circuit,
        CircuitSourceMap sourceMap,
        List<ValidationIssue> issues,
        HierarchySourceMap hierarchySourceMap,
        ChipSourceMap chipSourceMap) {

    public CompilationResult {
        issues = List.copyOf(issues);
        hierarchySourceMap = hierarchySourceMap == null ? HierarchySourceMap.EMPTY : hierarchySourceMap;
        chipSourceMap = chipSourceMap == null ? new ChipSourceMap(java.util.Map.of()) : chipSourceMap;
    }

    public CompilationResult(CompiledCircuit circuit, CircuitSourceMap sourceMap,
                             List<ValidationIssue> issues) {
        this(circuit, sourceMap, issues, HierarchySourceMap.EMPTY, new ChipSourceMap(java.util.Map.of()));
    }
    
    public CompilationResult(CompiledCircuit circuit, CircuitSourceMap sourceMap,
                             List<ValidationIssue> issues, HierarchySourceMap hierarchySourceMap) {
        this(circuit, sourceMap, issues, hierarchySourceMap, new ChipSourceMap(java.util.Map.of()));
    }

    /**
     * The runtime id of the component carrying this label. Labels are how a headless test
     * or a script addresses a circuit: {@code setInput(result.componentByLabel("A"), ONE)}.
     */
    public OptionalInt componentByLabel(String label) {
        for (int id = 0; id < circuit.componentCount(); id++) {
            if (circuit.component(id).label().equals(label)) {
                return OptionalInt.of(id);
            }
        }
        return OptionalInt.empty();
    }

    public List<ValidationIssue> warnings() {
        return issues.stream()
                .filter(issue -> issue.severity() == ValidationIssue.Severity.WARNING)
                .toList();
    }
}
