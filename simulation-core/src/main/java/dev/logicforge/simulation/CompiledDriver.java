package dev.logicforge.simulation;

/** One component output attached to one net. Each driver contributes one value to it. */
public record CompiledDriver(int id, int componentId, int outputIndex, int netId) {
}
