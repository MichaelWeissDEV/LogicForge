package dev.logicforge.processor.mos6502.transistor;

import dev.logicforge.processor.ByteBus;
import java.util.Objects;

/**
 * Optional headless board harness that connects a transistor 6502 to an 8-bit/64-KiB host
 * memory bus using the same half-cycle convention as the Visual6502 reference controller.
 * LogicForge itself will eventually replace this helper with normal simulated RAM/ROM nets.
 */
public final class Mos6502TransistorMachine {
    private final Mos6502TransistorChip chip;
    private final ByteBus memory;
    private long halfCycles;

    public Mos6502TransistorMachine(Mos6502TransistorChip chip, ByteBus memory) {
        this.chip = Objects.requireNonNull(chip, "chip");
        this.memory = Objects.requireNonNull(memory, "memory");
    }

    /**
     * Toggle one clock phase and service memory at the appropriate edge.
     * Falling edge: provide memory data for CPU reads.
     * Rising edge: commit CPU writes.
     */
    public Trace halfStep() {
        if (chip.clockHigh()) {
            chip.setClock(false);
            if (chip.readCycle()) {
                chip.driveDataBus(memory.read(chip.addressBus()));
            } else {
                chip.releaseDataBus();
            }
        } else {
            // If this is a write cycle, memory must stop driving before PHI2 rises.
            if (!chip.readCycle()) {
                chip.releaseDataBus();
            }
            chip.setClock(true);
            if (!chip.readCycle()) {
                memory.write(chip.addressBus(), chip.dataBus());
            }
        }
        halfCycles++;
        return trace();
    }

    /** Run the deterministic reference-style reset sequence while servicing the memory bus. */
    public void reset() {
        chip.resetNetwork();
        chip.setResetAsserted(true);
        chip.setReady(true);
        chip.setIrqAsserted(false);
        chip.setNmiAsserted(false);
        chip.setClock(false);
        chip.releaseDataBus();

        for (int i = 0; i < 8; i++) {
            halfStep();
            halfStep();
        }
        chip.setResetAsserted(false);
        for (int i = 0; i < 18; i++) {
            halfStep();
        }
        halfCycles = 0;
    }

    public Trace trace() {
        return new Trace(halfCycles, chip.clockHigh(), chip.addressBus(), chip.dataBus(),
                chip.readCycle(), chip.sync(), chip.pc(), chip.accumulator(), chip.x(), chip.y(),
                chip.stackPointer(), chip.status());
    }

    public Mos6502TransistorChip chip() { return chip; }
    public long halfCycles() { return halfCycles; }

    public record Trace(
            long halfCycle,
            boolean clockHigh,
            int address,
            int data,
            boolean read,
            boolean sync,
            int pc,
            int a,
            int x,
            int y,
            int sp,
            int status) {
    }
}
