package dev.logicforge.processor;

import java.util.Arrays;

/** Simple 64 KiB memory bus used by unit tests and headless experiments. */
public final class ArrayByteBus implements ByteBus {
    private final byte[] memory = new byte[0x10000];

    @Override
    public int read(int address) {
        return Byte.toUnsignedInt(memory[address & 0xffff]);
    }

    @Override
    public void write(int address, int value) {
        memory[address & 0xffff] = (byte) value;
    }

    public void load(int address, byte[] data) {
        int start = address & 0xffff;
        if (start + data.length > memory.length) {
            throw new IllegalArgumentException("Image crosses end of 64 KiB address space");
        }
        System.arraycopy(data, 0, memory, start, data.length);
    }

    public void poke(int address, int value) {
        write(address, value);
    }

    public int peek(int address) {
        return read(address);
    }

    public void clear() {
        Arrays.fill(memory, (byte) 0);
    }
}
