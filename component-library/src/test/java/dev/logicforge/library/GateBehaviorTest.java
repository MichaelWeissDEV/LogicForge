package dev.logicforge.library;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.logicforge.library.behavior.NaryGateBehavior;
import dev.logicforge.library.behavior.UnaryGateBehavior;
import dev.logicforge.logic.LogicOperation;
import dev.logicforge.logic.LogicState;
import dev.logicforge.simulation.ComponentBehavior;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Every gate is checked over the full four-state input space, not just over 0 and 1: for
 * two inputs that is 4 x 4 = 16 cases per gate.
 */
class GateBehaviorTest {

    private static final LogicState[] OPERANDS = {
            LogicState.ZERO, LogicState.ONE, LogicState.UNKNOWN, LogicState.HIGH_IMPEDANCE
    };

    // Rows: first input, columns: second input, both in OPERANDS order.
    private static final String[] AND = {"0000", "01XX", "0XXX", "0XXX"};
    private static final String[] NAND = {"1111", "10XX", "1XXX", "1XXX"};
    private static final String[] OR = {"01XX", "1111", "X1XX", "X1XX"};
    private static final String[] NOR = {"10XX", "0000", "X0XX", "X0XX"};
    private static final String[] XOR = {"01XX", "10XX", "XXXX", "XXXX"};
    private static final String[] XNOR = {"10XX", "01XX", "XXXX", "XXXX"};

    static Stream<Arguments> twoInputGates() {
        List<Arguments> cases = new ArrayList<>();
        addGate(cases, "logic.and", LogicOperation.AND, false, AND);
        addGate(cases, "logic.nand", LogicOperation.AND, true, NAND);
        addGate(cases, "logic.or", LogicOperation.OR, false, OR);
        addGate(cases, "logic.nor", LogicOperation.OR, true, NOR);
        addGate(cases, "logic.xor", LogicOperation.XOR, false, XOR);
        addGate(cases, "logic.xnor", LogicOperation.XOR, true, XNOR);
        return cases.stream();
    }

    private static void addGate(List<Arguments> cases, String name, LogicOperation operation,
                                boolean invert, String[] table) {
        ComponentBehavior behavior = new NaryGateBehavior(operation, invert);
        for (int row = 0; row < OPERANDS.length; row++) {
            for (int column = 0; column < OPERANDS.length; column++) {
                cases.add(Arguments.of(name, behavior, OPERANDS[row], OPERANDS[column],
                        LogicState.fromSymbol(table[row].charAt(column))));
            }
        }
    }

    @ParameterizedTest(name = "{0}({2}, {3}) = {4}")
    @MethodSource("twoInputGates")
    void twoInputTruthTables(String name, ComponentBehavior behavior, LogicState a, LogicState b,
                             LogicState expected) {
        assertEquals(expected, BehaviorHarness.evaluate(behavior, a, b));
    }

    @ParameterizedTest(name = "{0} inputs")
    @ValueSource(ints = {2, 3, 4, 8, 16})
    void multiInputGatesFoldOverEveryInput(int inputs) {
        LogicState[] allOnes = filled(inputs, LogicState.ONE);
        LogicState[] allZeros = filled(inputs, LogicState.ZERO);
        LogicState[] oneZero = filled(inputs, LogicState.ONE);
        oneZero[inputs - 1] = LogicState.ZERO;

        assertEquals(LogicState.ONE, BehaviorHarness.evaluate(gate(LogicOperation.AND, false), allOnes));
        assertEquals(LogicState.ZERO, BehaviorHarness.evaluate(gate(LogicOperation.AND, false), oneZero));
        assertEquals(LogicState.ZERO, BehaviorHarness.evaluate(gate(LogicOperation.AND, true), allOnes));

        assertEquals(LogicState.ZERO, BehaviorHarness.evaluate(gate(LogicOperation.OR, false), allZeros));
        assertEquals(LogicState.ONE, BehaviorHarness.evaluate(gate(LogicOperation.OR, false), oneZero));
        assertEquals(LogicState.ZERO, BehaviorHarness.evaluate(gate(LogicOperation.OR, true), oneZero));
    }

    @ParameterizedTest(name = "XOR over {0} inputs")
    @ValueSource(ints = {2, 3, 4, 8})
    void multiInputXorCountsOnes(int inputs) {
        for (int ones = 0; ones <= inputs; ones++) {
            LogicState[] values = filled(inputs, LogicState.ZERO);
            for (int i = 0; i < ones; i++) {
                values[i] = LogicState.ONE;
            }
            LogicState expected = ones % 2 == 1 ? LogicState.ONE : LogicState.ZERO;
            assertEquals(expected, BehaviorHarness.evaluate(gate(LogicOperation.XOR, false), values),
                    inputs + " inputs of which " + ones + " are 1");
            assertEquals(expected == LogicState.ONE ? LogicState.ZERO : LogicState.ONE,
                    BehaviorHarness.evaluate(gate(LogicOperation.XOR, true), values));
        }
    }

    @Test
    void aControllingInputDecidesEvenWithUnknownsAround() {
        assertEquals(LogicState.ZERO, BehaviorHarness.evaluate(gate(LogicOperation.AND, false),
                LogicState.ONE, LogicState.UNKNOWN, LogicState.ZERO, LogicState.HIGH_IMPEDANCE));
        assertEquals(LogicState.ONE, BehaviorHarness.evaluate(gate(LogicOperation.OR, false),
                LogicState.ZERO, LogicState.HIGH_IMPEDANCE, LogicState.ONE));
        assertEquals(LogicState.UNKNOWN, BehaviorHarness.evaluate(gate(LogicOperation.XOR, false),
                LogicState.ZERO, LogicState.ONE, LogicState.HIGH_IMPEDANCE));
    }

    @ParameterizedTest(name = "buffer({0}) = {1}, not({0}) = {2}")
    @CsvSource({
            "ZERO,ZERO,ONE",
            "ONE,ONE,ZERO",
            "UNKNOWN,UNKNOWN,UNKNOWN",
            "HIGH_IMPEDANCE,UNKNOWN,UNKNOWN"})
    void bufferAndInverterNeverPassOnHighImpedance(LogicState input, LogicState buffered,
                                                   LogicState inverted) {
        assertEquals(buffered, BehaviorHarness.evaluate(UnaryGateBehavior.BUFFER, input));
        assertEquals(inverted, BehaviorHarness.evaluate(UnaryGateBehavior.INVERTER, input));
    }

    private static ComponentBehavior gate(LogicOperation operation, boolean invert) {
        return new NaryGateBehavior(operation, invert);
    }

    private static LogicState[] filled(int count, LogicState value) {
        LogicState[] values = new LogicState[count];
        java.util.Arrays.fill(values, value);
        return values;
    }
}
