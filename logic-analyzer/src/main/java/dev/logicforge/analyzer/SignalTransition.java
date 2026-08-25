package dev.logicforge.analyzer;

import dev.logicforge.logic.LogicVector;

/**
 * One recorded change of a watched net: the value it took on, and exactly when — physical
 * simulation time plus the delta cycle, so two transitions at the same instant still have
 * a well defined order.
 */
public record SignalTransition(long time, int deltaCycle, LogicVector value) {
}
