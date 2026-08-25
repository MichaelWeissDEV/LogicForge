package dev.logicforge.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.circuit.component.ComponentCategory;
import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.component.PortDirection;
import dev.logicforge.circuit.component.PortSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComponentRegistryTest {

    private final ComponentRegistry registry = ComponentRegistry.standard();

    @Test
    void theLibraryStillContainsEveryComponentOfTheFirstMilestone() {
        // The library has grown well past milestone 1; this only checks that nothing from
        // it was ever removed or renamed, not that the registry is limited to it.
        List<String> ids = registry.all().stream().map(ComponentType::id).toList();
        assertTrue(ids.containsAll(List.of(
                "source.zero", "source.one", "source.unknown", "source.highz",
                "source.toggle", "source.button",
                "logic.buffer", "logic.not", "logic.tristate", "logic.tristate.inverting",
                "logic.and", "logic.nand", "logic.or", "logic.nor", "logic.xor", "logic.xnor",
                "output.led", "output.probe", "output.pin")));
    }

    @Test
    void onlyCategoriesThatContainSomethingAreOffered() {
        assertTrue(registry.populatedCategories().containsAll(
                List.of(ComponentCategory.SOURCES, ComponentCategory.LOGIC, ComponentCategory.OUTPUTS)));
    }

    @Test
    void searchFindsComponentsByNameIdAndKeyword() {
        assertEquals(List.of("logic.and", "logic.nand"),
                registry.search("and").stream().map(ComponentType::id).toList().subList(0, 2));
        assertEquals(List.of("logic.xor", "logic.xnor"),
                registry.search("x").stream().map(ComponentType::id).toList().subList(0, 2));
        assertEquals(List.of("source.toggle"),
                registry.search("switch").stream().map(ComponentType::id).toList());
        assertEquals(List.of("logic.buffer"),
                registry.search("buffer").stream().map(ComponentType::id).toList().subList(0, 1));
        assertTrue(registry.search("verilog").isEmpty());
        assertEquals(registry.size(), registry.search("   ").size());
    }

    @Test
    void unknownComponentsAreReported() {
        assertTrue(registry.find("logic.nonexistent").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> registry.require("logic.nonexistent"));
        assertFalse(registry.contains("logic.nonexistent"));
    }

    @Test
    void registeringTheSameIdTwiceFails() {
        ComponentType existing = registry.require("logic.and");
        assertThrows(IllegalStateException.class, () -> registry.register(existing));
    }

    @Test
    void gatesGrowPortsAndBodyWithTheirInputCount() {
        ComponentDefinition and = registry.require("logic.and").definition();

        List<PortSpec> twoInputs = and.ports(and.defaultParameters());
        assertEquals(List.of("IN0", "IN1", "OUT"), twoInputs.stream().map(PortSpec::name).toList());
        assertEquals(PortDirection.OUTPUT, twoInputs.get(2).direction());
        assertEquals(48, and.bodySize(and.defaultParameters()).height());

        ParameterValues four = and.defaultParameters().with(LibraryParameters.INPUT_COUNT, 4);
        assertEquals(List.of("IN0", "IN1", "IN2", "IN3", "OUT"),
                and.ports(four).stream().map(PortSpec::name).toList());
        assertEquals(80, and.bodySize(four).height());
    }

    @Test
    void wideningAGateKeepsTheNamesOfTheExistingPorts() {
        ComponentDefinition and = registry.require("logic.and").definition();
        ParameterValues two = and.defaultParameters();
        ParameterValues eight = two.with(LibraryParameters.INPUT_COUNT, 8);

        List<String> before = and.ports(two).stream().map(PortSpec::name).toList();
        List<String> after = and.ports(eight).stream().map(PortSpec::name).toList();

        assertTrue(after.containsAll(before), "wires attached to IN0, IN1 and OUT survive");
    }

    @Test
    void behavioursFollowTheParametersOfAnInstance() {
        ComponentType toggle = registry.require("source.toggle");
        ParameterValues on = toggle.definition().defaultParameters()
                .with(LibraryParameters.INITIALLY_ON, true);

        assertEquals(dev.logicforge.logic.LogicState.ZERO,
                BehaviorHarness.evaluate(toggle.behaviorFor(toggle.definition().defaultParameters())));
        assertEquals(dev.logicforge.logic.LogicState.ONE,
                BehaviorHarness.evaluate(toggle.behaviorFor(on)));
    }

    @Test
    void everyComponentHasUsablePortsAndSize() {
        for (ComponentType type : registry.all()) {
            ComponentDefinition definition = type.definition();
            ParameterValues defaults = definition.defaultParameters();
            assertFalse(definition.ports(defaults).isEmpty(), definition.id() + " has no ports");
            assertTrue(definition.bodySize(defaults).width() > 0);
            assertFalse(definition.description().isBlank(), definition.id() + " has no description");
            assertFalse(definition.displayName().isBlank());
        }
    }
}
