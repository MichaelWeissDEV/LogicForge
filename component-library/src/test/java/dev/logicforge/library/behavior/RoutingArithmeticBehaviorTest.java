package dev.logicforge.library.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.logicforge.logic.BitWidth;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.CompiledCircuit;
import dev.logicforge.simulation.Simulation;
import org.junit.jupiter.api.Test;

class RoutingArithmeticBehaviorTest {

    private static final BitWidth WIDTH8 = BitWidth.of(8);
    private static final int[] NONE = new int[0];

    @Test
    void muxSelectsTheChosenInput() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int in0 = builder.addNet(WIDTH8);
        int in1 = builder.addNet(WIDTH8);
        int in2 = builder.addNet(WIDTH8);
        int in3 = builder.addNet(WIDTH8);
        int sel = builder.addNet(BitWidth.of(2));
        int out = builder.addNet(WIDTH8);
        builder.addComponent("test.in0", "I0", new BusSource(WIDTH8), NONE, new int[]{in0});
        builder.addComponent("test.in1", "I1", new BusSource(WIDTH8), NONE, new int[]{in1});
        int in2Src = builder.addComponent("test.in2", "I2", new BusSource(WIDTH8), NONE, new int[]{in2});
        builder.addComponent("test.in3", "I3", new BusSource(WIDTH8), NONE, new int[]{in3});
        int selSrc = builder.addComponent("test.sel", "SEL", new BusSource(BitWidth.of(2)), NONE, new int[]{sel});
        builder.addComponent("routing.mux", "MUX", new MuxBehavior(WIDTH8, 4),
                new int[]{in0, in1, in2, in3, sel}, new int[]{out});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(in2Src, LogicVector.fromUnsignedLong(0x77, 8));
        simulation.setInput(selSrc, LogicVector.fromUnsignedLong(2, 2));

        assertEquals(LogicVector.fromUnsignedLong(0x77, 8), simulation.readNet(out), "IN2 selected by SEL=2");
    }

    @Test
    void adderComputesSumAndCarry() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int a = builder.addNet(WIDTH8);
        int b = builder.addNet(WIDTH8);
        int cin = builder.addNet(BitWidth.ONE);
        int sum = builder.addNet(WIDTH8);
        int cout = builder.addNet(BitWidth.ONE);
        int aSrc = builder.addComponent("test.a", "A", new BusSource(WIDTH8), NONE, new int[]{a});
        int bSrc = builder.addComponent("test.b", "B", new BusSource(WIDTH8), NONE, new int[]{b});
        builder.addComponent("test.cin", "CIN", new BusSource(BitWidth.ONE), NONE, new int[]{cin});
        builder.addComponent("arithmetic.adder", "ADD", new AdderBehavior(WIDTH8),
                new int[]{a, b, cin}, new int[]{sum, cout});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(aSrc, LogicVector.fromUnsignedLong(0x10, 8));
        simulation.setInput(bSrc, LogicVector.fromUnsignedLong(0x22, 8));

        assertEquals(LogicVector.fromUnsignedLong(0x32, 8), simulation.readNet(sum));
        assertEquals(LogicVector.ZERO, simulation.readNet(cout));
    }

    @Test
    void adderCarriesOutOnOverflow() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int a = builder.addNet(WIDTH8);
        int b = builder.addNet(WIDTH8);
        int cin = builder.addNet(BitWidth.ONE);
        int sum = builder.addNet(WIDTH8);
        int cout = builder.addNet(BitWidth.ONE);
        int aSrc = builder.addComponent("test.a", "A", new BusSource(WIDTH8), NONE, new int[]{a});
        int bSrc = builder.addComponent("test.b", "B", new BusSource(WIDTH8), NONE, new int[]{b});
        builder.addComponent("test.cin", "CIN", new BusSource(BitWidth.ONE), NONE, new int[]{cin});
        builder.addComponent("arithmetic.adder", "ADD", new AdderBehavior(WIDTH8),
                new int[]{a, b, cin}, new int[]{sum, cout});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(aSrc, LogicVector.fromUnsignedLong(0xFF, 8));
        simulation.setInput(bSrc, LogicVector.fromUnsignedLong(0x01, 8));

        assertEquals(LogicVector.fromUnsignedLong(0x00, 8), simulation.readNet(sum));
        assertEquals(LogicVector.ONE, simulation.readNet(cout));
    }

    @Test
    void splitterSplitsLsbFirst() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        BitWidth width4 = BitWidth.of(4);
        int bus = builder.addNet(width4);
        int[] bits = new int[4];
        for (int i = 0; i < 4; i++) {
            bits[i] = builder.addNet(BitWidth.ONE);
        }
        int busSrc = builder.addComponent("test.bus", "BUS", new BusSource(width4), NONE, new int[]{bus});
        builder.addComponent("routing.splitter", "SPLIT", new SplitterBehavior(width4), new int[]{bus}, bits);
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(busSrc, LogicVector.of("1010"));

        assertEquals(LogicVector.ZERO, simulation.readNet(bits[0]), "bit0");
        assertEquals(LogicVector.ONE, simulation.readNet(bits[1]), "bit1");
        assertEquals(LogicVector.ZERO, simulation.readNet(bits[2]), "bit2");
        assertEquals(LogicVector.ONE, simulation.readNet(bits[3]), "bit3");
    }

    @Test
    void joinerAssemblesLsbFirst() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        BitWidth width4 = BitWidth.of(4);
        int[] bitNets = new int[4];
        int[] bitSrcs = new int[4];
        for (int i = 0; i < 4; i++) {
            bitNets[i] = builder.addNet(BitWidth.ONE);
            bitSrcs[i] = builder.addComponent("test.bit" + i, "B" + i, new BusSource(BitWidth.ONE), NONE,
                    new int[]{bitNets[i]});
        }
        int bus = builder.addNet(width4);
        builder.addComponent("routing.joiner", "JOIN", new JoinerBehavior(width4), bitNets, new int[]{bus});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(bitSrcs[1], LogicVector.ONE);
        simulation.setInput(bitSrcs[3], LogicVector.ONE);

        assertEquals(LogicVector.of("1010"), simulation.readNet(bus));
    }

    @Test
    void comparatorReportsLessEqualGreater() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int a = builder.addNet(WIDTH8);
        int b = builder.addNet(WIDTH8);
        int lt = builder.addNet(BitWidth.ONE);
        int eq = builder.addNet(BitWidth.ONE);
        int gt = builder.addNet(BitWidth.ONE);
        int aSrc = builder.addComponent("test.a", "A", new BusSource(WIDTH8), NONE, new int[]{a});
        int bSrc = builder.addComponent("test.b", "B", new BusSource(WIDTH8), NONE, new int[]{b});
        builder.addComponent("routing.comparator", "CMP", ComparatorBehavior.INSTANCE,
                new int[]{a, b}, new int[]{lt, eq, gt});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(aSrc, LogicVector.fromUnsignedLong(5, 8));
        simulation.setInput(bSrc, LogicVector.fromUnsignedLong(9, 8));
        assertEquals(LogicVector.ONE, simulation.readNet(lt));
        assertEquals(LogicVector.ZERO, simulation.readNet(eq));
        assertEquals(LogicVector.ZERO, simulation.readNet(gt));

        simulation.setInput(aSrc, LogicVector.fromUnsignedLong(9, 8));
        assertEquals(LogicVector.ZERO, simulation.readNet(lt));
        assertEquals(LogicVector.ONE, simulation.readNet(eq));
        assertEquals(LogicVector.ZERO, simulation.readNet(gt));
    }

    @Test
    void signedComparatorReadsOperandsAsTwosComplement() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int a = builder.addNet(WIDTH8);
        int b = builder.addNet(WIDTH8);
        int lt = builder.addNet(BitWidth.ONE);
        int eq = builder.addNet(BitWidth.ONE);
        int gt = builder.addNet(BitWidth.ONE);
        int aSrc = builder.addComponent("test.a", "A", new BusSource(WIDTH8), NONE, new int[]{a});
        int bSrc = builder.addComponent("test.b", "B", new BusSource(WIDTH8), NONE, new int[]{b});
        builder.addComponent("routing.comparator_signed", "CMP", new SignedComparatorBehavior(WIDTH8),
                new int[]{a, b}, new int[]{lt, eq, gt});
        Simulation simulation = new Simulation(builder.build());

        // 0xFF is unsigned 255 but signed -1; unsigned it would read greater than 5.
        simulation.setInput(aSrc, LogicVector.fromUnsignedLong(0xFF, 8));
        simulation.setInput(bSrc, LogicVector.fromUnsignedLong(5, 8));
        assertEquals(LogicVector.ONE, simulation.readNet(lt), "-1 < 5 when read as signed");
        assertEquals(LogicVector.ZERO, simulation.readNet(gt));
    }

    @Test
    void zeroDetectorFindsAllZeroBits() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int a = builder.addNet(WIDTH8);
        int zero = builder.addNet(BitWidth.ONE);
        int aSrc = builder.addComponent("test.a", "A", new BusSource(WIDTH8), NONE, new int[]{a});
        builder.addComponent("arithmetic.zero_detector", "Z", new ZeroDetectorBehavior(WIDTH8),
                new int[]{a}, new int[]{zero});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(aSrc, LogicVector.fromUnsignedLong(0, 8));
        assertEquals(LogicVector.ONE, simulation.readNet(zero));

        simulation.setInput(aSrc, LogicVector.fromUnsignedLong(1, 8));
        assertEquals(LogicVector.ZERO, simulation.readNet(zero));
    }

    @Test
    void signDetectorReadsTheMostSignificantBit() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int a = builder.addNet(WIDTH8);
        int negative = builder.addNet(BitWidth.ONE);
        int aSrc = builder.addComponent("test.a", "A", new BusSource(WIDTH8), NONE, new int[]{a});
        builder.addComponent("arithmetic.sign_detector", "N", new SignDetectorBehavior(WIDTH8),
                new int[]{a}, new int[]{negative});
        Simulation simulation = new Simulation(builder.build());

        simulation.setInput(aSrc, LogicVector.fromUnsignedLong(0x80, 8));
        assertEquals(LogicVector.ONE, simulation.readNet(negative));

        simulation.setInput(aSrc, LogicVector.fromUnsignedLong(0x7F, 8));
        assertEquals(LogicVector.ZERO, simulation.readNet(negative));
    }

    @Test
    void overflowDetectorFlagsSignedAdditionOverflow() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int a = builder.addNet(WIDTH8);
        int b = builder.addNet(WIDTH8);
        int result = builder.addNet(WIDTH8);
        int overflow = builder.addNet(BitWidth.ONE);
        int aSrc = builder.addComponent("test.a", "A", new BusSource(WIDTH8), NONE, new int[]{a});
        int bSrc = builder.addComponent("test.b", "B", new BusSource(WIDTH8), NONE, new int[]{b});
        int resultSrc = builder.addComponent("test.result", "RESULT", new BusSource(WIDTH8), NONE, new int[]{result});
        builder.addComponent("arithmetic.overflow_detector", "V", new OverflowDetectorBehavior(WIDTH8, false),
                new int[]{a, b, result}, new int[]{overflow});
        Simulation simulation = new Simulation(builder.build());

        // 0x7F (+127) + 0x01 (+1) = 0x80 (-128 signed): two positives producing a negative.
        simulation.setInput(aSrc, LogicVector.fromUnsignedLong(0x7F, 8));
        simulation.setInput(bSrc, LogicVector.fromUnsignedLong(0x01, 8));
        simulation.setInput(resultSrc, LogicVector.fromUnsignedLong(0x80, 8));
        assertEquals(LogicVector.ONE, simulation.readNet(overflow));

        // 0x01 + 0x01 = 0x02: no overflow.
        simulation.setInput(aSrc, LogicVector.fromUnsignedLong(0x01, 8));
        simulation.setInput(resultSrc, LogicVector.fromUnsignedLong(0x02, 8));
        assertEquals(LogicVector.ZERO, simulation.readNet(overflow));
    }

    @Test
    void overflowDetectorFlagsSignedSubtractionOverflow() {
        CompiledCircuit.Builder builder = CompiledCircuit.builder();
        int a = builder.addNet(WIDTH8);
        int b = builder.addNet(WIDTH8);
        int result = builder.addNet(WIDTH8);
        int overflow = builder.addNet(BitWidth.ONE);
        int aSrc = builder.addComponent("test.a", "A", new BusSource(WIDTH8), NONE, new int[]{a});
        int bSrc = builder.addComponent("test.b", "B", new BusSource(WIDTH8), NONE, new int[]{b});
        int resultSrc = builder.addComponent("test.result", "RESULT", new BusSource(WIDTH8), NONE, new int[]{result});
        builder.addComponent("arithmetic.overflow_detector", "V", new OverflowDetectorBehavior(WIDTH8, true),
                new int[]{a, b, result}, new int[]{overflow});
        Simulation simulation = new Simulation(builder.build());

        // -128 (0x80) - 1 (0x01) = -129, wraps to 0x7F (+127 signed): overflow.
        simulation.setInput(aSrc, LogicVector.fromUnsignedLong(0x80, 8));
        simulation.setInput(bSrc, LogicVector.fromUnsignedLong(0x01, 8));
        simulation.setInput(resultSrc, LogicVector.fromUnsignedLong(0x7F, 8));
        assertEquals(LogicVector.ONE, simulation.readNet(overflow));
    }
}
