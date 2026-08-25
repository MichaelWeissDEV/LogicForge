package dev.logicforge.processor.mos6502.transistor;

import dev.logicforge.transistor.CompiledTransistorNetlist;
import dev.logicforge.transistor.DriveState;
import dev.logicforge.transistor.SwitchLevelSolver;
import dev.logicforge.transistor.TransistorNetlist;

/**
 * MOS 6502 chip facade backed by an imported switch-level transistor netlist.
 *
 * <p>The class addresses signals by the canonical Visual6502 node names rather than hard
 * coded node numbers, so another compatible extracted netlist can be substituted. Internal
 * register values are read from the actual simulated silicon nodes; they are not mirrored
 * in a second emulator state.</p>
 */
public final class Mos6502TransistorChip {
    private final CompiledTransistorNetlist netlist;
    private final SwitchLevelSolver solver;
    private boolean clockHigh;

    public Mos6502TransistorChip(TransistorNetlist source) {
        this.netlist = CompiledTransistorNetlist.compile(source);
        validateRequiredNames();
        this.solver = new SwitchLevelSolver(netlist);
        initialisePins();
    }

    private void validateRequiredNames() {
        String[] required = {
                "vcc", "vss", "clk0", "res", "rdy", "irq", "nmi", "so", "rw", "sync"
        };
        for (String name : required) {
            netlist.requireNamedNode(name);
        }
        for (int bit = 0; bit < 8; bit++) {
            netlist.requireNamedNode("db" + bit);
            netlist.requireNamedNode("a" + bit);
            netlist.requireNamedNode("x" + bit);
            netlist.requireNamedNode("y" + bit);
            netlist.requireNamedNode("s" + bit);
            netlist.requireNamedNode("pcl" + bit);
            netlist.requireNamedNode("pch" + bit);
        }
        for (int bit = 0; bit < 16; bit++) {
            netlist.requireNamedNode("ab" + bit);
        }
    }

    /** Stable deasserted input defaults; RESET remains asserted until released by the caller. */
    private void initialisePins() {
        solver.drive("clk0", DriveState.LOW);
        solver.drive("res", DriveState.LOW);
        solver.drive("rdy", DriveState.HIGH);
        solver.drive("irq", DriveState.HIGH);
        solver.drive("nmi", DriveState.HIGH);
        // Visual6502 initializes SO low. It is an edge-sensitive asynchronous input; keeping
        // it stable avoids injecting a later edge until the caller explicitly changes it.
        solver.drive("so", DriveState.LOW);
        releaseDataBus();
        solver.settle();
        clockHigh = false;
    }

    /** Reset solver state and restore stable external input defaults. */
    public void resetNetwork() {
        solver.reset();
        initialisePins();
    }

    /**
     * Reproduce the reference simulator's deterministic transistor-network reset warm-up:
     * eight full clock cycles with RESET low, then release RESET and run nine more cycles.
     */
    public void referenceResetSequence() {
        setResetAsserted(true);
        setReady(true);
        setIrqAsserted(false);
        setNmiAsserted(false);
        setClock(false);
        for (int i = 0; i < 8; i++) {
            setClock(true);
            setClock(false);
        }
        setResetAsserted(false);
        for (int i = 0; i < 9; i++) {
            setClock(true);
            setClock(false);
        }
    }

    public void setClock(boolean high) {
        solver.drive("clk0", high ? DriveState.HIGH : DriveState.LOW);
        solver.settle();
        clockHigh = high;
    }

    public void halfStep() {
        setClock(!clockHigh);
    }

    public boolean clockHigh() {
        return clockHigh;
    }

    public void setResetAsserted(boolean asserted) {
        solver.drive("res", asserted ? DriveState.LOW : DriveState.HIGH);
        solver.settle();
    }

    public void setReady(boolean ready) {
        solver.drive("rdy", ready ? DriveState.HIGH : DriveState.LOW);
        solver.settle();
    }

    public void setIrqAsserted(boolean asserted) {
        solver.drive("irq", asserted ? DriveState.LOW : DriveState.HIGH);
        solver.settle();
    }

    public void setNmiAsserted(boolean asserted) {
        solver.drive("nmi", asserted ? DriveState.LOW : DriveState.HIGH);
        solver.settle();
    }

    public void setSoAsserted(boolean asserted) {
        solver.drive("so", asserted ? DriveState.LOW : DriveState.HIGH);
        solver.settle();
    }

    /** External memory/peripheral drives D0..D7. Use {@link #releaseDataBus()} for writes. */
    public void driveDataBus(int value) {
        for (int bit = 0; bit < 8; bit++) {
            solver.drive("db" + bit, ((value >>> bit) & 1) != 0 ? DriveState.HIGH : DriveState.LOW);
        }
        solver.settle();
    }

    public void releaseDataBus() {
        for (int bit = 0; bit < 8; bit++) {
            solver.drive("db" + bit, DriveState.FLOATING);
        }
        solver.settle();
    }

    public int addressBus() { return readBits("ab", 16); }
    public int dataBus() { return readBits("db", 8); }
    public boolean readCycle() { return solver.high("rw"); }
    public boolean sync() { return solver.high("sync"); }
    public boolean phi1Out() { return solver.high("clk1out"); }
    public boolean phi2Out() { return solver.high("clk2out"); }

    public int accumulator() { return readBits("a", 8); }
    public int x() { return readBits("x", 8); }
    public int y() { return readBits("y", 8); }
    public int stackPointer() { return readBits("s", 8); }
    public int pc() { return (readBits("pch", 8) << 8) | readBits("pcl", 8); }

    public int status() {
        int value = 0x20; // P bit 5 has no physical storage node and reads as 1 conventionally.
        for (int bit : new int[]{0, 1, 2, 3, 4, 6, 7}) {
            if (solver.high("p" + bit)) {
                value |= 1 << bit;
            }
        }
        return value;
    }

    public Mos6502SiliconSnapshot snapshot() {
        return new Mos6502SiliconSnapshot(addressBus(), dataBus(), readCycle(), sync(),
                accumulator(), x(), y(), stackPointer(), pc(), status());
    }

    /** Direct named-node probing for the future Logic Analyzer / silicon explorer. */
    public boolean nodeHigh(String name) {
        return solver.high(name);
    }

    public SwitchLevelSolver solver() {
        return solver;
    }

    private int readBits(String prefix, int width) {
        int value = 0;
        for (int bit = 0; bit < width; bit++) {
            if (solver.high(prefix + bit)) {
                value |= 1 << bit;
            }
        }
        return value;
    }
}
