package dev.logicforge.library.behavior;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.ComponentRuntimeState;
import java.util.Arrays;

/**
 * The contents of a RAM: one word per address, zero-initialized on reset for deterministic
 * simulation (a documented, revisitable choice — real hardware powers up undefined).
 */
final class RamState implements ComponentRuntimeState {

    private final int wordCount;
    private final BitWidth dataWidth;
    private LogicVector[] memory;

    RamState(int addressBits, BitWidth dataWidth) {
        this.wordCount = 1 << addressBits;
        this.dataWidth = dataWidth;
        reset();
    }

    LogicVector read(int address) {
        return address >= 0 && address < wordCount ? memory[address] : LogicVector.repeat(LogicState.UNKNOWN, dataWidth);
    }

    void write(int address, LogicVector value) {
        if (address >= 0 && address < wordCount) {
            memory[address] = value;
        }
    }

    int wordCount() {
        return wordCount;
    }

    @Override
    public void reset() {
        memory = new LogicVector[wordCount];
        Arrays.fill(memory, LogicVector.repeat(LogicState.ZERO, dataWidth));
    }
}
