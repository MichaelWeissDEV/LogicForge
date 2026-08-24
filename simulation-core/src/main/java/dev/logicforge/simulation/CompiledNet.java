package dev.logicforge.simulation;

import dev.logicforge.logic.BitWidth;

/**
 * A net in the compiled circuit: the electrical node that ports share.
 *
 * <p>A net has any number of drivers and any number of consumers. Version 0.1 usually has
 * exactly one driver, but the model has never assumed that — which is what makes
 * tri-state buffers work today and shared buses possible later.
 */
public record CompiledNet(int id, BitWidth width, int[] drivers, int[] consumers) {

    public int driverCount() {
        return drivers.length;
    }

    public int consumerCount() {
        return consumers.length;
    }
}
