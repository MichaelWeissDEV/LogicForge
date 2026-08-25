package dev.logicforge.processor.mos6502.transistor;

/** Architectural state reconstructed directly from named transistor-netlist nodes. */
public record Mos6502SiliconSnapshot(
        int addressBus,
        int dataBus,
        boolean readCycle,
        boolean sync,
        int a,
        int x,
        int y,
        int sp,
        int pc,
        int status) {
}
