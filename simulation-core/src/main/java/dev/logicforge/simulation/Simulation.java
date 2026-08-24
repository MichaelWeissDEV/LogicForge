package dev.logicforge.simulation;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.TreeSet;

/**
 * The simulation engine: an event driven, delta-cycle simulator over a
 * {@link CompiledCircuit}.
 *
 * <p>How a change travels through the circuit:
 *
 * <pre>
 *   an input changes
 *        -&gt; the component drives a new value            (a queued event)
 *        -&gt; the net resolves its drivers
 *        -&gt; the net's consumers are evaluated
 *        -&gt; their outputs schedule the next delta cycle
 * </pre>
 *
 * <p>Only components attached to nets that actually changed are evaluated; nothing is
 * recomputed wholesale.
 *
 * <h2>Determinism</h2>
 *
 * Events are ordered by time, then delta cycle, then a strictly increasing sequence
 * number, and nets and components are always visited in ascending id order. No hash
 * iteration order and no threading is involved, so the same circuit with the same inputs
 * always produces exactly the same trace.
 *
 * <h2>Initial state</h2>
 *
 * After {@link #reset()} every net is {@code Z} and every component is evaluated once, so
 * constants and switches drive their values and the gates settle. A net nobody drives
 * stays {@code Z} and reads as {@code X} at any gate input — an unconnected input is never
 * silently treated as {@code 0}.
 */
public final class Simulation {

    /** Delta cycles allowed per stabilisation before a circuit counts as oscillating. */
    public static final int DEFAULT_MAX_DELTA_CYCLES = 1000;

    private final CompiledCircuit circuit;
    private final LogicVector[] netValues;
    private final LogicVector[] driverValues;
    private final ComponentRuntimeState[] states;
    private final PriorityQueue<SimulationEvent> queue = new PriorityQueue<>();
    private final List<SimulationObserver> observers = new ArrayList<>();
    private final List<Integer> lastChangedNets = new ArrayList<>();
    private final EvaluationContext context = new EvaluationContext();

    private int maxDeltaCycles = DEFAULT_MAX_DELTA_CYCLES;
    private long sequence;
    private long time;
    private int deltaCycle;
    private boolean running = true;
    private SimulationStatus status = SimulationStatus.STABLE;
    private SimulationOscillationException oscillation;

    public Simulation(CompiledCircuit circuit) {
        this.circuit = circuit;
        this.netValues = new LogicVector[circuit.netCount()];
        this.driverValues = new LogicVector[circuit.driverCount()];
        this.states = new ComponentRuntimeState[circuit.componentCount()];
        for (int id = 0; id < circuit.componentCount(); id++) {
            states[id] = circuit.component(id).behavior().createState();
        }
        reset();
    }

    public CompiledCircuit circuit() {
        return circuit;
    }

    // ------------------------------------------------------------------
    // Running the simulation
    // ------------------------------------------------------------------

    /**
     * Returns to the initial state: nets undriven, component state at its power-on value,
     * every component evaluated once. While the simulation is running this also settles
     * the circuit; while it is paused the resulting events wait for {@link #step()}.
     */
    public void reset() {
        queue.clear();
        lastChangedNets.clear();
        oscillation = null;
        sequence = 0;
        time = 0;
        deltaCycle = 0;
        for (int netId = 0; netId < netValues.length; netId++) {
            netValues[netId] = undriven(netId);
        }
        for (int driverId = 0; driverId < driverValues.length; driverId++) {
            driverValues[driverId] = undriven(circuit.driver(driverId).netId());
        }
        for (ComponentRuntimeState state : states) {
            state.reset();
        }
        for (int componentId = 0; componentId < circuit.componentCount(); componentId++) {
            evaluate(componentId, 0);
        }
        status = queue.isEmpty() ? SimulationStatus.STABLE : SimulationStatus.PENDING;
        for (SimulationObserver observer : List.copyOf(observers)) {
            observer.onReset();
        }
        if (running) {
            stabilizeAfterReset();
        }
    }

    private void stabilizeAfterReset() {
        try {
            runUntilStable();
        } catch (SimulationOscillationException oscillation) {
            // A circuit can be built oscillating; that is a state to display, not a
            // failure to construct the simulation. Explicit runs still report it.
            this.oscillation = oscillation;
        }
    }

    /**
     * Processes one delta cycle: applies every event scheduled for the earliest pending
     * (time, delta), resolves the nets they touch and evaluates the components that read
     * a net which actually changed.
     *
     * @return {@code false} if there was nothing left to do
     */
    public boolean step() {
        if (queue.isEmpty()) {
            status = SimulationStatus.STABLE;
            return false;
        }
        SimulationEvent next = queue.peek();
        time = next.time();
        deltaCycle = next.deltaCycle();

        TreeSet<Integer> dirtyNets = new TreeSet<>();
        while (!queue.isEmpty() && queue.peek().time() == time && queue.peek().deltaCycle() == deltaCycle) {
            SimulationEvent event = queue.poll();
            driverValues[event.driverId()] = event.value();
            dirtyNets.add(event.netId());
        }

        TreeSet<Integer> toEvaluate = new TreeSet<>();
        lastChangedNets.clear();
        for (int netId : dirtyNets) {
            LogicVector resolved = resolveNet(netId);
            LogicVector previous = netValues[netId];
            if (!resolved.equals(previous)) {
                netValues[netId] = resolved;
                lastChangedNets.add(netId);
                for (SimulationObserver observer : observers) {
                    observer.onNetChanged(netId, previous, resolved, time, deltaCycle);
                }
                for (int consumer : circuit.net(netId).consumers()) {
                    toEvaluate.add(consumer);
                }
            }
        }
        for (int componentId : toEvaluate) {
            evaluate(componentId, deltaCycle + 1);
        }
        status = queue.isEmpty() ? SimulationStatus.STABLE : SimulationStatus.PENDING;
        return true;
    }

    /**
     * Propagates until nothing is left to do.
     *
     * @return the number of delta cycles it took
     * @throws SimulationOscillationException if the circuit does not settle
     */
    public int runUntilStable() {
        int cycles = 0;
        while (!queue.isEmpty()) {
            step();
            if (++cycles > maxDeltaCycles) {
                status = SimulationStatus.OSCILLATING;
                queue.clear();
                oscillation = new SimulationOscillationException(cycles, List.copyOf(lastChangedNets));
                throw oscillation;
            }
        }
        status = SimulationStatus.STABLE;
        oscillation = null;
        return cycles;
    }

    /** Details of the last detected oscillation, while {@link #status()} reports one. */
    public java.util.Optional<SimulationOscillationException> oscillation() {
        return java.util.Optional.ofNullable(oscillation);
    }

    /**
     * Sets the value a user-driven component (a switch, a button) puts out. This is a
     * simulation input, not a document edit.
     */
    public void setInput(int componentId, LogicVector value) {
        if (!(states[componentId] instanceof InputSourceState source)) {
            throw new SimulationException("Component " + componentId + " ("
                    + circuit.component(componentId).definitionId() + ") is not a user driven input");
        }
        source.setValue(value);
        time++;
        deltaCycle = 0;
        evaluate(componentId, 0);
        status = queue.isEmpty() ? SimulationStatus.STABLE : SimulationStatus.PENDING;
        if (running) {
            runUntilStable();
        }
    }

    public void setInput(int componentId, LogicState value) {
        setInput(componentId, LogicVector.single(value));
    }

    /**
     * While running, changes settle immediately. While paused, they queue up and only
     * {@link #step()} advances the circuit — which is what breakpoints will later need.
     */
    public void setRunning(boolean shouldRun) {
        this.running = shouldRun;
        if (shouldRun && !queue.isEmpty()) {
            runUntilStable();
        }
    }

    public boolean isRunning() {
        return running;
    }

    // ------------------------------------------------------------------
    // Reading the state
    // ------------------------------------------------------------------

    public LogicVector readNet(int netId) {
        return netValues[netId];
    }

    /** The value a component's output port currently sees on its net. */
    public LogicVector readOutput(int componentId, int outputIndex) {
        return netValues[circuit.component(componentId).outputNets()[outputIndex]];
    }

    public LogicVector readInput(int componentId, int inputIndex) {
        return netValues[circuit.component(componentId).inputNets()[inputIndex]];
    }

    public LogicVector driverValue(int driverId) {
        return driverValues[driverId];
    }

    /** {@code true} if two drivers of this net actively drive different values. */
    public boolean hasDriverConflict(int netId) {
        int[] drivers = circuit.net(netId).drivers();
        int width = circuit.net(netId).width().bits();
        for (int bit = 0; bit < width; bit++) {
            LogicState[] contributions = new LogicState[drivers.length];
            for (int i = 0; i < drivers.length; i++) {
                contributions[i] = driverValues[drivers[i]].getBit(bit);
            }
            if (LogicOperations.hasDriverConflict(contributions)) {
                return true;
            }
        }
        return false;
    }

    public ComponentRuntimeState stateOf(int componentId) {
        return states[componentId];
    }

    public SimulationStatus status() {
        return status;
    }

    public boolean isStable() {
        return queue.isEmpty();
    }

    public long time() {
        return time;
    }

    public int deltaCycle() {
        return deltaCycle;
    }

    public int pendingEventCount() {
        return queue.size();
    }

    public int maxDeltaCycles() {
        return maxDeltaCycles;
    }

    public void setMaxDeltaCycles(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("The delta cycle limit must be positive");
        }
        this.maxDeltaCycles = limit;
    }

    public void addObserver(SimulationObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(SimulationObserver observer) {
        observers.remove(observer);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private LogicVector undriven(int netId) {
        return LogicVector.repeat(LogicState.HIGH_IMPEDANCE, circuit.net(netId).width());
    }

    private LogicVector resolveNet(int netId) {
        int[] drivers = circuit.net(netId).drivers();
        LogicVector resolved = undriven(netId);
        for (int driverId : drivers) {
            resolved = LogicOperations.resolve(resolved, driverValues[driverId]);
        }
        return resolved;
    }

    private void evaluate(int componentId, int scheduleDelta) {
        context.component = circuit.component(componentId);
        context.scheduleDelta = scheduleDelta;
        context.component.behavior().evaluate(context);
    }

    /** Reused per evaluation; the simulator is single threaded by design. */
    private final class EvaluationContext implements ComponentContext {

        private CompiledComponent component;
        private int scheduleDelta;

        @Override
        public int inputCount() {
            return component.inputCount();
        }

        @Override
        public LogicVector readInput(int index) {
            return netValues[component.inputNets()[index]];
        }

        @Override
        public int outputCount() {
            return component.outputCount();
        }

        @Override
        public void driveOutput(int index, LogicVector value) {
            int driverId = component.outputDrivers()[index];
            CompiledDriver driver = circuit.driver(driverId);
            value.requireWidth(circuit.net(driver.netId()).width());
            if (driverValues[driverId].equals(value)) {
                return;
            }
            queue.add(new SimulationEvent(time, scheduleDelta, sequence++, driver.netId(), driverId, value));
        }

        @Override
        public ComponentRuntimeState state() {
            return states[component.id()];
        }

        @Override
        public long time() {
            return time;
        }

        @Override
        public int deltaCycle() {
            return deltaCycle;
        }
    }
}
