package dev.logicforge.ui.edit;

import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.document.CircuitChange;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitDocumentListener;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortSlice;
import dev.logicforge.circuit.document.SubcircuitSupport;
import dev.logicforge.compiler.CircuitCompileException;
import dev.logicforge.compiler.CircuitCompiler;
import dev.logicforge.compiler.CompilationResult;
import dev.logicforge.compiler.ValidationIssue;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.LibraryParameters;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.InputSourceState;
import dev.logicforge.simulation.Simulation;
import dev.logicforge.simulation.SimulationOscillationException;
import dev.logicforge.simulation.SimulationStatus;
import dev.logicforge.ui.command.CircuitCommand;
import dev.logicforge.ui.command.ChangeParameterCommand;
import dev.logicforge.ui.command.UndoStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Ties the circuit document to the compiler, the simulation and the undo history.
 *
 * <p>This is the editor's model layer: it knows nothing about JavaFX, and the controllers
 * above it do little more than translate user gestures into the operations offered here.
 *
 * <h2>When the circuit is recompiled</h2>
 *
 * Only changes that alter the electrical structure — adding, removing, wiring,
 * reconfiguring — cause a recompile. Moving, rotating or renaming a component leaves the
 * netlist alone and never disturbs a running simulation. When a recompile does happen, the
 * values the user set on switches are carried over, so building on a circuit does not
 * throw away the state it is in.
 */
public final class CircuitEditor {

    private final ComponentRegistry registry;
    private final CircuitCompiler compiler;
    private final UndoStack undoStack = new UndoStack();
    private final SelectionModel selection = new SelectionModel();
    private final CircuitClipboard clipboard = new CircuitClipboard();
    private final List<Runnable> changeListeners = new ArrayList<>();

    private CircuitProject project;
    private CircuitDocument document;
    private CompilationResult compilation;
    private Simulation simulation;
    private String compileError;
    private List<ValidationIssue> lastValidationIssues = List.of();
    private boolean dirty;
    private CircuitDocumentListener documentListener;
    /**
     * The user's run/pause intent, independent of any particular {@link Simulation}
     * instance. A recompile replaces {@code simulation} outright (a fresh object, not an
     * update of the old one), so this is what makes Pause survive a topology edit instead
     * of every new Simulation silently defaulting back to running.
     */
    private boolean desiredRunning = true;

    public CircuitEditor(ComponentRegistry registry) {
        this.registry = registry;
        this.compiler = new CircuitCompiler(registry);
        setProject(CircuitProject.empty("untitled"), false);
    }

    // ------------------------------------------------------------------
    // Project handling
    // ------------------------------------------------------------------

    public void setProject(CircuitProject newProject, boolean markDirty) {
        if (document != null && documentListener != null) {
            document.removeListener(documentListener);
        }
        this.project = newProject;
        this.document = newProject.mainCircuit();
        this.documentListener = this::onCircuitChanged;
        this.document.addListener(documentListener);
        this.undoStack.clear();
        this.selection.clear();
        this.dirty = markDirty;
        // Clear lastValidationIssues before recompiling
        this.lastValidationIssues = List.of();
        recompile(Map.of(), Map.of());
        notifyChanged();
    }

    public CircuitProject project() {
        return project;
    }

    public CircuitDocument document() {
        return document;
    }

    public ComponentRegistry registry() {
        return registry;
    }

    public UndoStack undoStack() {
        return undoStack;
    }

    public SelectionModel selection() {
        return selection;
    }

    public CircuitClipboard clipboard() {
        return clipboard;
    }

    /** {@code true} while the project has changes that are not saved yet. */
    public boolean isDirty() {
        return dirty;
    }

    public void markSaved() {
        dirty = false;
        notifyChanged();
    }

    // ------------------------------------------------------------------
    // Editing
    // ------------------------------------------------------------------

    /** Runs an editing command through the undo history. */
    public void execute(CircuitCommand command) {
        undoStack.execute(command);
        selection.retainExisting(document.components().stream().map(ComponentInstance::id).toList(),
                document.connections().stream().map(connection -> connection.id()).toList());
        notifyChanged();
    }

    public void undo() {
        undoStack.undo();
        afterHistoryChange();
    }

    public void redo() {
        undoStack.redo();
        afterHistoryChange();
    }

    private void afterHistoryChange() {
        selection.retainExisting(document.components().stream().map(ComponentInstance::id).toList(),
                document.connections().stream().map(connection -> connection.id()).toList());
        notifyChanged();
    }

    public Optional<ComponentDefinition> definitionOf(ComponentInstance instance) {
        Optional<ComponentDefinition> builtIn = registry.definition(instance.definitionId());
        return builtIn.isPresent() ? builtIn
                : SubcircuitSupport.definition(project, instance.definitionId());
    }

    public Optional<ComponentDefinition> definition(String definitionId) {
        Optional<ComponentDefinition> builtIn = registry.definition(definitionId);
        return builtIn.isPresent() ? builtIn : SubcircuitSupport.definition(project, definitionId);
    }

    // ------------------------------------------------------------------
    // Simulation
    // ------------------------------------------------------------------

    public Optional<Simulation> simulation() {
        return Optional.ofNullable(simulation);
    }

    public Optional<CompilationResult> compilation() {
        return Optional.ofNullable(compilation);
    }

    /** The message of the last failed compilation, if the circuit currently has errors. */
    public Optional<String> compileError() {
        return Optional.ofNullable(compileError);
    }

    public List<ValidationIssue> issues() {
        return lastValidationIssues;
    }

    public SimulationStatus status() {
        return simulation == null ? SimulationStatus.STABLE : simulation.status();
    }

    public boolean isRunning() {
        return desiredRunning;
    }

    public void setRunning(boolean running) {
        desiredRunning = running;
        if (simulation != null) {
            guarded(() -> simulation.setRunning(running));
        }
        notifyChanged();
    }

    public void step() {
        if (simulation != null) {
            guarded(simulation::step);
            notifyChanged();
        }
    }

    /**
     * Jumps straight to the next scheduled event, wherever in virtual time that is, and
     * settles there — how a clocked circuit is advanced without single-stepping every
     * intervening delta cycle.
     */
    public void stepTime() {
        if (simulation != null) {
            guarded(simulation::advanceToNextEvent);
            notifyChanged();
        }
    }

    /** The next scheduled simulation time, if a future event (e.g. a clock edge) is pending. */
    public java.util.OptionalLong nextScheduledTime() {
        return simulation == null ? java.util.OptionalLong.empty() : simulation.nextScheduledTime();
    }

    /** The current simulation time, or 0 if nothing is compiled. */
    public long currentTime() {
        return simulation == null ? 0 : simulation.time();
    }

    /**
     * Advances virtual time towards {@code targetTime}, bounded to at most {@code maxAdvances}
     * scheduled events so one call can never block the UI regardless of how far in the future
     * {@code targetTime} is. Used by the playback controller once per animation frame.
     */
    public void advancePlayback(long targetTime, int maxAdvances) {
        if (simulation != null) {
            guarded(() -> simulation.advanceBudgeted(targetTime, maxAdvances));
            notifyChanged();
        }
    }

    /** Puts the simulation back into its initial state without touching the circuit. */
    public void resetSimulation() {
        if (simulation != null) {
            guarded(simulation::reset);
            notifyChanged();
        }
    }

    /** The value currently on the net a port is attached to. */
    public Optional<LogicVector> valueAt(PortReference port) {
        if (simulation == null || compilation == null) {
            return Optional.empty();
        }
        OptionalInt net = netOf(port);
        return net.isPresent() ? Optional.of(simulation.readNet(net.getAsInt())) : Optional.empty();
    }

    /** Value at an exact whole or bit endpoint. A bit of a whole vector is extracted. */
    public Optional<LogicVector> valueAt(PortEndpoint endpoint) {
        if (simulation == null || compilation == null) {
            return Optional.empty();
        }
        OptionalInt net = netOf(endpoint);
        if (net.isEmpty()) {
            return Optional.empty();
        }
        LogicVector value = simulation.readNet(net.getAsInt());
        if (endpoint.slice() instanceof PortSlice.Bit bit && value.width() > 1) {
            return Optional.of(LogicVector.single(value.getBit(bit.index())));
        }
        return Optional.of(value);
    }

    /** The value on a wire, for drawing it in the colour of its signal. */
    public Optional<LogicVector> valueOfConnection(UUID connectionId) {
        if (simulation == null || compilation == null) {
            return Optional.empty();
        }
        OptionalInt net = compilation.sourceMap().netOfConnection(connectionId);
        return net.isPresent() ? Optional.of(simulation.readNet(net.getAsInt())) : Optional.empty();
    }

    public OptionalInt netOf(PortReference port) {
        if (compilation == null) return OptionalInt.empty();
        OptionalInt direct = compilation.sourceMap().netOf(port);
        return direct.isPresent() ? direct : netOf(PortEndpoint.whole(port));
    }

    public OptionalInt netOf(PortEndpoint endpoint) {
        if (compilation == null) return OptionalInt.empty();
        OptionalInt direct = compilation.sourceMap().netOf(endpoint);
        return direct.isPresent() ? direct : compilation.hierarchySourceMap().netId(
                dev.logicforge.compiler.CircuitFlattener.endpointPath(
                        document.metadata().name(), endpoint));
    }

    public OptionalInt netOfConnection(UUID connectionId) {
        return compilation == null ? OptionalInt.empty()
                : compilation.sourceMap().netOfConnection(connectionId);
    }

    public boolean hasDriverConflict(int netId) {
        return simulation != null && simulation.hasDriverConflict(netId);
    }

    /** Returns the width in bits of the net carrying this connection, or 0 if unknown. */
    public int netWidth(UUID connectionId) {
        if (compilation == null) return 0;
        OptionalInt net = compilation.sourceMap().netOfConnection(connectionId);
        if (net.isEmpty()) return 0;
        return compilation.circuit().net(net.getAsInt()).width().bits();
    }

    /** {@code true} if this component can be driven by clicking it. */
    public boolean isUserInput(UUID componentId) {
        return inputStateOf(componentId).isPresent();
    }

    /** Returns the input interaction type for a component, or NONE if not interactive. */
    public dev.logicforge.circuit.component.InputInteraction inputInteraction(UUID componentId) {
        return document.component(componentId)
                .flatMap(instance -> registry.definition(instance.definitionId()))
                .map(ComponentDefinition::inputInteraction)
                .orElse(dev.logicforge.circuit.component.InputInteraction.NONE);
    }

    /** Flips a toggle switch. Simulation state only — the project stays unmodified. */
    public void toggleInput(UUID componentId) {
        inputStateOf(componentId).ifPresent(state -> {
            LogicState current = state.value().singleBit();
            setInput(componentId, current == LogicState.ONE ? LogicState.ZERO : LogicState.ONE);
        });
    }

    /**
     * Handles a user interaction with an input component.
     * For TOGGLE: toggles the state on click.
     * For MOMENTARY: sets to active state on press, inactive on release.
     * Respects the inverted parameter for both types.
     */
    public void handleInputInteraction(UUID componentId, boolean pressed) {
        document.component(componentId).ifPresent(instance -> {
            var defOpt = registry.definition(instance.definitionId());
            if (defOpt.isEmpty()) {
                return;
            }
            var def = defOpt.get();
            var interaction = def.inputInteraction();
            
            // Get inverted parameter, defaulting to false if not present
            boolean inverted = false;
            if (instance.parameters().asMap().containsKey(LibraryParameters.INVERTED.key())) {
                inverted = instance.parameters().getBoolean(LibraryParameters.INVERTED);
            }
            
            if (interaction == dev.logicforge.circuit.component.InputInteraction.TOGGLE && !pressed) {
                // Toggle on click (release after press)
                inputStateOf(componentId).ifPresent(state -> {
                    LogicState current = state.value().singleBit();
                    setInput(componentId, current == LogicState.ONE ? LogicState.ZERO : LogicState.ONE);
                });
            } else if (interaction == dev.logicforge.circuit.component.InputInteraction.MOMENTARY) {
                // Momentary: pressed = active, released = inactive
                LogicState value = pressed
                        ? (inverted ? LogicState.ZERO : LogicState.ONE)
                        : (inverted ? LogicState.ONE : LogicState.ZERO);
                setInput(componentId, value);
            }
        });
    }

    public void setInput(UUID componentId, LogicState value) {
        if (simulation == null || compilation == null) {
            return;
        }
        OptionalInt runtimeId = compilation.sourceMap().componentId(componentId);
        if (runtimeId.isEmpty()) {
            return;
        }
        guarded(() -> simulation.setInput(runtimeId.getAsInt(), value));
        notifyChanged();
    }

    public Optional<LogicState> inputValueOf(UUID componentId) {
        return inputStateOf(componentId).map(state -> state.value().singleBit());
    }

    private Optional<InputSourceState> inputStateOf(UUID componentId) {
        if (simulation == null || compilation == null) {
            return Optional.empty();
        }
        OptionalInt runtimeId = compilation.sourceMap().componentId(componentId);
        if (runtimeId.isEmpty()) {
            return Optional.empty();
        }
        return simulation.stateOf(runtimeId.getAsInt()) instanceof InputSourceState state
                ? Optional.of(state)
                : Optional.empty();
    }

    // ------------------------------------------------------------------
    // Compilation
    // ------------------------------------------------------------------

    private void onCircuitChanged(CircuitDocument changed, CircuitChange change) {
        dirty = true;
        if (change.affectsTopology()) {
            Map<UUID, LogicVector> previousInputs = captureInputValues();
            Map<UUID, Object> previousStates = captureRuntimeStates();
            recompile(previousInputs, previousStates);
        }
        notifyChanged();
    }

    private void recompile(Map<UUID, LogicVector> previousInputs, Map<UUID, Object> previousStates) {
        try {
            compilation = compiler.compile(project, document.metadata().name());
            compileError = null;
            lastValidationIssues = compilation.issues();
            simulation = new Simulation(compilation.circuit(), false);
            restoreInputValues(previousInputs);
            restoreRuntimeStates(previousStates);
            simulation.reevaluateAllAtCurrentTime();
            simulation.runUntilStableAtCurrentTime();
            simulation.setRunning(desiredRunning);
        } catch (CircuitCompileException failure) {
            compilation = null;
            simulation = null;
            compileError = failure.getMessage();
            lastValidationIssues = failure.issues();
        } catch (SimulationOscillationException oscillation) {
            compilation = null;
            simulation = null;
            compileError = oscillation.getMessage();
            lastValidationIssues = List.of();
        }
    }

    /** Remembers what every switch is set to, so a recompile does not reset the circuit. */
    private Map<UUID, LogicVector> captureInputValues() {
        Map<UUID, LogicVector> values = new HashMap<>();
        if (compilation == null || simulation == null) {
            return values;
        }
        for (Map.Entry<UUID, Integer> entry : compilation.sourceMap().componentIdByUuid().entrySet()) {
            if (simulation.stateOf(entry.getValue()) instanceof InputSourceState state) {
                values.put(entry.getKey(), state.value());
            }
        }
        return values;
    }

    private void restoreInputValues(Map<UUID, LogicVector> values) {
        values.forEach((componentUuid, value) -> {
            OptionalInt runtimeId = compilation.sourceMap().componentId(componentUuid);
            if (runtimeId.isPresent()
                    && simulation.stateOf(runtimeId.getAsInt()) instanceof InputSourceState) {
                guarded(() -> simulation.restoreInputState(runtimeId.getAsInt(), value));
            }
        });
    }

    /**
     * Snapshots every component's runtime state (registers, RAM, etc.) so the values can
     * be carried across a recompile. Components that return {@code null} from
     * {@link dev.logicforge.simulation.ComponentRuntimeState#snapshot()} are skipped.
     */
    private Map<UUID, Object> captureRuntimeStates() {
        Map<UUID, Object> snapshots = new LinkedHashMap<>();
        if (compilation == null || simulation == null) {
            return snapshots;
        }
        for (Map.Entry<UUID, Integer> entry : compilation.sourceMap().componentIdByUuid().entrySet()) {
            Object snap = simulation.stateOf(entry.getValue()).snapshot();
            if (snap != null) {
                snapshots.put(entry.getKey(), snap);
            }
        }
        return snapshots;
    }

    /**
     * Restores previously-captured runtime-state snapshots into the freshly-built
     * simulation. Components that no longer exist in the new circuit (UUID gone) or
     * whose configuration has changed (incompatible snapshot) are silently skipped.
     */
    private void restoreRuntimeStates(Map<UUID, Object> snapshots) {
        snapshots.forEach((uuid, snap) -> {
            OptionalInt id = compilation.sourceMap().componentId(uuid);
            if (id.isPresent()) {
                simulation.stateOf(id.getAsInt()).restore(snap);
            }
        });
    }

    /** Runs a simulation step, turning an oscillation into a status instead of a crash. */
    private void guarded(Runnable action) {
        try {
            action.run();
            compileError = null;
        } catch (SimulationOscillationException oscillation) {
            compileError = "Combinational oscillation detected";
        }
    }

    // ------------------------------------------------------------------
    // Listeners
    // ------------------------------------------------------------------

    public java.util.Optional<dev.logicforge.simulation.MemorySnapshot> memorySnapshot(java.util.UUID componentId) {
        if (simulation == null || compilation == null) return java.util.Optional.empty();
        java.util.OptionalInt runtimeId = compilation.sourceMap().componentId(componentId);
        if (runtimeId.isEmpty()) return java.util.Optional.empty();
        return simulation.memorySnapshot(runtimeId.getAsInt());
    }

    public void writeMemoryWord(java.util.UUID componentId, int address, dev.logicforge.logic.LogicVector value) {
        document.component(componentId).ifPresent(instance -> {
            if (instance.parameters().asMap().containsKey(LibraryParameters.ROM_CONTENTS.key())) {
                memorySnapshot(componentId).ifPresent(snapshot -> {
                    List<LogicVector> words = new ArrayList<>();
                    for (int i = 0; i < snapshot.size(); i++) {
                        words.add(i == address ? value : snapshot.wordAt(i));
                    }
                    updateRomContents(instance, words);
                });
                return;
            }
            if (simulation == null || compilation == null) return;
            java.util.OptionalInt runtimeId = compilation.sourceMap().componentId(componentId);
            if (runtimeId.isEmpty()) return;
            guarded(() -> simulation.writeMemoryWord(runtimeId.getAsInt(), address, value));
            notifyChanged();
        });
    }

    /** Loads complete contents into project-backed ROM or live RAM. */
    public void loadMemory(UUID componentId, List<LogicVector> words) {
        document.component(componentId).ifPresent(instance -> {
            if (instance.parameters().asMap().containsKey(LibraryParameters.ROM_CONTENTS.key())) {
                updateRomContents(instance, words);
                return;
            }
            if (simulation == null || compilation == null) return;
            OptionalInt runtimeId = compilation.sourceMap().componentId(componentId);
            if (runtimeId.isEmpty()) return;
            int id = runtimeId.getAsInt();
            for (int address = 0; address < words.size(); address++) {
                compilation.circuit().component(id).behavior().writeMemoryWord(
                        simulation.stateOf(id), address, words.get(address));
            }
            guarded(() -> simulation.reevaluateComponent(id));
            notifyChanged();
        });
    }

    private void updateRomContents(ComponentInstance instance, List<LogicVector> words) {
        String csv = words.stream().map(word -> word.toUnsignedLong().isPresent()
                        ? Long.toHexString(word.toUnsignedLong().getAsLong()).toUpperCase(java.util.Locale.ROOT)
                        : "0")
                .collect(java.util.stream.Collectors.joining(","));
        definitionOf(instance).ifPresent(definition -> execute(new ChangeParameterCommand(
                document, definition, document.requireComponent(instance.id()),
                LibraryParameters.ROM_CONTENTS.key(), csv)));
    }

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    private void notifyChanged() {
        changeListeners.forEach(Runnable::run);
    }
}
