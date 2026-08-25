package dev.logicforge.processor.mos6502;

/** Immutable debugger view of the fast MOS 6502 model. */
public record Mos6502Snapshot(
        int a,
        int x,
        int y,
        int sp,
        int pc,
        int status,
        long instructions,
        long cycles,
        boolean irqAsserted,
        boolean nmiPending) {
}
