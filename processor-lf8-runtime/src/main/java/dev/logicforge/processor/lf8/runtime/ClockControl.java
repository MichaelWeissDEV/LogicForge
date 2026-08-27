package dev.logicforge.processor.lf8.runtime;

import dev.logicforge.compiler.CompilationResult;
import dev.logicforge.compiler.RuntimeInstancePath;
import dev.logicforge.logic.LogicState;
import dev.logicforge.simulation.InputSourceState;
import dev.logicforge.simulation.Simulation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Controls one unambiguous concrete clock without confusing scheduled clocks with switches. */
public final class ClockControl {

    private static final int MAX_SCHEDULED_ADVANCES = 1_000_000;

    public enum Mode {
        MANUAL_DRIVE,
        SCHEDULED_CLOCK
    }

    private final Simulation simulation;
    private final int clockNet;
    private final int sourceComponent;
    private final Mode mode;

    private ClockControl(Simulation simulation, int clockNet, int sourceComponent, Mode mode) {
        this.simulation = simulation;
        this.clockNet = clockNet;
        this.sourceComponent = sourceComponent;
        this.mode = mode;
    }

    /** Returns empty when the CPU clock is absent, multiply driven, or not controllable. */
    public static Optional<ClockControl> discover(CompilationResult compilation,
                                                  Simulation simulation,
                                                  RuntimeInstancePath cpuPath) {
        Objects.requireNonNull(compilation, "compilation");
        Objects.requireNonNull(simulation, "simulation");
        Objects.requireNonNull(cpuPath, "cpuPath");
        var net = compilation.hierarchySourceMap().netId(cpuPath + ".CLK");
        if (net.isEmpty()) {
            return Optional.empty();
        }
        List<Integer> components = new ArrayList<>();
        for (int driver : compilation.circuit().net(net.getAsInt()).drivers()) {
            int component = compilation.circuit().driver(driver).componentId();
            if (!components.contains(component)) {
                components.add(component);
            }
        }
        if (components.size() != 1) {
            return Optional.empty();
        }
        int component = components.getFirst();
        String definition = compilation.circuit().component(component).definitionId();
        if ("source.clock".equals(definition)) {
            return Optional.of(new ClockControl(simulation, net.getAsInt(), component,
                    Mode.SCHEDULED_CLOCK));
        }
        if (simulation.stateOf(component) instanceof InputSourceState) {
            return Optional.of(new ClockControl(simulation, net.getAsInt(), component,
                    Mode.MANUAL_DRIVE));
        }
        return Optional.empty();
    }

    public Mode mode() {
        return mode;
    }

    /** Advances exactly through the next definite rising edge of the selected clock net. */
    public boolean stepActiveEdge() {
        if (mode == Mode.MANUAL_DRIVE) {
            simulation.setInput(sourceComponent, LogicState.ZERO);
            simulation.runUntilStableAtCurrentTime();
            simulation.setInput(sourceComponent, LogicState.ONE);
            simulation.runUntilStableAtCurrentTime();
            return true;
        }
        LogicState previous = simulation.readNet(clockNet).singleBit();
        for (int advances = 0; advances < MAX_SCHEDULED_ADVANCES; advances++) {
            if (!simulation.advanceToNextEvent()) {
                return false;
            }
            LogicState current = simulation.readNet(clockNet).singleBit();
            if (previous == LogicState.ZERO && current == LogicState.ONE) {
                return true;
            }
            previous = current;
        }
        throw new IllegalStateException("Scheduled clock did not produce a rising edge");
    }
}
