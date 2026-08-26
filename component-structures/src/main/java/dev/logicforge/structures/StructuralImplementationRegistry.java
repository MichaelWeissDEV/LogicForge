package dev.logicforge.structures;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Headless registry of canonical structural/reference implementations. */
public final class StructuralImplementationRegistry {

    private final Map<String, List<StructuralImplementation>> implementations =
            new LinkedHashMap<>();

    public static StructuralImplementationRegistry standard() {
        StructuralImplementationRegistry registry = new StructuralImplementationRegistry();
        registry.register(new StructuralImplementation("arithmetic.half_adder",
                "Gate-level Half Adder", "XOR sum and AND carry",
                ImplementationLevel.GATE, StructuralCircuitFactory::halfAdderProject,
                List.of("adder", "combinational", "gates")));
        registry.register(new StructuralImplementation("arithmetic.full_adder",
                "Hierarchical Full Adder", "Two half adders and an OR gate",
                ImplementationLevel.GATE, StructuralCircuitFactory::fullAdderProject,
                List.of("adder", "hierarchy", "gates")));
        registry.register(new StructuralImplementation("arithmetic.adder8",
                "Ripple Adder 8", "Eight explicit full-adder stages",
                ImplementationLevel.GATE, () -> StructuralCircuitFactory.rippleAdderProject(8),
                List.of("adder", "ripple", "8-bit", "hierarchy")));
        registry.register(new StructuralImplementation("routing.mux2",
                "Gate-level Mux2", "NOT/AND/OR implementation",
                ImplementationLevel.GATE, StructuralCircuitFactory::mux2Project,
                List.of("mux", "routing", "gates")));
        registry.register(new StructuralImplementation("routing.decoder2to4",
                "Gate-level Decoder 2-to-4", "Inverters and product terms",
                ImplementationLevel.GATE, StructuralCircuitFactory::decoder2To4Project,
                List.of("decoder", "routing", "gates")));
        registry.register(new StructuralImplementation("sequential.d_ff",
                "Master-Slave DFF", "DFF -> D latches -> SR latches -> gates",
                ImplementationLevel.GATE, StructuralCircuitFactory::dFlipFlopProject,
                List.of("flip-flop", "latch", "state", "hierarchy")));
        registry.register(new StructuralImplementation("sequential.register8",
                "Structural Register8", "Eight DFFs and eight load muxes",
                ImplementationLevel.GATE, StructuralCircuitFactory::register8Project,
                List.of("register", "8-bit", "state", "hierarchy")));
        registry.register(new StructuralImplementation("arithmetic.alu8",
                "Structural ALU8", "Ripple adder, gate logic, shifts, mux tree and flags",
                ImplementationLevel.GATE, StructuralCircuitFactory::alu8Project,
                List.of("alu", "8-bit", "adder", "flags", "hierarchy")));
        registry.register(new StructuralImplementation("memory.register_file8x8",
                "Structural Register File 8x8", "Eight Register8 children with decoder and read muxes",
                ImplementationLevel.GATE, StructuralCircuitFactory::registerFile8x8Project,
                List.of("register file", "8x8", "state", "hierarchy")));
        return registry;
    }

    public void register(StructuralImplementation implementation) {
        List<StructuralImplementation> existing = implementations.computeIfAbsent(
                implementation.targetComponentId(), ignored -> new ArrayList<>());
        boolean duplicate = existing.stream().anyMatch(candidate ->
                candidate.level() == implementation.level()
                        && candidate.name().equals(implementation.name()));
        if (duplicate) {
            throw new IllegalStateException("Duplicate structural implementation: "
                    + implementation.targetComponentId() + " / " + implementation.level()
                    + " / " + implementation.name());
        }
        existing.add(implementation);
    }

    public Optional<StructuralImplementation> find(String componentId,
                                                    ImplementationLevel level) {
        return allFor(componentId).stream()
                .filter(implementation -> implementation.level() == level)
                .findFirst();
    }

    public List<StructuralImplementation> allFor(String componentId) {
        return List.copyOf(implementations.getOrDefault(componentId, List.of()));
    }

    public List<StructuralImplementation> all() {
        return implementations.values().stream().flatMap(List::stream).toList();
    }
}
