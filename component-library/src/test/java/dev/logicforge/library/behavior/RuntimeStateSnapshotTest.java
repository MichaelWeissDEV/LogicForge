package dev.logicforge.library.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.CompiledCircuit;
import dev.logicforge.simulation.ComponentRuntimeState;
import dev.logicforge.simulation.MemorySnapshot;
import dev.logicforge.simulation.Simulation;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link ComponentRuntimeState#snapshot()} and
 * {@link ComponentRuntimeState#restore(Object)} round-trip correctly for
 * {@link VectorRegisterState} and {@link RamState}, and that
 * {@link ComponentRuntimeState#memorySnapshot()} works for RAM.
 *
 * <p>Tests sit in the {@code dev.logicforge.library.behavior} package so they have
 * access to the package-private state classes and the {@link BusSource} test helper.
 */
class RuntimeStateSnapshotTest {

    private static final BitWidth WIDTH4 = BitWidth.of(4);
    private static final BitWidth WIDTH8 = BitWidth.of(8);
    private static final int[] NONE = new int[0];

    // ------------------------------------------------------------------
    // Helpers: build register / RAM circuits
    // ------------------------------------------------------------------

    private record RegisterCircuit(
            Simulation simulation,
            int dataSrc, int clkSrc, int loadSrc,
            int regComponent, int qNet) {}

    private RegisterCircuit buildRegister(BitWidth width) {
        CompiledCircuit.Builder b = CompiledCircuit.builder();
        int data = b.addNet(width);
        int clk  = b.addNet(BitWidth.ONE);
        int load = b.addNet(BitWidth.ONE);
        int q    = b.addNet(width);
        int dataSrc = b.addComponent("test.data", "DATA", new BusSource(width),   NONE, new int[]{data});
        int clkSrc  = b.addComponent("test.clk",  "CLK",  new BusSource(BitWidth.ONE), NONE, new int[]{clk});
        int loadSrc = b.addComponent("test.load", "LOAD", new BusSource(BitWidth.ONE), NONE, new int[]{load});
        int reg = b.addComponent("sequential.register", "REG",
                new RegisterBehavior(width, true, false),
                new int[]{data, clk, load}, new int[]{q});
        return new RegisterCircuit(new Simulation(b.build()), dataSrc, clkSrc, loadSrc, reg, q);
    }

    private record RamCircuit(
            Simulation simulation,
            int addrSrc, int weSrc, int oeSrc, int csSrc, int dataSrc,
            int ramComponent, int dataNet) {}

    private RamCircuit buildRam(BitWidth addrWidth, BitWidth dataWidth) {
        CompiledCircuit.Builder b = CompiledCircuit.builder();
        int address = b.addNet(addrWidth);
        int we      = b.addNet(BitWidth.ONE);
        int oe      = b.addNet(BitWidth.ONE);
        int cs      = b.addNet(BitWidth.ONE);
        int data    = b.addNet(dataWidth);
        int addrSrc = b.addComponent("test.addr", "ADDR", new BusSource(addrWidth), NONE, new int[]{address});
        int weSrc   = b.addComponent("test.we",   "WE",   new BusSource(BitWidth.ONE), NONE, new int[]{we});
        int oeSrc   = b.addComponent("test.oe",   "OE",   new BusSource(BitWidth.ONE), NONE, new int[]{oe});
        int csSrc   = b.addComponent("test.cs",   "CS",   new BusSource(BitWidth.ONE), NONE, new int[]{cs});
        int dataSrc = b.addComponent("test.data", "DATA", new BusSource(dataWidth), NONE, new int[]{data});
        int ram = b.addComponent("memory.ram", "RAM",
                new RamBehavior(addrWidth, dataWidth),
                new int[]{address, we, oe, cs, data}, new int[]{data});
        return new RamCircuit(new Simulation(b.build()), addrSrc, weSrc, oeSrc, csSrc, dataSrc, ram, data);
    }

    // ------------------------------------------------------------------
    // Register: snapshot/restore
    // ------------------------------------------------------------------

    @Test
    void registerSnapshotIsNonNull() {
        // VectorRegisterState always produces a snapshot (even the all-zero reset state).
        RegisterCircuit rc = buildRegister(WIDTH4);
        assertNotNull(rc.simulation().stateOf(rc.regComponent()).snapshot(),
                "VectorRegisterState must return a non-null snapshot");
    }

    @Test
    void registerSnapshotPreservesValue() {
        RegisterCircuit rc = buildRegister(WIDTH4);
        Simulation sim = rc.simulation();

        // Clock in 0b1010 (= 10)
        sim.setInput(rc.loadSrc(), LogicVector.ONE);
        sim.setInput(rc.dataSrc(), LogicVector.fromUnsignedLong(0b1010, 4));
        sim.setInput(rc.clkSrc(),  LogicVector.ONE);
        assertEquals(LogicVector.fromUnsignedLong(0b1010, 4), sim.readNet(rc.qNet()));

        // Snapshot then reset (clears to zero)
        Object snap = sim.stateOf(rc.regComponent()).snapshot();
        sim.reset();
        assertEquals(LogicVector.fromUnsignedLong(0, 4), sim.readNet(rc.qNet()), "reset to zero");

        // Restore into state object and verify the field directly (same package)
        sim.stateOf(rc.regComponent()).restore(snap);
        assertEquals(LogicVector.fromUnsignedLong(0b1010, 4),
                ((VectorRegisterState) sim.stateOf(rc.regComponent())).value,
                "restored value should be 0b1010");
    }

    @Test
    void registerRestoreIgnoresIncompatibleWidth() {
        // Take a 4-bit snapshot, try to restore it into an 8-bit register → silently ignored
        RegisterCircuit rc4 = buildRegister(WIDTH4);
        Simulation sim4 = rc4.simulation();
        sim4.setInput(rc4.loadSrc(), LogicVector.ONE);
        sim4.setInput(rc4.dataSrc(), LogicVector.fromUnsignedLong(0b1111, 4));
        sim4.setInput(rc4.clkSrc(),  LogicVector.ONE);
        Object snap4 = sim4.stateOf(rc4.regComponent()).snapshot();

        RegisterCircuit rc8 = buildRegister(WIDTH8);
        Simulation sim8 = rc8.simulation();
        sim8.stateOf(rc8.regComponent()).restore(snap4);

        assertEquals(LogicVector.fromUnsignedLong(0, 8),
                ((VectorRegisterState) sim8.stateOf(rc8.regComponent())).value,
                "width mismatch must not modify the 8-bit register");
    }

    // ------------------------------------------------------------------
    // RAM: snapshot/restore
    // ------------------------------------------------------------------

    @Test
    void ramSnapshotPreservesContents() {
        RamCircuit rc = buildRam(WIDTH4, WIDTH8);
        Simulation sim = rc.simulation();

        // Write 0xAB at address 3
        sim.setInput(rc.csSrc(),   LogicVector.ONE);
        sim.setInput(rc.addrSrc(), LogicVector.fromUnsignedLong(3, 4));
        sim.setInput(rc.dataSrc(), LogicVector.fromUnsignedLong(0xAB, 8));
        sim.setInput(rc.weSrc(),   LogicVector.ONE);
        sim.setInput(rc.weSrc(),   LogicVector.ZERO);

        Object snap = sim.stateOf(rc.ramComponent()).snapshot();

        sim.reset(); // wipes memory to zero

        sim.stateOf(rc.ramComponent()).restore(snap);
        MemorySnapshot ms = sim.stateOf(rc.ramComponent()).memorySnapshot();
        assertNotNull(ms, "RAM must provide a MemorySnapshot");
        assertEquals(16, ms.size(), "4-bit address => 16 words");
        assertEquals(LogicVector.fromUnsignedLong(0xAB, 8), ms.wordAt(3),
                "address 3 should contain 0xAB after restore");
        assertEquals(LogicVector.fromUnsignedLong(0, 8), ms.wordAt(0),
                "other addresses should be zero");
    }

    @Test
    void ramRestoreIgnoresIncompatibleWordCount() {
        // Snapshot from 16-word (4-bit address) RAM, restore into 4-word (2-bit) RAM → ignored
        RamCircuit rc16 = buildRam(WIDTH4, WIDTH8);
        Simulation sim16 = rc16.simulation();
        sim16.setInput(rc16.csSrc(),   LogicVector.ONE);
        sim16.setInput(rc16.addrSrc(), LogicVector.fromUnsignedLong(1, 4));
        sim16.setInput(rc16.dataSrc(), LogicVector.fromUnsignedLong(0xFF, 8));
        sim16.setInput(rc16.weSrc(),   LogicVector.ONE);
        sim16.setInput(rc16.weSrc(),   LogicVector.ZERO);
        Object snap = sim16.stateOf(rc16.ramComponent()).snapshot();

        RamCircuit rc4 = buildRam(BitWidth.of(2), WIDTH8);
        Simulation sim4 = rc4.simulation();
        sim4.stateOf(rc4.ramComponent()).restore(snap);

        MemorySnapshot ms = sim4.stateOf(rc4.ramComponent()).memorySnapshot();
        assertEquals(4, ms.size(), "2-bit address => 4 words");
        assertEquals(LogicVector.fromUnsignedLong(0, 8), ms.wordAt(0),
                "incompatible snapshot must not overwrite the 4-word RAM");
    }

    // ------------------------------------------------------------------
    // MemorySnapshot: defensive copy
    // ------------------------------------------------------------------

    @Test
    void memorySnapshotIsDefensivelyCopied() {
        RamCircuit rc = buildRam(WIDTH4, WIDTH8);
        Simulation sim = rc.simulation();

        sim.setInput(rc.csSrc(),   LogicVector.ONE);
        sim.setInput(rc.addrSrc(), LogicVector.fromUnsignedLong(0, 4));
        sim.setInput(rc.dataSrc(), LogicVector.fromUnsignedLong(0x55, 8));
        sim.setInput(rc.weSrc(),   LogicVector.ONE);
        sim.setInput(rc.weSrc(),   LogicVector.ZERO);

        MemorySnapshot ms = sim.stateOf(rc.ramComponent()).memorySnapshot();
        assertEquals(LogicVector.fromUnsignedLong(0x55, 8), ms.wordAt(0));

        // Mutate live RAM at address 0
        sim.setInput(rc.addrSrc(), LogicVector.fromUnsignedLong(0, 4));
        sim.setInput(rc.dataSrc(), LogicVector.fromUnsignedLong(0xAA, 8));
        sim.setInput(rc.weSrc(),   LogicVector.ONE);
        sim.setInput(rc.weSrc(),   LogicVector.ZERO);

        assertEquals(LogicVector.fromUnsignedLong(0x55, 8), ms.wordAt(0),
                "snapshot must be a defensive copy, not a live view");
    }

    // ------------------------------------------------------------------
    // STATELESS default implementations
    // ------------------------------------------------------------------

    @Test
    void statelessComponentHasNullSnapshot() {
        assertNull(ComponentRuntimeState.STATELESS.snapshot());
    }

    @Test
    void statelessComponentHasNullMemorySnapshot() {
        assertNull(ComponentRuntimeState.STATELESS.memorySnapshot());
    }

    // ------------------------------------------------------------------
    // ComponentDebugSnapshot: generic introspection the Inspector renders
    // without ever casting down to a specific behavior's state class.
    // ------------------------------------------------------------------

    @Test
    void registerDebugSnapshotExposesItsStoredValue() {
        RegisterCircuit rc = buildRegister(WIDTH4);
        Simulation sim = rc.simulation();
        sim.setInput(rc.loadSrc(), LogicVector.ONE);
        sim.setInput(rc.dataSrc(), LogicVector.fromUnsignedLong(0b1010, 4));
        sim.setInput(rc.clkSrc(), LogicVector.ONE);

        dev.logicforge.simulation.ComponentDebugSnapshot debug = sim.debugSnapshot(rc.regComponent());

        assertEquals(LogicVector.fromUnsignedLong(0b1010, 4), debug.namedValues().get("Stored"));
    }

    @Test
    void ramDebugSnapshotCarriesMemoryInfoWithoutCloningContents() {
        RamCircuit rc = buildRam(WIDTH4, WIDTH8);
        Simulation sim = rc.simulation();
        sim.setInput(rc.csSrc(), LogicVector.ONE);
        sim.setInput(rc.addrSrc(), LogicVector.fromUnsignedLong(3, 4));
        sim.setInput(rc.dataSrc(), LogicVector.fromUnsignedLong(0xAB, 8));
        sim.setInput(rc.weSrc(), LogicVector.ONE);
        sim.setInput(rc.weSrc(), LogicVector.ZERO);

        dev.logicforge.simulation.ComponentDebugSnapshot debug = sim.debugSnapshot(rc.ramComponent());

        // RamState.memoryInfo() is the cheap override; a compile error here would mean it
        // regressed back to deriving from the array-cloning memorySnapshot().
        dev.logicforge.simulation.MemoryInfo memory = debug.memory();
        assertNotNull(memory, "RAM must expose memory metadata");
        assertEquals(16, memory.size(), "4-bit address => 16 words");
        assertEquals(8, memory.wordWidth());
        assertEquals(3, memory.lastWriteAddress());
        assertEquals(LogicVector.fromUnsignedLong(0xAB, 8), memory.lastWrittenValue());
    }
}
