package dev.logicforge.library.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.CompiledCircuit;
import dev.logicforge.simulation.Simulation;
import org.junit.jupiter.api.Test;

class MemoryBehaviorTest {

    private static final BitWidth ADDR4 = BitWidth.of(4);
    private static final BitWidth DATA8 = BitWidth.of(8);
    private static final int[] NONE = new int[0];

    @Test
    void romReturnsTheContentAtAnAddress() {
        LogicVector[] contents = new LogicVector[16];
        java.util.Arrays.fill(contents, LogicVector.fromUnsignedLong(0, 8));
        contents[5] = LogicVector.fromUnsignedLong(0x42, 8);

        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int address = builder.addNet(ADDR4);
        int enable = builder.addNet(BitWidth.ONE);
        int data = builder.addNet(DATA8);
        int addrSrc = builder.addComponent("test.addr", "ADDR", new BusSource(ADDR4), NONE, new int[]{address});
        int enableSrc = builder.addComponent("test.en", "EN", new BusSource(BitWidth.ONE), NONE, new int[]{enable});
        builder.addComponent("memory.rom", "ROM", new RomBehavior(DATA8, contents),
                new int[]{address, enable}, new int[]{data});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(enableSrc, LogicVector.ONE);
        simulation.setInput(addrSrc, LogicVector.fromUnsignedLong(5, 4));

        assertEquals(LogicVector.fromUnsignedLong(0x42, 8), simulation.readNet(data));
    }

    @Test
    void romFloatsWhenDisabled() {
        LogicVector[] contents = new LogicVector[16];
        java.util.Arrays.fill(contents, LogicVector.fromUnsignedLong(0xFF, 8));

        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int address = builder.addNet(ADDR4);
        int enable = builder.addNet(BitWidth.ONE);
        int data = builder.addNet(DATA8);
        builder.addComponent("test.addr", "ADDR", new BusSource(ADDR4), NONE, new int[]{address});
        builder.addComponent("test.en", "EN", new BusSource(BitWidth.ONE), NONE, new int[]{enable});
        builder.addComponent("memory.rom", "ROM", new RomBehavior(DATA8, contents),
                new int[]{address, enable}, new int[]{data});
        Simulation simulation = new Simulation(builder.build());

        assertEquals(LogicVector.repeat(dev.logicforge.logic.LogicState.HIGH_IMPEDANCE, 8), simulation.readNet(data));
    }

    @Test
    void ramWritesThenReadsBackTheSameAddress() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int address = builder.addNet(ADDR4);
        int we = builder.addNet(BitWidth.ONE);
        int oe = builder.addNet(BitWidth.ONE);
        int cs = builder.addNet(BitWidth.ONE);
        int data = builder.addNet(DATA8);
        int addrSrc = builder.addComponent("test.addr", "ADDR", new BusSource(ADDR4), NONE, new int[]{address});
        int weSrc = builder.addComponent("test.we", "WE", new BusSource(BitWidth.ONE), NONE, new int[]{we});
        int oeSrc = builder.addComponent("test.oe", "OE", new BusSource(BitWidth.ONE), NONE, new int[]{oe});
        int csSrc = builder.addComponent("test.cs", "CS", new BusSource(BitWidth.ONE), NONE, new int[]{cs});
        int dataSrc = builder.addComponent("test.data", "DATA", new BusSource(DATA8), NONE, new int[]{data});
        builder.addComponent("memory.ram", "RAM", new RamBehavior(ADDR4, DATA8),
                new int[]{address, we, oe, cs, data}, new int[]{data});
        Simulation simulation = new Simulation(builder.build());

        // Write 0x42 at address 0x1.
        simulation.setInput(csSrc, LogicVector.ONE);
        simulation.setInput(addrSrc, LogicVector.fromUnsignedLong(1, 4));
        simulation.setInput(dataSrc, LogicVector.fromUnsignedLong(0x42, 8));
        simulation.setInput(weSrc, LogicVector.ONE);

        // Stop driving DATA externally and switch to read mode.
        simulation.setInput(weSrc, LogicVector.ZERO);
        simulation.setInput(dataSrc, LogicVector.repeat(dev.logicforge.logic.LogicState.HIGH_IMPEDANCE, 8));
        simulation.setInput(oeSrc, LogicVector.ONE);

        assertEquals(LogicVector.fromUnsignedLong(0x42, 8), simulation.readNet(data));
    }

    @Test
    void ramFloatsWhenChipSelectIsInactive() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int address = builder.addNet(ADDR4);
        int we = builder.addNet(BitWidth.ONE);
        int oe = builder.addNet(BitWidth.ONE);
        int cs = builder.addNet(BitWidth.ONE);
        int data = builder.addNet(DATA8);
        builder.addComponent("test.addr", "ADDR", new BusSource(ADDR4), NONE, new int[]{address});
        builder.addComponent("test.we", "WE", new BusSource(BitWidth.ONE), NONE, new int[]{we});
        int oeSrc = builder.addComponent("test.oe", "OE", new BusSource(BitWidth.ONE), NONE, new int[]{oe});
        builder.addComponent("test.cs", "CS", new BusSource(BitWidth.ONE), NONE, new int[]{cs});
        builder.addComponent("memory.ram", "RAM", new RamBehavior(ADDR4, DATA8),
                new int[]{address, we, oe, cs, data}, new int[]{data});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(oeSrc, LogicVector.ONE);

        assertEquals(LogicVector.repeat(dev.logicforge.logic.LogicState.HIGH_IMPEDANCE, 8), simulation.readNet(data));
    }

    @Test
    void ramDrivesXWhenCsIsUnknown() {
        // CS=X, WE=0, OE=1 -> ambiguous (RAM might be driving), output must be X not Z
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int address = builder.addNet(ADDR4);
        int we = builder.addNet(BitWidth.ONE);
        int oe = builder.addNet(BitWidth.ONE);
        int cs = builder.addNet(BitWidth.ONE);
        int data = builder.addNet(DATA8);
        builder.addComponent("test.addr", "ADDR", new BusSource(ADDR4), NONE, new int[]{address});
        int weSrc = builder.addComponent("test.we", "WE", new BusSource(BitWidth.ONE), NONE, new int[]{we});
        int oeSrc = builder.addComponent("test.oe", "OE", new BusSource(BitWidth.ONE), NONE, new int[]{oe});
        // cs net has no driver → floats as Z, which asGateInput maps to X
        builder.addComponent("memory.ram", "RAM", new RamBehavior(ADDR4, DATA8),
                new int[]{address, we, oe, cs, data}, new int[]{data});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(weSrc, LogicVector.ZERO);
        simulation.setInput(oeSrc, LogicVector.ONE);

        assertEquals(LogicVector.repeat(dev.logicforge.logic.LogicState.UNKNOWN, 8), simulation.readNet(data));
    }

    @Test
    void ramDrivesZWhenCsIsDefinitelyZero() {
        // CS=0 -> definitely disabled, DATA must float (Z)
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int address = builder.addNet(ADDR4);
        int we = builder.addNet(BitWidth.ONE);
        int oe = builder.addNet(BitWidth.ONE);
        int cs = builder.addNet(BitWidth.ONE);
        int data = builder.addNet(DATA8);
        builder.addComponent("test.addr", "ADDR", new BusSource(ADDR4), NONE, new int[]{address});
        int weSrc = builder.addComponent("test.we", "WE", new BusSource(BitWidth.ONE), NONE, new int[]{we});
        int oeSrc = builder.addComponent("test.oe", "OE", new BusSource(BitWidth.ONE), NONE, new int[]{oe});
        int csSrc = builder.addComponent("test.cs", "CS", new BusSource(BitWidth.ONE), NONE, new int[]{cs});
        builder.addComponent("memory.ram", "RAM", new RamBehavior(ADDR4, DATA8),
                new int[]{address, we, oe, cs, data}, new int[]{data});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(csSrc, LogicVector.ZERO);
        simulation.setInput(weSrc, LogicVector.ZERO);
        simulation.setInput(oeSrc, LogicVector.ONE);

        assertEquals(LogicVector.repeat(dev.logicforge.logic.LogicState.HIGH_IMPEDANCE, 8), simulation.readNet(data));
    }

    @Test
    void ramDrivesXWhenWeIsAmbiguous() {
        // CS=1, WE=X, OE=1 -> could be a read or a write -> DATA must be X
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int address = builder.addNet(ADDR4);
        int we = builder.addNet(BitWidth.ONE);
        int oe = builder.addNet(BitWidth.ONE);
        int cs = builder.addNet(BitWidth.ONE);
        int data = builder.addNet(DATA8);
        builder.addComponent("test.addr", "ADDR", new BusSource(ADDR4), NONE, new int[]{address});
        // we net has no driver → floats as Z, which asGateInput maps to X
        int oeSrc = builder.addComponent("test.oe", "OE", new BusSource(BitWidth.ONE), NONE, new int[]{oe});
        int csSrc = builder.addComponent("test.cs", "CS", new BusSource(BitWidth.ONE), NONE, new int[]{cs});
        builder.addComponent("memory.ram", "RAM", new RamBehavior(ADDR4, DATA8),
                new int[]{address, we, oe, cs, data}, new int[]{data});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(csSrc, LogicVector.ONE);
        simulation.setInput(oeSrc, LogicVector.ONE);

        assertEquals(LogicVector.repeat(dev.logicforge.logic.LogicState.UNKNOWN, 8), simulation.readNet(data));
    }

    @Test
    void ramDoesNotWriteWhenAddressIsUnknown() {
        // Write with address=X should not corrupt memory; a known address should read back intact.
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int address = builder.addNet(ADDR4);
        int we = builder.addNet(BitWidth.ONE);
        int oe = builder.addNet(BitWidth.ONE);
        int cs = builder.addNet(BitWidth.ONE);
        int data = builder.addNet(DATA8);
        int addrSrc = builder.addComponent("test.addr", "ADDR", new BusSource(ADDR4), NONE, new int[]{address});
        int weSrc = builder.addComponent("test.we", "WE", new BusSource(BitWidth.ONE), NONE, new int[]{we});
        int oeSrc = builder.addComponent("test.oe", "OE", new BusSource(BitWidth.ONE), NONE, new int[]{oe});
        int csSrc = builder.addComponent("test.cs", "CS", new BusSource(BitWidth.ONE), NONE, new int[]{cs});
        int dataSrc = builder.addComponent("test.data", "DATA", new BusSource(DATA8), NONE, new int[]{data});
        builder.addComponent("memory.ram", "RAM", new RamBehavior(ADDR4, DATA8),
                new int[]{address, we, oe, cs, data}, new int[]{data});
        Simulation simulation = new Simulation(builder.build());

        // First write a known value to address 0x3
        simulation.setInput(csSrc, LogicVector.ONE);
        simulation.setInput(addrSrc, LogicVector.fromUnsignedLong(3, 4));
        simulation.setInput(dataSrc, LogicVector.fromUnsignedLong(0xAB, 8));
        simulation.setInput(weSrc, LogicVector.ONE);

        // Attempt write with unknown (X) address — should be a no-op
        simulation.setInput(addrSrc, LogicVector.repeat(dev.logicforge.logic.LogicState.UNKNOWN, 4));
        simulation.setInput(dataSrc, LogicVector.fromUnsignedLong(0xFF, 8));

        // Switch to read mode with the known good address — value must be undamaged
        simulation.setInput(weSrc, LogicVector.ZERO);
        simulation.setInput(dataSrc, LogicVector.repeat(dev.logicforge.logic.LogicState.HIGH_IMPEDANCE, 8));
        simulation.setInput(oeSrc, LogicVector.ONE);
        simulation.setInput(addrSrc, LogicVector.fromUnsignedLong(3, 4));

        assertEquals(LogicVector.fromUnsignedLong(0xAB, 8), simulation.readNet(data));
    }
}
