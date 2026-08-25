package dev.logicforge.processor;

/**
 * An 8-bit data bus with a 16-bit address space.
 *
 * <p>This interface is intentionally independent of LogicForge's circuit/runtime classes.
 * The fast processor model can therefore be tested headlessly, while a later adapter may
 * map accesses to RAM/ROM models or a board-level simulator.</p>
 */
public interface ByteBus {
    int read(int address);
    void write(int address, int value);
}
