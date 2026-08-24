package dev.logicforge.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Complete four-state truth tables. Every binary operation is checked for all
 * 4 x 4 = 16 input combinations, not just the classical 0/1 cases.
 */
class LogicOperationsTest {

    /** Operand order for the tables below: 0, 1, X, Z. */
    private static final LogicState[] OPERANDS = {
            LogicState.ZERO, LogicState.ONE, LogicState.UNKNOWN, LogicState.HIGH_IMPEDANCE
    };

    // Rows are the left operand, columns the right operand, in OPERANDS order.
    private static final String[] AND_TABLE = {
            "0000",
            "01XX",
            "0XXX",
            "0XXX"
    };

    private static final String[] OR_TABLE = {
            "01XX",
            "1111",
            "X1XX",
            "X1XX"
    };

    private static final String[] XOR_TABLE = {
            "01XX",
            "10XX",
            "XXXX",
            "XXXX"
    };

    private static final String[] RESOLVE_TABLE = {
            "0XX0",
            "X1X1",
            "XXXX",
            "01XZ"
    };

    private static Stream<Arguments> table(String name, String[] rows) {
        List<Arguments> cases = new ArrayList<>();
        for (int row = 0; row < OPERANDS.length; row++) {
            for (int column = 0; column < OPERANDS.length; column++) {
                cases.add(Arguments.of(name, OPERANDS[row], OPERANDS[column],
                        LogicState.fromSymbol(rows[row].charAt(column))));
            }
        }
        return cases.stream();
    }

    static Stream<Arguments> andCases() {
        return table("AND", AND_TABLE);
    }

    static Stream<Arguments> orCases() {
        return table("OR", OR_TABLE);
    }

    static Stream<Arguments> xorCases() {
        return table("XOR", XOR_TABLE);
    }

    static Stream<Arguments> resolveCases() {
        return table("RESOLVE", RESOLVE_TABLE);
    }

    @ParameterizedTest(name = "{1} {0} {2} = {3}")
    @MethodSource("andCases")
    void andTruthTable(String name, LogicState a, LogicState b, LogicState expected) {
        assertEquals(expected, LogicOperations.and(a, b));
        assertEquals(expected, LogicOperations.and(b, a), "AND must be commutative");
        assertEquals(LogicOperations.not(expected), LogicOperations.nand(a, b));
    }

    @ParameterizedTest(name = "{1} {0} {2} = {3}")
    @MethodSource("orCases")
    void orTruthTable(String name, LogicState a, LogicState b, LogicState expected) {
        assertEquals(expected, LogicOperations.or(a, b));
        assertEquals(expected, LogicOperations.or(b, a), "OR must be commutative");
        assertEquals(LogicOperations.not(expected), LogicOperations.nor(a, b));
    }

    @ParameterizedTest(name = "{1} {0} {2} = {3}")
    @MethodSource("xorCases")
    void xorTruthTable(String name, LogicState a, LogicState b, LogicState expected) {
        assertEquals(expected, LogicOperations.xor(a, b));
        assertEquals(expected, LogicOperations.xor(b, a), "XOR must be commutative");
        assertEquals(LogicOperations.not(expected), LogicOperations.xnor(a, b));
    }

    @ParameterizedTest(name = "resolve({1}, {2}) = {3}")
    @MethodSource("resolveCases")
    void resolutionTruthTable(String name, LogicState a, LogicState b, LogicState expected) {
        assertEquals(expected, LogicOperations.resolve(a, b));
        assertEquals(expected, LogicOperations.resolve(b, a), "Resolution must be commutative");
    }

    @ParameterizedTest(name = "NOT {0} = {1}")
    @CsvSource({"ZERO,ONE", "ONE,ZERO", "UNKNOWN,UNKNOWN", "HIGH_IMPEDANCE,UNKNOWN"})
    void notTruthTable(LogicState input, LogicState expected) {
        assertEquals(expected, LogicOperations.not(input));
    }

    @Test
    void floatingGateInputIsUnknownNotZero() {
        assertEquals(LogicState.UNKNOWN, LogicOperations.asGateInput(LogicState.HIGH_IMPEDANCE));
        assertEquals(LogicState.ZERO, LogicOperations.asGateInput(LogicState.ZERO));
        assertEquals(LogicState.ONE, LogicOperations.asGateInput(LogicState.ONE));
        assertEquals(LogicState.UNKNOWN, LogicOperations.asGateInput(LogicState.UNKNOWN));
    }

    @Test
    void gatesNeverProduceHighImpedance() {
        for (LogicState a : OPERANDS) {
            for (LogicState b : OPERANDS) {
                assertTrue(LogicOperations.and(a, b).isDriven());
                assertTrue(LogicOperations.or(a, b).isDriven());
                assertTrue(LogicOperations.xor(a, b).isDriven());
            }
            assertTrue(LogicOperations.not(a).isDriven());
        }
    }

    @Test
    void resolutionOfNoDriversIsHighImpedance() {
        assertEquals(LogicState.HIGH_IMPEDANCE, LogicOperations.resolve());
    }

    @Test
    void resolutionIgnoresUndrivenContributions() {
        assertEquals(LogicState.ONE, LogicOperations.resolve(
                LogicState.HIGH_IMPEDANCE, LogicState.ONE, LogicState.HIGH_IMPEDANCE));
    }

    @Test
    void conflictingDriversResolveToUnknown() {
        assertEquals(LogicState.UNKNOWN, LogicOperations.resolve(LogicState.ZERO, LogicState.ONE));
        assertTrue(LogicOperations.hasDriverConflict(LogicState.ZERO, LogicState.ONE));
        assertFalse(LogicOperations.hasDriverConflict(LogicState.ONE, LogicState.HIGH_IMPEDANCE));
        assertFalse(LogicOperations.hasDriverConflict(LogicState.ONE, LogicState.ONE));
    }

    @Test
    void resolutionIsAssociative() {
        for (LogicState a : OPERANDS) {
            for (LogicState b : OPERANDS) {
                for (LogicState c : OPERANDS) {
                    assertEquals(
                            LogicOperations.resolve(LogicOperations.resolve(a, b), c),
                            LogicOperations.resolve(a, LogicOperations.resolve(b, c)));
                }
            }
        }
    }

    @Test
    void multiInputReduceFoldsWithIdentity() {
        assertEquals(LogicState.ONE, LogicOperations.reduce(LogicOperation.AND));
        assertEquals(LogicState.ZERO, LogicOperations.reduce(LogicOperation.OR));
        assertEquals(LogicState.ZERO, LogicOperations.reduce(LogicOperation.XOR));
    }

    @Test
    void multiInputXorIsOddParity() {
        assertEquals(LogicState.ONE, LogicOperations.reduce(LogicOperation.XOR,
                LogicState.ONE, LogicState.ZERO, LogicState.ZERO));
        assertEquals(LogicState.ZERO, LogicOperations.reduce(LogicOperation.XOR,
                LogicState.ONE, LogicState.ONE, LogicState.ZERO));
        assertEquals(LogicState.ONE, LogicOperations.reduce(LogicOperation.XOR,
                LogicState.ONE, LogicState.ONE, LogicState.ONE));
        assertEquals(LogicState.UNKNOWN, LogicOperations.reduce(LogicOperation.XOR,
                LogicState.ONE, LogicState.UNKNOWN, LogicState.ONE));
        assertEquals(LogicState.UNKNOWN, LogicOperations.reduce(LogicOperation.XOR,
                LogicState.ONE, LogicState.HIGH_IMPEDANCE));
    }

    @Test
    void controllingValuesSurviveUnknownInputs() {
        assertEquals(LogicState.ZERO, LogicOperations.reduce(LogicOperation.AND,
                LogicState.ONE, LogicState.UNKNOWN, LogicState.ZERO));
        assertEquals(LogicState.ONE, LogicOperations.reduce(LogicOperation.OR,
                LogicState.ZERO, LogicState.UNKNOWN, LogicState.ONE));
        assertEquals(LogicState.UNKNOWN, LogicOperations.reduce(LogicOperation.AND,
                LogicState.ONE, LogicState.UNKNOWN, LogicState.ONE));
    }

    @Test
    void vectorOperationsWorkBitwise() {
        LogicVector a = LogicVector.of("1010");
        LogicVector b = LogicVector.of("110X");
        assertEquals(LogicVector.of("1000"), LogicOperations.apply(LogicOperation.AND, a, b));
        assertEquals(LogicVector.of("111X"), LogicOperations.apply(LogicOperation.OR, a, b));
        assertEquals(LogicVector.of("0101"), LogicOperations.not(a));
        assertEquals(LogicVector.of("011X"), LogicOperations.apply(LogicOperation.XOR, a, b));
    }

    @Test
    void vectorOperationsRejectWidthMismatch() {
        LogicVector a = LogicVector.of("1010");
        LogicVector b = LogicVector.of("10");
        org.junit.jupiter.api.Assertions.assertThrows(WidthMismatchException.class,
                () -> LogicOperations.apply(LogicOperation.AND, a, b));
        org.junit.jupiter.api.Assertions.assertThrows(WidthMismatchException.class,
                () -> LogicOperations.resolve(a, b));
    }

    @Test
    void vectorResolutionFollowsSameRules() {
        assertEquals(LogicVector.of("01XZ"),
                LogicOperations.resolve(LogicVector.of("0ZXZ"), LogicVector.of("Z1ZZ")));
    }
}
