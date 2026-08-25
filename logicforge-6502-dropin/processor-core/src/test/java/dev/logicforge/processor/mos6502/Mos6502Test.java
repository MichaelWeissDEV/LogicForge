package dev.logicforge.processor.mos6502;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.processor.ArrayByteBus;
import dev.logicforge.processor.IllegalOpcodeException;
import org.junit.jupiter.api.Test;

final class Mos6502Test {
    @Test
    void resetLoadsVector() {
        ArrayByteBus bus = new ArrayByteBus();
        bus.poke(0xfffc, 0x34);
        bus.poke(0xfffd, 0x12);
        Mos6502 cpu = new Mos6502(bus);
        cpu.reset();
        assertEquals(0x1234, cpu.pc());
        assertTrue(cpu.interruptDisable());
        assertEquals(0xfd, cpu.sp());
    }

    @Test
    void executesSmallProgram() {
        ArrayByteBus bus = new ArrayByteBus();
        int[] program = {0xa9, 0x10, 0x69, 0x20, 0x8d, 0x00, 0x02, 0xe8};
        for (int i = 0; i < program.length; i++) bus.poke(0x8000 + i, program[i]);
        Mos6502 cpu = new Mos6502(bus);
        cpu.setPc(0x8000);

        assertEquals(2, cpu.stepInstruction());
        assertEquals(0x10, cpu.a());
        assertEquals(2, cpu.stepInstruction());
        assertEquals(0x30, cpu.a());
        assertEquals(4, cpu.stepInstruction());
        assertEquals(0x30, bus.peek(0x0200));
        assertEquals(2, cpu.stepInstruction());
        assertEquals(1, cpu.x());
    }

    @Test
    void jmpIndirectReproducesNmosPageWrapQuirk() {
        ArrayByteBus bus = new ArrayByteBus();
        bus.poke(0x8000, 0x6c);
        bus.poke(0x8001, 0xff);
        bus.poke(0x8002, 0x12);
        bus.poke(0x12ff, 0x34);
        bus.poke(0x1200, 0x56);
        bus.poke(0x1300, 0x99);
        Mos6502 cpu = new Mos6502(bus);
        cpu.setPc(0x8000);
        cpu.stepInstruction();
        assertEquals(0x5634, cpu.pc());
    }

    @Test
    void branchAddsTakenAndPageCrossCycles() {
        ArrayByteBus bus = new ArrayByteBus();
        bus.poke(0x80fd, 0xd0); // BNE +2; PC after operand = 80ff, destination=8101
        bus.poke(0x80fe, 0x02);
        Mos6502 cpu = new Mos6502(bus);
        cpu.setPc(0x80fd);
        cpu.setStatus(0); // Z clear
        assertEquals(4, cpu.stepInstruction());
        assertEquals(0x8101, cpu.pc());
    }

    @Test
    void decimalAdcAndSbcHandleValidBcd() {
        ArrayByteBus bus = new ArrayByteBus();
        Mos6502 cpu = new Mos6502(bus);

        bus.poke(0x2000, 0x69); bus.poke(0x2001, 0x55); // ADC #55
        cpu.setPc(0x2000); cpu.setA(0x45); cpu.setStatus(Mos6502.FLAG_D);
        cpu.stepInstruction();
        assertEquals(0x00, cpu.a());
        assertTrue(cpu.carry());

        bus.poke(0x2010, 0xe9); bus.poke(0x2011, 0x01); // SBC #01
        cpu.setPc(0x2010); cpu.setA(0x00);
        cpu.setStatus(Mos6502.FLAG_D | Mos6502.FLAG_C);
        cpu.stepInstruction();
        assertEquals(0x99, cpu.a());
        assertFalse(cpu.carry());
    }

    @Test
    void nmiIsEdgeLatchedAndIrqHonoursIFlag() {
        ArrayByteBus bus = new ArrayByteBus();
        bus.poke(0xfffa, 0x00); bus.poke(0xfffb, 0x90);
        bus.poke(0xfffe, 0x00); bus.poke(0xffff, 0xa0);
        Mos6502 cpu = new Mos6502(bus);
        cpu.setPc(0x1234);
        cpu.setNmiAsserted(true);
        assertEquals(7, cpu.stepInstruction());
        assertEquals(0x9000, cpu.pc());

        cpu.setPc(0x2345);
        cpu.setStatus(Mos6502.FLAG_I);
        cpu.setIrqAsserted(true);
        bus.poke(0x2345, 0xea);
        assertEquals(2, cpu.stepInstruction());
        assertEquals(0x2346, cpu.pc());

        cpu.setStatus(0);
        assertEquals(7, cpu.stepInstruction());
        assertEquals(0xa000, cpu.pc());
    }

    @Test
    void rejectsUndocumentedOpcodeInDocumentedModel() {
        ArrayByteBus bus = new ArrayByteBus();
        bus.poke(0x4000, 0x02);
        Mos6502 cpu = new Mos6502(bus);
        cpu.setPc(0x4000);
        assertThrows(IllegalOpcodeException.class, cpu::stepInstruction);
    }
}
