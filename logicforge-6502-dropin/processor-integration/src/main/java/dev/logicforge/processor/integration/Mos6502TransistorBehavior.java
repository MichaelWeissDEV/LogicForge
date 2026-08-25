package dev.logicforge.processor.integration;

import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.processor.mos6502.transistor.Mos6502TransistorChip;
import dev.logicforge.simulation.ComponentBehavior;
import dev.logicforge.simulation.ComponentContext;
import dev.logicforge.simulation.ComponentRuntimeState;
import dev.logicforge.transistor.TransistorNetlist;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Thin adapter from LogicForge's existing headless ComponentBehavior contract to the
 * transistor-level MOS 6502. No JavaFX/UI dependency is introduced.
 *
 * <p>The behavior requires the port order documented by {@link Mos6502PortContract}.
 * Undefined control inputs conservatively produce X on control/address outputs and Z on
 * the CPU's data driver instead of inventing a transistor input voltage.</p>
 */
public final class Mos6502TransistorBehavior implements ComponentBehavior {
    private final TransistorNetlist netlist;

    public Mos6502TransistorBehavior(TransistorNetlist netlist) {
        this.netlist = Objects.requireNonNull(netlist, "netlist");
    }

    @Override
    public ComponentRuntimeState createState() {
        return new State(new Mos6502TransistorChip(netlist));
    }

    @Override
    public void evaluate(ComponentContext context) {
        requireShape(context);
        State state = (State) context.state();
        Mos6502TransistorChip chip = state.chip;

        LogicState clk = bit(context, Mos6502PortContract.IN_CLK);
        LogicState resetN = bit(context, Mos6502PortContract.IN_RESET_N);
        LogicState irqN = bit(context, Mos6502PortContract.IN_IRQ_N);
        LogicState nmiN = bit(context, Mos6502PortContract.IN_NMI_N);
        LogicState rdy = bit(context, Mos6502PortContract.IN_RDY);
        LogicState soN = bit(context, Mos6502PortContract.IN_SO_N);

        if (!defined(clk, resetN, irqN, nmiN, rdy, soN)) {
            driveUnknownOutputs(context);
            return;
        }

        chip.setResetAsserted(resetN == LogicState.ZERO);
        chip.setIrqAsserted(irqN == LogicState.ZERO);
        chip.setNmiAsserted(nmiN == LogicState.ZERO);
        chip.setReady(rdy == LogicState.ONE);
        chip.setSoAsserted(soN == LogicState.ZERO);

        // Avoid an external-memory drive during a possible write-direction transition.
        chip.releaseDataBus();
        chip.setClock(clk == LogicState.ONE);

        LogicVector incomingData = context.readInput(Mos6502PortContract.IN_DATA).requireWidth(8);
        if (chip.readCycle()) {
            OptionalLong value = incomingData.toUnsignedLong();
            if (value.isPresent()) {
                chip.driveDataBus((int) value.getAsLong());
            } else {
                chip.releaseDataBus();
            }
        }

        context.driveOutput(Mos6502PortContract.OUT_DATA,
                chip.readCycle()
                        ? LogicVector.repeat(LogicState.HIGH_IMPEDANCE, 8)
                        : LogicVector.fromUnsignedLong(chip.dataBus(), 8));
        context.driveOutput(Mos6502PortContract.OUT_ADDRESS,
                LogicVector.fromUnsignedLong(chip.addressBus(), 16));
        context.driveOutput(Mos6502PortContract.OUT_RW, LogicVector.single(LogicState.of(chip.readCycle())));
        context.driveOutput(Mos6502PortContract.OUT_SYNC, LogicVector.single(LogicState.of(chip.sync())));
        context.driveOutput(Mos6502PortContract.OUT_PHI1, LogicVector.single(LogicState.of(chip.phi1Out())));
        context.driveOutput(Mos6502PortContract.OUT_PHI2, LogicVector.single(LogicState.of(chip.phi2Out())));
    }

    private static LogicState bit(ComponentContext context, int input) {
        return context.readInput(input).requireWidth(1).singleBit();
    }

    private static boolean defined(LogicState... states) {
        for (LogicState state : states) {
            if (!state.isDefined()) {
                return false;
            }
        }
        return true;
    }

    private static void driveUnknownOutputs(ComponentContext context) {
        context.driveOutput(Mos6502PortContract.OUT_DATA,
                LogicVector.repeat(LogicState.HIGH_IMPEDANCE, 8));
        context.driveOutput(Mos6502PortContract.OUT_ADDRESS,
                LogicVector.repeat(LogicState.UNKNOWN, 16));
        context.driveOutput(Mos6502PortContract.OUT_RW, LogicVector.UNKNOWN);
        context.driveOutput(Mos6502PortContract.OUT_SYNC, LogicVector.UNKNOWN);
        context.driveOutput(Mos6502PortContract.OUT_PHI1, LogicVector.UNKNOWN);
        context.driveOutput(Mos6502PortContract.OUT_PHI2, LogicVector.UNKNOWN);
    }

    private static void requireShape(ComponentContext context) {
        if (context.inputCount() != Mos6502PortContract.INPUT_COUNT
                || context.outputCount() != Mos6502PortContract.OUTPUT_COUNT) {
            throw new IllegalStateException("MOS6502 behavior expects "
                    + Mos6502PortContract.INPUT_COUNT + " inputs and "
                    + Mos6502PortContract.OUTPUT_COUNT + " outputs");
        }
    }

    public static final class State implements ComponentRuntimeState {
        private final Mos6502TransistorChip chip;

        private State(Mos6502TransistorChip chip) {
            this.chip = chip;
        }

        @Override
        public void reset() {
            chip.resetNetwork();
        }

        public Mos6502TransistorChip chip() {
            return chip;
        }
    }
}
