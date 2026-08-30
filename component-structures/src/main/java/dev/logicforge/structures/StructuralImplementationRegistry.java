package dev.logicforge.structures;

import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.LibraryParameters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Headless registry of canonical structural/reference implementations. */
public final class StructuralImplementationRegistry {

    private final ComponentRegistry componentRegistry;
    private final Map<String, List<StructuralImplementationDescriptor>> implementations =
            new LinkedHashMap<>();

    public StructuralImplementationRegistry() {
        this(ComponentRegistry.standard());
    }

    public StructuralImplementationRegistry(ComponentRegistry componentRegistry) {
        this.componentRegistry = componentRegistry;
    }

    public static StructuralImplementationRegistry standard() {
        StructuralImplementationRegistry registry = new StructuralImplementationRegistry();
        registry.register(descriptor("arithmetic.half_adder", ParameterMatcher.any(),
                "Gate-level Half Adder", "XOR sum and AND carry",
                StructuralCircuitFactory::halfAdderProject,
                List.of("adder", "combinational", "gates")));
        registry.register(descriptor("arithmetic.full_adder", ParameterMatcher.any(),
                "Hierarchical Full Adder", "Two half adders and an OR gate",
                StructuralCircuitFactory::fullAdderProject,
                List.of("adder", "hierarchy", "gates")));
        registry.register(descriptor("arithmetic.adder",
                ParameterMatcher.equalTo(LibraryParameters.WIDTH, 4),
                "Ripple Adder 4", "Four explicit full-adder stages",
                () -> StructuralCircuitFactory.rippleAdderProject(4),
                List.of("adder", "ripple", "4-bit", "74HC283", "hierarchy")));
        registry.register(descriptor("arithmetic.adder",
                ParameterMatcher.equalTo(LibraryParameters.WIDTH, 8),
                "Ripple Adder 8", "Eight explicit full-adder stages",
                () -> StructuralCircuitFactory.rippleAdderProject(8),
                List.of("adder", "ripple", "8-bit", "hierarchy")));
        registry.register(descriptor("routing.mux2",
                ParameterMatcher.equalTo(LibraryParameters.WIDTH, 1),
                "Gate-level Mux2", "NOT/AND/OR implementation",
                StructuralCircuitFactory::mux2Project,
                List.of("mux", "routing", "gates")));
        for (int width : List.of(3, 4, 8, 16)) {
            registry.register(descriptor("routing.mux2",
                    ParameterMatcher.equalTo(LibraryParameters.WIDTH, width),
                    "Structural Mux2 (width " + width + ")", "Bit-sliced gate-level 2:1 mux",
                    () -> StructuralCircuitFactory.structuralBusMuxProject(width),
                    List.of("mux", "routing", "hierarchy")));
        }
        registry.register(descriptor("arithmetic.decrementer",
                ParameterMatcher.equalTo(LibraryParameters.WIDTH, 16),
                "Structural Decrementer16", "A + 0xFFFF through the ripple-adder hierarchy",
                StructuralCircuitFactory::decrementer16Project,
                List.of("decrement", "16-bit", "adder", "hierarchy")));
        registry.register(descriptor("sequential.d_ff",
                ParameterMatcher.equalTo(LibraryParameters.CLOCK_EDGE, "rising"),
                "Master-Slave DFF", "DFF -> D latches -> SR latches -> gates",
                StructuralCircuitFactory::dFlipFlopProject,
                List.of("flip-flop", "latch", "state", "hierarchy")));
        registry.register(descriptor("sequential.register",
                ParameterMatcher.equalTo(LibraryParameters.WIDTH, 8)
                        .and(ParameterMatcher.equalTo(LibraryParameters.CLOCK_EDGE, "rising")),
                "Structural Register8", "Eight DFFs and eight load muxes",
                StructuralCircuitFactory::register8Project,
                List.of("register", "8-bit", "state", "hierarchy")));
        registry.register(descriptor("arithmetic.alu",
                ParameterMatcher.equalTo(LibraryParameters.WIDTH, 8),
                "Structural ALU8", "Ripple adder, gate logic, shifts, mux tree and flags",
                StructuralCircuitFactory::alu8Project,
                List.of("alu", "8-bit", "adder", "flags", "hierarchy")));
        registry.register(descriptor("memory.register_file",
                ParameterMatcher.equalTo(LibraryParameters.WIDTH, 8)
                        .and(ParameterMatcher.equalTo(LibraryParameters.REGISTER_COUNT, 8)),
                "Structural Register File 8x8", "Eight Register8 children with decoder and read muxes",
                StructuralCircuitFactory::registerFile8x8Project,
                List.of("register file", "8x8", "state", "hierarchy")));
        for (int width : List.of(1, 3, 4, 8)) {
            registry.register(descriptor("sequential.register_reset",
                    ParameterMatcher.equalTo(LibraryParameters.WIDTH, width)
                            .and(ParameterMatcher.equalTo(LibraryParameters.CLOCK_EDGE, "rising")),
                    "Structural Register" + width + " (Reset)", "A DFF and a load mux per bit, "
                            + "each fed back through an asynchronous clear",
                    () -> StructuralCircuitFactory.structuralRegister(width, true, 0),
                    List.of("register", "reset", "state", "hierarchy")));
        }
        for (int resetValue : List.of(0, 0xBFFF)) {
            registry.register(descriptor("sequential.loadable_counter",
                    ParameterMatcher.equalTo(LibraryParameters.WIDTH, 16)
                            .and(ParameterMatcher.equalTo(LibraryParameters.CLOCK_EDGE, "rising"))
                            .and(ParameterMatcher.equalTo(LibraryParameters.RESET_VALUE,
                                    Integer.toHexString(resetValue))),
                    "Structural Loadable Counter16 (reset 0x" + Integer.toHexString(resetValue) + ")",
                    "Register16 (Reset), RippleAdder16 and two load muxes, with a gate-level "
                            + "terminal-count AND reduction",
                    () -> StructuralCircuitFactory.loadableCounter16Project(resetValue),
                    List.of("counter", "program counter", "pc", "loadable", "hierarchy")));
        }
        registry.register(descriptor("sequential.modulo_counter",
                ParameterMatcher.equalTo(LibraryParameters.WIDTH, 3)
                        .and(ParameterMatcher.equalTo(LibraryParameters.MODULUS, 8))
                        .and(ParameterMatcher.equalTo(LibraryParameters.CLOCK_EDGE, "rising")),
                "Structural Modulo Counter3", "Register3 (Reset) and RippleAdder3 — modulus 8 is "
                        + "the full 3-bit range, so this is an ordinary binary counter",
                StructuralCircuitFactory::moduloCounter3Project,
                List.of("counter", "modulo", "hierarchy")));
        return registry;
    }

    private static StructuralImplementationDescriptor descriptor(
            String targetDefinitionId, ParameterMatcher matcher, String name, String description,
            java.util.function.Supplier<dev.logicforge.circuit.document.CircuitProject> factory,
            List<String> tags) {
        return new StructuralImplementationDescriptor(targetDefinitionId, ImplementationLevel.GATE,
                matcher, factory, name, description, tags);
    }

    public void register(StructuralImplementationDescriptor implementation) {
        if (!componentRegistry.contains(implementation.targetDefinitionId())) {
            throw new IllegalArgumentException("Unknown target component definition: "
                    + implementation.targetDefinitionId());
        }
        List<StructuralImplementationDescriptor> existing = implementations.computeIfAbsent(
                implementation.targetDefinitionId(), ignored -> new ArrayList<>());
        boolean duplicate = existing.stream().anyMatch(candidate ->
                candidate.level() == implementation.level()
                        && candidate.name().equals(implementation.name()));
        if (duplicate) {
            throw new IllegalStateException("Duplicate structural implementation: "
                    + implementation.targetDefinitionId() + " / " + implementation.level()
                    + " / " + implementation.name());
        }
        existing.add(implementation);
    }

    public Optional<StructuralImplementationDescriptor> find(String componentId,
            ParameterValues parameters, ImplementationLevel level) {
        return allFor(componentId).stream()
                .filter(implementation -> implementation.level() == level)
                .filter(implementation -> implementation.supports(parameters))
                .findFirst();
    }

    public List<StructuralImplementationDescriptor> allFor(String componentId) {
        return List.copyOf(implementations.getOrDefault(componentId, List.of()));
    }

    public List<StructuralImplementationDescriptor> all() {
        return implementations.values().stream().flatMap(List::stream).toList();
    }
}
