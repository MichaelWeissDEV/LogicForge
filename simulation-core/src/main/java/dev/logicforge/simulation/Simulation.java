package dev.logicforge.simulation;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import java.util.ArrayList;
import java.util.Arrays;
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
 * <h2>Time</h2>
 *
 * {@code time} is physical simulation time in picoseconds; {@code deltaCycle} orders
 * events that share the same {@code time}, e.g. a chain of combinational gates settling.
 * A component schedules a same-time reaction with {@link ComponentContext#driveOutput},
 * a future reaction with {@link ComponentContext#driveOutputAfter}/{@code driveOutputAt},
 * and a self-triggered re-evaluation (a clock's next edge) with
 * {@link ComponentContext#scheduleWakeup}. {@link #setInput} is the one exception: a user
 * stimulus always advances time by exactly one picosecond, which is negligible next to any
 * realistic clock period (1 MHz is already 10<sup>6</sup> ps) but means a circuit driven by
 * an extremely long run of user clicks could in principle catch up with a very fast virtual
 * clock; this is not a concern for the interactive use this simulator targets.
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
 *
 * <h2>Stable at current time vs. no future events</h2>
 *
 * A circuit can be perfectly settled <em>right now</em> while still having future work
 * queued — a running clock always does. {@link #status()} and the {@code runUntilStable*}
 * methods therefore only ever settle up to the current instant; they never block waiting
 * for a self-perpetuating component to stop scheduling itself. Use
 * {@link #nextScheduledTime()} or {@link #isStable()} to ask about the future separately,
 * and {@link #advanceToNextEvent()} / {@link #runUntil(long)} to move virtual time forward.
 */
public final class Simulation {

    /** Delta cycles allowed per stabilisation before a circuit counts as oscillating. */
    public static final int DEFAULT_MAX_DELTA_CYCLES = 1000;

    private final CompiledCircuit circuit;
    private final LogicVector[] netValues;
    private final LogicVector[] driverValues;
    private final ComponentRuntimeState[] states;
    /** Time of the pending wakeup per component, or -1; avoids queueing exact duplicates. */
    private final long[] pendingWakeupAt;
    private final PriorityQueue<TimelineEntry> queue = new PriorityQueue<>();
    private final List<SimulationObserver> observers = new ArrayList<>();
    private final List<Integer> lastChangedNets = new ArrayList<>();
    private final EvaluationContext context = new EvaluationContext();

    private int maxDeltaCycles = DEFAULT_MAX_DELTA_CYCLES;
    private long sequence;
    private long time;
    private int deltaCycle;
    private int deltaCyclesAtCurrentTime;
    private boolean running = true;
    private SimulationStatus status = SimulationStatus.STABLE;
    private SimulationOscillationException oscillation;

    // Cumulative activity counters backing metrics(); see SimulationMetrics for what each
    // one means. Plain longs updated at the point the corresponding work already happens -
    // no extra allocation, no behavior change.
    private long metricEventsProcessed;
    private long metricComponentEvaluations;
    private long metricNetTransitions;
    private long metricDeltaCycles;
    private long metricScheduledWakeups;
    private long metricMaxDeltaDepth;

    public Simulation(CompiledCircuit circuit) {
        this(circuit, true);
    }

    /** Creates a simulation with explicit initial run/pause intent. */
    public Simulation(CompiledCircuit circuit, boolean running) {
        this.circuit = circuit;
        this.netValues = new LogicVector[circuit.netCount()];
        this.driverValues = new LogicVector[circuit.driverCount()];
        this.states = new ComponentRuntimeState[circuit.componentCount()];
        this.pendingWakeupAt = new long[circuit.componentCount()];
        for (int id = 0; id < circuit.componentCount(); id++) {
            states[id] = circuit.component(id).behavior().createState();
        }
        this.running = running;
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
     * the circuit at time zero; while it is paused the resulting events wait for
     * {@link #step()}. A component that schedules future work (a clock) leaves that work
     * pending in the queue rather than being run out to completion.
     */
    public void reset() {
        queue.clear();
        lastChangedNets.clear();
        oscillation = null;
        sequence = 0;
        time = 0;
        deltaCycle = 0;
        deltaCyclesAtCurrentTime = 0;
        metricEventsProcessed = 0;
        metricComponentEvaluations = 0;
        metricNetTransitions = 0;
        metricDeltaCycles = 0;
        metricScheduledWakeups = 0;
        metricMaxDeltaDepth = 0;
        Arrays.fill(pendingWakeupAt, -1);
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
            runUntilStableAtCurrentTime();
        } catch (SimulationOscillationException oscillation) {
            // A circuit can be built oscillating; that is a state to display, not a
            // failure to construct the simulation. Explicit runs still report it.
            this.oscillation = oscillation;
        }
    }

    /**
     * Processes one delta cycle: applies every event scheduled for the earliest pending
     * (time, delta) — driver value changes and component wakeups alike — resolves the
     * nets that changed and evaluates the components that read one or requested this
     * wakeup.
     *
     * @return {@code false} if there was nothing left to do
     */
    public boolean step() {
        if (queue.isEmpty()) {
            status = SimulationStatus.STABLE;
            return false;
        }
        TimelineEntry next = queue.peek();
        long newTime = next.time();
        int newDeltaCycle = next.deltaCycle();

        if (newTime != time) {
            deltaCyclesAtCurrentTime = 0;
        }
        time = newTime;
        deltaCycle = newDeltaCycle;
        deltaCyclesAtCurrentTime++;
        metricDeltaCycles++;
        if (deltaCyclesAtCurrentTime > metricMaxDeltaDepth) {
            metricMaxDeltaDepth = deltaCyclesAtCurrentTime;
        }

        TreeSet<Integer> dirtyNets = new TreeSet<>();
        TreeSet<Integer> toEvaluate = new TreeSet<>();
        while (!queue.isEmpty() && queue.peek().time() == time && queue.peek().deltaCycle() == deltaCycle) {
            TimelineEntry entry = queue.poll();
            metricEventsProcessed++;
            switch (entry) {
                case SimulationEvent event -> {
                    driverValues[event.driverId()] = event.value();
                    dirtyNets.add(event.netId());
                }
                case WakeupEvent wakeup -> {
                    if (pendingWakeupAt[wakeup.componentId()] == wakeup.time()) {
                        pendingWakeupAt[wakeup.componentId()] = -1;
                    }
                    toEvaluate.add(wakeup.componentId());
                }
            }
        }

        lastChangedNets.clear();
        for (int netId : dirtyNets) {
            LogicVector resolved = resolveNet(netId);
            LogicVector previous = netValues[netId];
            if (!resolved.equals(previous)) {
                netValues[netId] = resolved;
                metricNetTransitions++;
                lastChangedNets.add(netId);
                for (SimulationObserver observer : List.copyOf(observers)) {
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
     * Propagates until the queue is completely empty, including every future event a
     * self-perpetuating component (a clock) schedules for itself. Since such a component
     * never stops rescheduling, this must only be called on circuits known not to contain
     * one; prefer {@link #runUntilStableAtCurrentTime()} or {@link #runUntil(long)}
     * otherwise.
     *
     * @return the number of delta cycles it took
     * @throws SimulationOscillationException if the circuit does not settle within
     *         {@link #maxDeltaCycles} delta cycles at the same timestamp
     */
    public int runUntilStable() {
        int totalCycles = 0;
        while (!queue.isEmpty()) {
            step();
            totalCycles++;
            checkOscillation(totalCycles);
        }
        status = SimulationStatus.STABLE;
        oscillation = null;
        return totalCycles;
    }

    /**
     * Propagates until the circuit is stable at the current timestamp: every event
     * scheduled for {@link #time()} is processed, through as many delta cycles as it
     * takes, but the simulation never advances to a later timestamp. A future event (a
     * clock's next edge) is left pending.
     *
     * @return the number of delta cycles processed at the current timestamp
     * @throws SimulationOscillationException if the circuit oscillates at this timestamp
     */
    public int runUntilStableAtCurrentTime() {
        return runUntilTimeBoundary(time);
    }

    /**
     * Jumps directly to the next scheduled time, wherever that may be, and stabilizes
     * there. This is how a clocked circuit is advanced through virtual time without
     * single-stepping every intervening delta cycle.
     *
     * @return {@code false} if there was no future event to advance to
     * @throws SimulationOscillationException if the circuit oscillates at that time
     */
    public boolean advanceToNextEvent() {
        if (queue.isEmpty()) {
            return false;
        }
        runUntilTimeBoundary(queue.peek().time());
        return true;
    }

    /**
     * Repeatedly advances to the next scheduled event until simulation time reaches or
     * passes {@code targetTime}, or no events remain.
     *
     * @return the simulation time reached, which may be before {@code targetTime} if the
     *         queue ran out of events
     */
    public long runUntil(long targetTime) {
        while (!queue.isEmpty() && queue.peek().time() <= targetTime) {
            advanceToNextEvent();
        }
        return time;
    }

    /**
     * Like {@link #runUntil(long)}, but never advances more than {@code maxAdvances} times
     * in one call, even if {@code targetTime} has not been reached yet. This is what a UI
     * playback loop calls once per frame: a fast clock's virtual horizon can be far enough
     * ahead that draining it in one call would freeze the interface, so the caller instead
     * gets however far a bounded amount of work reaches and continues from there on the
     * next frame. A slow clock reaches {@code targetTime} in well under the budget, so nothing
     * about the ordinary case changes.
     *
     * @return the simulation time reached, which may be before {@code targetTime}
     */
    public long advanceBudgeted(long targetTime, int maxAdvances) {
        int advances = 0;
        while (advances < maxAdvances && !queue.isEmpty() && queue.peek().time() <= targetTime) {
            advanceToNextEvent();
            advances++;
        }
        return time;
    }

    private int runUntilTimeBoundary(long targetTime) {
        int cycles = 0;
        while (!queue.isEmpty() && queue.peek().time() == targetTime) {
            step();
            cycles++;
            checkOscillation(cycles);
        }
        status = SimulationStatus.STABLE;
        oscillation = null;
        return cycles;
    }

    private void checkOscillation(int cyclesSoFar) {
        if (deltaCyclesAtCurrentTime > maxDeltaCycles) {
            status = SimulationStatus.OSCILLATING;
            queue.clear();
            oscillation = new SimulationOscillationException(cyclesSoFar, List.copyOf(lastChangedNets));
            throw oscillation;
        }
    }

    /**
     * Returns the timestamp of the next scheduled event, or empty if the queue is empty.
     *
     * @return the next scheduled time, or empty if no events are pending
     */
    public java.util.OptionalLong nextScheduledTime() {
        return queue.isEmpty() ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(queue.peek().time());
    }

    /**
     * Returns the current simulation timestamp.
     *
     * @return the current time
     */
    public long currentTime() {
        return time;
    }

    /**
     * Returns the current delta cycle within the current timestamp.
     *
     * @return the current delta cycle
     */
    public int currentDeltaCycle() {
        return deltaCycle;
    }

    /** Details of the last detected oscillation, while {@link #status()} reports one. */
    public java.util.Optional<SimulationOscillationException> oscillation() {
        return java.util.Optional.ofNullable(oscillation);
    }

    /** A snapshot of cumulative activity counters since construction or the last {@link #reset()}. */
    public SimulationMetrics metrics() {
        return new SimulationMetrics(metricEventsProcessed, metricComponentEvaluations,
                metricNetTransitions, metricDeltaCycles, metricScheduledWakeups, time,
                metricMaxDeltaDepth);
    }

    /**
     * Sets the value a user-driven component (a switch, a button) puts out. This is an
     * external stimulus that advances simulation time.
     *
     * @param componentId the runtime id of the component to set
     * @param value the value to set
     */
    public void setInput(int componentId, LogicVector value) {
        if (!(states[componentId] instanceof InputSourceState source)) {
            throw new SimulationException("Component " + componentId + " ("
                    + circuit.component(componentId).definitionId() + ") is not a user driven input");
        }
        source.setValue(value);
        time++;
        deltaCycle = 0;
        deltaCyclesAtCurrentTime = 0;
        evaluate(componentId, 0);
        status = queue.isEmpty() ? SimulationStatus.STABLE : SimulationStatus.PENDING;
        if (running) {
            runUntilStableAtCurrentTime();
        }
    }

    public void setInput(int componentId, LogicState value) {
        setInput(componentId, LogicVector.single(value));
    }

    /**
     * Restores the value of a user-driven component without advancing simulation time.
     * This is used when re-compiling a circuit to preserve the user's switch settings,
     * which is not an external stimulus but an internal state restoration.
     *
     * @param componentId the runtime id of the component to restore
     * @param value the value to restore
     */
    public void restoreInputState(int componentId, LogicVector value) {
        if (!(states[componentId] instanceof InputSourceState source)) {
            throw new SimulationException("Component " + componentId + " ("
                    + circuit.component(componentId).definitionId() + ") is not a user driven input");
        }
        source.setValue(value);
        // Do NOT advance time - this is state restoration, not an external stimulus. It is
        // still a discrete settle operation in its own right (recompile restores every
        // switch this way, one call per input), so it gets a fresh delta-cycle budget the
        // same way setInput does - otherwise a circuit with many inputs could trip the
        // oscillation detector purely from the number of restoreInputState calls, none of
        // which individually does anything close to oscillating.
        deltaCyclesAtCurrentTime = 0;
        evaluate(componentId, 0);
        status = queue.isEmpty() ? SimulationStatus.STABLE : SimulationStatus.PENDING;
        if (running) {
            runUntilStableAtCurrentTime();
        }
    }

    /**
     * Restores the value of a user-driven component without advancing simulation time.
     *
     * @param componentId the runtime id of the component to restore
     * @param value the value to restore
     */
    public void restoreInputState(int componentId, LogicState value) {
        restoreInputState(componentId, LogicVector.single(value));
    }

    /** Re-evaluates one component without advancing physical time. */
    public void reevaluateComponent(int componentId) {
        deltaCyclesAtCurrentTime = 0;
        evaluate(componentId, deltaCycle + 1);
        status = queue.isEmpty() ? SimulationStatus.STABLE : SimulationStatus.PENDING;
        if (running) {
            runUntilStableAtCurrentTime();
        }
    }

    /** Re-evaluates all behavior outputs from their current restored state. */
    public void reevaluateAllAtCurrentTime() {
        deltaCyclesAtCurrentTime = 0;
        for (int componentId = 0; componentId < circuit.componentCount(); componentId++) {
            evaluate(componentId, deltaCycle + 1);
        }
        status = queue.isEmpty() ? SimulationStatus.STABLE : SimulationStatus.PENDING;
        if (running) {
            runUntilStableAtCurrentTime();
        }
    }

    /**
     * While running, changes settle immediately, up to the current instant. While paused,
     * they queue up and only {@link #step()} advances the circuit — which is what
     * breakpoints will later need.
     */
    public void setRunning(boolean shouldRun) {
        this.running = shouldRun;
        if (shouldRun && !queue.isEmpty()) {
            runUntilStableAtCurrentTime();
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
        return readOutputBinding(circuit.component(componentId).outputBinding(outputIndex));
    }

    public LogicVector readInput(int componentId, int inputIndex) {
        return readInputBinding(circuit.component(componentId).inputBinding(inputIndex));
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

    /** Memory contents exposed by this component's behavior, if any. */
    public java.util.Optional<MemorySnapshot> memorySnapshot(int componentId) {
        CompiledComponent component = circuit.component(componentId);
        return java.util.Optional.ofNullable(
                component.behavior().memorySnapshot(states[componentId]));
    }

    public long memoryRevision(int componentId) {
        CompiledComponent component = circuit.component(componentId);
        return component.behavior().memoryRevision(states[componentId]);
    }

    /** Cheap memory metadata (size, content/access revision, last access) with no contents. */
    public java.util.Optional<MemoryInfo> memoryInfo(int componentId) {
        CompiledComponent component = circuit.component(componentId);
        return java.util.Optional.ofNullable(component.behavior().memoryInfo(states[componentId]));
    }

    /** A window of a memory's contents, for paging through a large RAM/ROM. */
    public java.util.Optional<MemoryPageSnapshot> memoryPage(int componentId, int startAddress, int count) {
        CompiledComponent component = circuit.component(componentId);
        return java.util.Optional.ofNullable(
                component.behavior().memoryPage(states[componentId], startAddress, count));
    }

    public ComponentDebugSnapshot debugSnapshot(int componentId) {
        CompiledComponent component = circuit.component(componentId);
        return component.behavior().debugSnapshot(states[componentId]);
    }

    /** Writes runtime-backed memory and refreshes outputs at the current timestamp. */
    public void writeMemoryWord(int componentId, int address, LogicVector value) {
        CompiledComponent component = circuit.component(componentId);
        component.behavior().writeMemoryWord(states[componentId], address, value);
        reevaluateComponent(componentId);
    }

    public SimulationStatus status() {
        return status;
    }

    /** {@code true} if no event is scheduled at all, at the current time or later. */
    public boolean isStable() {
        return queue.isEmpty();
    }

    /** {@code true} if nothing is left to process at the current instant specifically. */
    public boolean isStableAtCurrentTime() {
        return queue.isEmpty() || queue.peek().time() != time;
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
        metricComponentEvaluations++;
        context.component.behavior().evaluate(context);
    }

    private LogicVector readInputBinding(CompiledInputBinding binding) {
        if (binding instanceof CompiledInputBinding.Whole whole) {
            return netValues[whole.netId()];
        }
        int[] netIds = ((CompiledInputBinding.Bits) binding).netIds();
        LogicState[] bits = new LogicState[netIds.length];
        for (int bit = 0; bit < netIds.length; bit++) {
            bits[bit] = netValues[netIds[bit]].singleBit();
        }
        return LogicVector.ofLsbFirst(bits);
    }

    private LogicVector readOutputBinding(CompiledOutputBinding binding) {
        if (binding instanceof CompiledOutputBinding.Whole whole) {
            return netValues[whole.netId()];
        }
        int[] netIds = ((CompiledOutputBinding.Bits) binding).netIds();
        LogicState[] bits = new LogicState[netIds.length];
        for (int bit = 0; bit < netIds.length; bit++) {
            bits[bit] = netValues[netIds[bit]].singleBit();
        }
        return LogicVector.ofLsbFirst(bits);
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
            return readInputBinding(component.inputBinding(index));
        }

        @Override
        public int outputCount() {
            return component.outputCount();
        }

        @Override
        public void driveOutput(int index, LogicVector value) {
            CompiledOutputBinding binding = component.outputBinding(index);
            value.requireWidth(binding.width());
            if (binding instanceof CompiledOutputBinding.Whole whole) {
                scheduleImmediate(whole.netId(), whole.driverId(), value);
            } else {
                CompiledOutputBinding.Bits bits = (CompiledOutputBinding.Bits) binding;
                int[] netIds = bits.netIds();
                int[] driverIds = bits.driverIds();
                for (int bit = 0; bit < netIds.length; bit++) {
                    scheduleImmediate(netIds[bit], driverIds[bit], LogicVector.single(value.getBit(bit)));
                }
            }
        }

        private void scheduleImmediate(int netId, int driverId, LogicVector value) {
            if (!driverValues[driverId].equals(value)) {
                queue.add(new SimulationEvent(
                        time, scheduleDelta, sequence++, netId, driverId, value));
            }
        }

        @Override
        public void driveOutputAfter(int index, long delay, LogicVector value) {
            if (delay <= 0) {
                throw new SimulationException("driveOutputAfter delay must be positive, was " + delay);
            }
            scheduleDriverEvent(index, time + delay, value);
        }

        @Override
        public void driveOutputAt(int index, long eventTime, LogicVector value) {
            if (eventTime <= time) {
                throw new SimulationException(
                        "driveOutputAt requires a time after the current time (" + time + "), was " + eventTime);
            }
            scheduleDriverEvent(index, eventTime, value);
        }

        private void scheduleDriverEvent(int index, long eventTime, LogicVector value) {
            CompiledOutputBinding binding = component.outputBinding(index);
            value.requireWidth(binding.width());
            if (binding instanceof CompiledOutputBinding.Whole whole) {
                queue.add(new SimulationEvent(
                        eventTime, 0, sequence++, whole.netId(), whole.driverId(), value));
            } else {
                CompiledOutputBinding.Bits bits = (CompiledOutputBinding.Bits) binding;
                int[] netIds = bits.netIds();
                int[] driverIds = bits.driverIds();
                for (int bit = 0; bit < netIds.length; bit++) {
                    queue.add(new SimulationEvent(eventTime, 0, sequence++, netIds[bit],
                            driverIds[bit], LogicVector.single(value.getBit(bit))));
                }
            }
        }

        @Override
        public void scheduleWakeup(long wakeupTime) {
            if (wakeupTime <= time) {
                throw new SimulationException(
                        "scheduleWakeup requires a time after the current time (" + time + "), was " + wakeupTime);
            }
            int componentId = component.id();
            if (pendingWakeupAt[componentId] == wakeupTime) {
                return;
            }
            pendingWakeupAt[componentId] = wakeupTime;
            metricScheduledWakeups++;
            queue.add(new WakeupEvent(wakeupTime, 0, sequence++, componentId));
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
