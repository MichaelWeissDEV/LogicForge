package dev.logicforge.analyzer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import org.junit.jupiter.api.Test;

/**
 * Pure evaluation semantics of {@link TriggerCondition} — no simulation involved. The named
 * cases spell out exactly how each condition treats a transition through X or Z, since a
 * naive two-valued reading of "rising edge" would silently drop the four-valued-logic
 * requirement the rest of the simulator honors everywhere else.
 */
class TriggerConditionTest {

    private static LogicVector bit(LogicState state) {
        return LogicVector.single(state);
    }

    // ------------------------------------------------------------ RisingEdge

    @Test
    void risingEdgeMatchesALiteralZeroToOne() {
        TriggerCondition condition = new TriggerCondition.RisingEdge(0);
        assertTrue(condition.matches(bit(LogicState.ZERO), bit(LogicState.ONE)));
    }

    @Test
    void risingEdgeDoesNotMatchZeroToX() {
        TriggerCondition condition = new TriggerCondition.RisingEdge(0);
        assertFalse(condition.matches(bit(LogicState.ZERO), bit(LogicState.UNKNOWN)));
    }

    @Test
    void risingEdgeDoesNotMatchXToOne() {
        TriggerCondition condition = new TriggerCondition.RisingEdge(0);
        assertFalse(condition.matches(bit(LogicState.UNKNOWN), bit(LogicState.ONE)),
                "a settle out of X is not itself a rising edge — only a literal 0->1 counts");
    }

    @Test
    void risingEdgeDoesNotMatchZToZero() {
        TriggerCondition condition = new TriggerCondition.RisingEdge(0);
        assertFalse(condition.matches(bit(LogicState.HIGH_IMPEDANCE), bit(LogicState.ZERO)));
    }

    // ------------------------------------------------------------ FallingEdge

    @Test
    void fallingEdgeMatchesALiteralOneToZero() {
        TriggerCondition condition = new TriggerCondition.FallingEdge(0);
        assertTrue(condition.matches(bit(LogicState.ONE), bit(LogicState.ZERO)));
    }

    @Test
    void fallingEdgeDoesNotMatchOneToX() {
        TriggerCondition condition = new TriggerCondition.FallingEdge(0);
        assertFalse(condition.matches(bit(LogicState.ONE), bit(LogicState.UNKNOWN)));
    }

    // ------------------------------------------------------------ AnyEdge

    @Test
    void anyEdgeMatchesZeroToX() {
        TriggerCondition condition = new TriggerCondition.AnyEdge(0);
        assertTrue(condition.matches(bit(LogicState.ZERO), bit(LogicState.UNKNOWN)),
                "AnyEdge is the four-valued-aware trigger — every real change counts");
    }

    @Test
    void anyEdgeMatchesXToOne() {
        TriggerCondition condition = new TriggerCondition.AnyEdge(0);
        assertTrue(condition.matches(bit(LogicState.UNKNOWN), bit(LogicState.ONE)));
    }

    @Test
    void anyEdgeMatchesZToZero() {
        TriggerCondition condition = new TriggerCondition.AnyEdge(0);
        assertTrue(condition.matches(bit(LogicState.HIGH_IMPEDANCE), bit(LogicState.ZERO)));
    }

    @Test
    void anyEdgeDoesNotMatchWhenTheBitDidNotActuallyChange() {
        TriggerCondition condition = new TriggerCondition.AnyEdge(0);
        assertFalse(condition.matches(bit(LogicState.ONE), bit(LogicState.ONE)));
    }

    // ------------------------------------------------------------ Value triggers

    @Test
    void valueEqualsMatchesOnlyTheExactFourValuedTarget() {
        LogicVector target = LogicVector.fromUnsignedLong(0x42, 8);
        TriggerCondition condition = new TriggerCondition.ValueEquals(target);
        assertTrue(condition.matches(LogicVector.fromUnsignedLong(0, 8), target));
        assertFalse(condition.matches(target, LogicVector.fromUnsignedLong(0x43, 8)));
    }

    @Test
    void valueEqualsTargetingXOnlyMatchesActualXNeverActingAsAWildcard() {
        LogicVector allX = LogicVector.repeat(LogicState.UNKNOWN, 8);
        TriggerCondition condition = new TriggerCondition.ValueEquals(allX);
        assertTrue(condition.matches(LogicVector.fromUnsignedLong(0, 8), allX));
        assertFalse(condition.matches(allX, LogicVector.fromUnsignedLong(0x7, 8)),
                "X in the target is a literal state to match, not a don't-care wildcard");
    }

    @Test
    void valueNotEqualsFiresTheInstantTheValueLeavesTheTarget() {
        LogicVector target = LogicVector.fromUnsignedLong(0xFF, 8);
        TriggerCondition condition = new TriggerCondition.ValueNotEquals(target);
        assertFalse(condition.matches(LogicVector.fromUnsignedLong(0, 8), target));
        assertTrue(condition.matches(target, LogicVector.fromUnsignedLong(0xFE, 8)));
    }
}
