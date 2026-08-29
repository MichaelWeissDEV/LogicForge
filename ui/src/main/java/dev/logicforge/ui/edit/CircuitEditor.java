package dev.logicforge.ui.edit;

import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.document.CircuitChange;
import dev.logicforge.circuit.document.CircuitDocument;
import dev.logicforge.circuit.document.CircuitDocumentListener;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.circuit.document.ComponentInstance;
import dev.logicforge.circuit.document.ElectricalEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.SubcircuitSupport;
import dev.logicforge.compiler.CircuitCompileException;
import dev.logicforge.compiler.CircuitCompiler;
import dev.logicforge.compiler.CompilationResult;
import dev.logicforge.compiler.ValidationIssue;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.library.chip.StandardChipLibrary;
import dev.logicforge.library.LibraryParameters;
import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.InputSourceState;
import dev.logicforge.simulation.Simulation;
import dev.logicforge.simulation.SimulationOscillationException;
import dev.logicforge.simulation.SimulationStatus;
import dev.logicforge.simulation.SimulationSession;
import dev.logicforge.ui.command.CircuitCommand;
import dev.logicforge.ui.command.ChangeParameterCommand;
import dev.logicforge.ui.command.UndoStack;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
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
    private final dev.logicforge.circuit.chip.ChipRegistry chipRegistry;
    private final CircuitCompiler compiler;
    private final Map<String, UndoStack> undoStacks = new LinkedHashMap<>();
    private UndoStack undoStack = new UndoStack();
    private final SelectionModel selection = new SelectionModel();
    private final CircuitClipboard clipboard = new CircuitClipboard();
    private final List<Runnable> changeListeners = new ArrayList<>();

    private CircuitProject project;
    private CircuitDocument document;
    private CircuitViewContext view;
    private final Deque<CircuitViewContext> navigationStack = new ArrayDeque<>();
    private CompilationResult compilation;
    private Simulation simulation;
    private SimulationSession simulationSession;
    private final Runnable simulationSessionListener = this::onSessionChanged;
    private String compileError;
    private List<ValidationIssue> lastValidationIssues = List.of();
    private boolean dirty;
    private CircuitDocumentListener documentListener;
    private boolean projectMutation;
    /**
     * The user's run/pause intent, independent of any particular {@link Simulation}
     * instance. A recompile replaces {@code simulation} outright (a fresh object, not an
     * update of the old one), so this is what makes Pause survive a topology edit instead
     * of every new Simulation silently defaulting back to running.
     */
    private boolean desiredRunning = true;

    public CircuitEditor(ComponentRegistry registry) {
        this(registry, StandardChipLibrary.create());
    }

    public CircuitEditor(ComponentRegistry registry, dev.logicforge.circuit.chip.ChipRegistry chipRegistry) {
        this.registry = registry;
        this.chipRegistry = chipRegistry;
        this.compiler = new CircuitCompiler(registry, chipRegistry);
        setProject(CircuitProject.empty("untitled"), false);
    }

    public dev.logicforge.circuit.chip.ChipRegistry chipRegistry() {
        return chipRegistry;
    }

    // ------------------------------------------------------------------
    // Project handling
    // ------------------------------------------------------------------

    public void setProject(CircuitProject newProject, boolean markDirty) {
        if (project != null && documentListener != null) {
            project.circuits().forEach(circuit -> circuit.removeListener(documentListener));
        }
        this.project = newProject;
        this.document = newProject.mainCircuit();
        this.view = new CircuitViewContext(CircuitProject.MAIN_CIRCUIT,
                Optional.of(CircuitProject.MAIN_CIRCUIT));
        this.navigationStack.clear();
        this.documentListener = this::onCircuitChanged;
        this.project.circuits().forEach(circuit -> circuit.addListener(documentListener));
        this.undoStacks.clear();
        this.project.circuitNames().forEach(name -> undoStacks.put(name, new UndoStack()));
        this.undoStack = undoStacks.get(view.circuitName());
        this.selection.clear();
        this.dirty = markDirty;
        // Clear lastValidationIssues before recompiling
        this.lastValidationIssues = List.of();
        recompile(Map.of(), Map.of());
        notifyChanged();
    }

    /**
     * Attaches a second, read-only-oriented view model to an already compiled live runtime.
     * The project is installed normally so definitions and hierarchy navigation remain
     * available, then the freshly compiled private runtime is replaced by the caller's
     * exact compilation/simulation pair. Study windows use this to display the same concrete
     * instance state as the main workbench rather than a look-alike reference simulation.
     */
    public void attachRuntime(CircuitProject liveProject, CompilationResult liveCompilation,
                              Simulation liveSimulation) {
        attachRuntime(liveProject, liveCompilation, liveSimulation,
                new SimulationSession(liveSimulation),
                CircuitProject.MAIN_CIRCUIT, Optional.of(CircuitProject.MAIN_CIRCUIT),
                Optional.empty());
    }

    /** Attaches a live runtime and restores the exact circuit/instance/selection context. */
    public void attachRuntime(CircuitProject liveProject, CompilationResult liveCompilation,
                              Simulation liveSimulation, SimulationSession liveSession,
                              String initialCircuitName,
                              Optional<String> initialInstancePath,
                              Optional<UUID> selectedComponent) {
        if (liveProject == null || liveCompilation == null || liveSimulation == null) {
            throw new IllegalArgumentException("Live project, compilation and simulation are required");
        }
        setProject(liveProject, false);
        this.compilation = liveCompilation;
        this.simulation = liveSimulation;
        bindSession(liveSession);
        this.compileError = null;
        this.lastValidationIssues = List.of();
        this.desiredRunning = liveSimulation.isRunning();
        switchActiveCircuit(initialCircuitName,
                initialInstancePath == null ? Optional.empty() : initialInstancePath, true);
        if (selectedComponent != null) {
            selectedComponent.filter(id -> document.component(id).isPresent())
                    .ifPresent(selection::selectComponent);
        }
        notifyChanged();
    }

    public CircuitProject project() {
        return project;
    }

    public CircuitDocument document() {
        return document;
    }

    public String activeCircuitName() {
        return view.circuitName();
    }

    /**
     * Hierarchy instance path when the current circuit was reached through a concrete
     * instance (main -&gt; cpuA -&gt; ...); empty when it was opened directly as a definition,
     * which may back zero, one or many live instances and therefore has no single runtime
     * state to show.
     */
    public Optional<String> activeInstancePath() {
        return view.instancePath();
    }

    /** {@code true} while viewing a definition with no concrete running instance selected. */
    public boolean isDefinitionMode() {
        return view.instancePath().isEmpty();
    }

    public boolean canNavigateBack() {
        return !navigationStack.isEmpty();
    }

    public List<String> navigationLabels() {
        List<String> labels = navigationStack.stream().map(CircuitViewContext::circuitName)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        java.util.Collections.reverse(labels);
        labels.add(view.circuitName());
        return List.copyOf(labels);
    }

    /** Opens a definition directly from the project tree: no runtime instance is implied. */
    public void openCircuit(String circuitName) {
        switchActiveCircuit(circuitName, Optional.empty(), true);
    }

    /** Opens a selected subcircuit while retaining enough context for Back and signal paths. */
    public void openSubcircuit(ComponentInstance instance) {
        if (!SubcircuitSupport.isInstanceDefinition(instance.definitionId())) {
            return;
        }
        String child = SubcircuitSupport.circuitName(instance.definitionId());
        if (project.circuit(child).isEmpty()) {
            return;
        }
        navigationStack.push(view);
        Optional<String> childPath = view.instancePath().map(path -> path + "/" + instance.id());
        switchActiveCircuit(child, childPath, false);
    }

    public void navigateBack() {
        if (navigationStack.isEmpty()) {
            return;
        }
        CircuitViewContext parent = navigationStack.pop();
        switchActiveCircuit(parent.circuitName(), parent.instancePath(), false);
    }

    /** Opens the exact concrete hierarchy containing an analyzer endpoint and selects it. */
    public boolean navigateToRuntimeEndpoint(
            dev.logicforge.compiler.RuntimeInstancePath parentPath, PortEndpoint endpoint) {
        return navigateToRuntimeEndpoint(parentPath, new ElectricalEndpoint.ComponentEndpoint(endpoint));
    }

    /**
     * Opens the exact concrete hierarchy containing an analyzer endpoint and selects it — a
     * component port or a physical chip pin.
     */
    public boolean navigateToRuntimeEndpoint(
            dev.logicforge.compiler.RuntimeInstancePath parentPath, ElectricalEndpoint endpoint) {
        if (project.circuit(parentPath.rootCircuitName()).isEmpty()) {
            return false;
        }
        switchActiveCircuit(parentPath.rootCircuitName(),
                Optional.of(parentPath.rootCircuitName()), true);
        for (java.util.UUID instanceId : parentPath.instanceIds()) {
            ComponentInstance instance = document.component(instanceId).orElse(null);
            if (instance == null || !SubcircuitSupport.isInstanceDefinition(instance.definitionId())) {
                return false;
            }
            openSubcircuit(instance);
        }
        if (endpoint instanceof ElectricalEndpoint.ComponentEndpoint component) {
            PortEndpoint port = component.port();
            if (document.component(port.componentId()).isEmpty()) {
                return false;
            }
            selection.selectComponent(port.componentId());
            document.connections().stream()
                    .filter(connection -> connection.touches(port.port()))
                    .findFirst().ifPresent(connection -> selection.selectConnection(connection.id()));
            notifyChanged();
            return true;
        }
        if (endpoint instanceof ElectricalEndpoint.ChipPinEndpoint chipPin) {
            if (document.chip(chipPin.chipInstanceId()).isEmpty()) {
                return false;
            }
            selection.selectChip(chipPin.chipInstanceId());
            document.connections().stream()
                    .filter(connection -> connection.from().equals(endpoint) || connection.to().equals(endpoint))
                    .findFirst().ifPresent(connection -> selection.selectConnection(connection.id()));
            notifyChanged();
            return true;
        }
        return false;
    }

    private void switchActiveCircuit(String circuitName, Optional<String> instancePath,
                                     boolean clearNavigation) {
        CircuitDocument next = project.circuit(circuitName).orElseThrow(() ->
                new IllegalArgumentException("Unknown circuit '" + circuitName + "'"));
        if (clearNavigation) {
            navigationStack.clear();
        }
        view = new CircuitViewContext(circuitName, instancePath);
        document = next;
        undoStack = undoStacks.computeIfAbsent(circuitName, ignored -> new UndoStack());
        selection.clear();
        notifyChanged();
    }

    /** Resolver from components/endpoints local to the open circuit to the live runtime. */
    private HierarchyRuntimeContext hierarchyContext() {
        return new HierarchyRuntimeContext(compilation, view.instancePath());
    }

    public CircuitDocument addCircuit(String circuitName) {
        CircuitDocument added = project.addCircuit(circuitName);
        added.addListener(documentListener);
        undoStacks.put(added.metadata().name(), new UndoStack());
        dirty = true;
        notifyChanged();
        openCircuit(added.metadata().name());
        return added;
    }

    public void renameCircuit(String oldName, String newName) {
        projectMutation = true;
        try {
            project.renameCircuit(oldName, newName);
        } finally {
            projectMutation = false;
        }
        String target = newName.trim();
        UndoStack history = undoStacks.remove(oldName);
        if (history != null) {
            undoStacks.put(target, history);
        }
        if (view.circuitName().equals(oldName)) {
            view = new CircuitViewContext(target, view.instancePath());
            document = project.circuit(target).orElseThrow();
            undoStack = undoStacks.get(target);
        }
        List<CircuitViewContext> updatedNavigation = navigationStack.stream()
                .map(context -> context.circuitName().equals(oldName)
                        ? new CircuitViewContext(target, context.instancePath()) : context)
                .toList();
        navigationStack.clear();
        updatedNavigation.forEach(navigationStack::addLast);
        dirty = true;
        recompilePreservingState();
        notifyChanged();
    }

    public void deleteCircuit(String circuitName) {
        CircuitDocument removed = project.circuit(circuitName).orElseThrow(() ->
                new IllegalArgumentException("Unknown circuit '" + circuitName + "'"));
        removed.removeListener(documentListener);
        project.removeCircuit(circuitName);
        undoStacks.remove(circuitName);
        navigationStack.removeIf(context -> context.circuitName().equals(circuitName));
        if (view.circuitName().equals(circuitName)) {
            navigationStack.clear();
            view = new CircuitViewContext(CircuitProject.MAIN_CIRCUIT,
                    Optional.of(CircuitProject.MAIN_CIRCUIT));
            document = project.mainCircuit();
            undoStack = undoStacks.get(CircuitProject.MAIN_CIRCUIT);
            selection.clear();
        }
        dirty = true;
        recompilePreservingState();
        notifyChanged();
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
        retainExistingSelection();
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
        retainExistingSelection();
        notifyChanged();
    }

    private void retainExistingSelection() {
        selection.retainExisting(document.components().stream().map(ComponentInstance::id).toList(),
                document.chips().stream().map(dev.logicforge.circuit.chip.ChipInstance::id).toList(),
                document.connections().stream().map(connection -> connection.id()).toList());
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

    public Optional<dev.logicforge.circuit.chip.ChipDefinition> chipDefinitionOf(
            dev.logicforge.circuit.chip.ChipInstance instance) {
        return chipRegistry.find(instance.chipDefinitionId());
    }

    // ------------------------------------------------------------------
    // Simulation
    // ------------------------------------------------------------------

    public Optional<Simulation> simulation() {
        return Optional.ofNullable(simulation);
    }

    public Optional<SimulationSession> simulationSession() {
        return Optional.ofNullable(simulationSession);
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
        return simulationSession == null ? desiredRunning : simulationSession.isRunning();
    }

    public void setRunning(boolean running) {
        desiredRunning = running;
        if (simulationSession != null) {
            guarded(() -> simulationSession.setRunning(running));
        }
    }

    public void step() {
        if (simulationSession != null) {
            guarded(simulationSession::stepEvent);
        }
    }

    /**
     * Jumps straight to the next scheduled event, wherever in virtual time that is, and
     * settles there — how a clocked circuit is advanced without single-stepping every
     * intervening delta cycle.
     */
    public void stepTime() {
        if (simulationSession != null) {
            guarded(simulationSession::stepTime);
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
            publishSimulationChange();
        }
    }

    /** Puts the simulation back into its initial state without touching the circuit. */
    public void resetSimulation() {
        if (simulationSession != null) {
            guarded(simulationSession::reset);
        }
    }

    /** The value currently on the net a port is attached to. */
    public Optional<LogicVector> valueAt(PortReference port) {
        return valueAt(PortEndpoint.whole(port));
    }

    /**
     * Value at a whole, bit or range endpoint. A RANGE (or a WHOLE read of a bit-mode port)
     * may genuinely span several independent nets — see {@link
     * dev.logicforge.compiler.ResolvedSignal} — so this always resolves through {@link
     * #signalAt} rather than assuming one net.
     */
    public Optional<LogicVector> valueAt(PortEndpoint endpoint) {
        if (simulation == null) {
            return Optional.empty();
        }
        return signalAt(endpoint).map(signal -> signal.read(simulation));
    }

    /** The runtime net(s) backing a local port endpoint; see {@link #valueAt(PortEndpoint)}. */
    public Optional<dev.logicforge.compiler.ResolvedSignal> signalAt(PortEndpoint endpoint) {
        return compilation == null ? Optional.empty() : hierarchyContext().resolveSignal(endpoint);
    }

    /**
     * The runtime net(s) backing any electrical endpoint — a component port or a physical
     * chip pin. A chip pin is resolved to the logical port it was expanded onto via the
     * compiler's {@code ChipSourceMap} and then handled exactly like an ordinary port.
     */
    public Optional<dev.logicforge.compiler.ResolvedSignal> signalAt(ElectricalEndpoint endpoint) {
        if (endpoint instanceof ElectricalEndpoint.ComponentEndpoint component) {
            return signalAt(component.port());
        }
        if (endpoint instanceof ElectricalEndpoint.ChipPinEndpoint chipPin) {
            return logicalEndpointForChipPin(chipPin.chipInstanceId(), chipPin.physicalPinNumber())
                    .flatMap(this::signalAt);
        }
        return Optional.empty();
    }

    /** The value on the net a chip's physical pin is attached to. */
    public Optional<LogicVector> valueAt(ElectricalEndpoint endpoint) {
        if (simulation == null) {
            return Optional.empty();
        }
        return signalAt(endpoint).map(signal -> signal.read(simulation));
    }

    /**
     * The logical port a physical chip pin was expanded onto during compilation, for the
     * chip instance currently in the root of this editor's document — see
     * {@link #chipSourceMap()} for the hierarchy-aware variant.
     */
    public Optional<PortEndpoint> logicalEndpointForChipPin(UUID chipInstanceId, int physicalPinNumber) {
        return compilation == null ? Optional.empty()
                : compilation.chipSourceMap().logicalEndpointForPin(chipInstanceId, physicalPinNumber);
    }

    /** The compiler's chip source map for the currently compiled project, if any. */
    public Optional<dev.logicforge.compiler.ChipSourceMap> chipSourceMap() {
        return compilation == null ? Optional.empty() : Optional.of(compilation.chipSourceMap());
    }

    /**
     * Resolves against an explicitly given hierarchy instance path rather than whatever
     * circuit the editor currently has open — see {@link #memorySnapshot(Optional, UUID)} for
     * why a long-lived watch (a logic analyzer trace) must capture its instance path instead
     * of relying on the editor's ambient current view, which changes as the user navigates.
     */
    public Optional<dev.logicforge.compiler.ResolvedSignal> signalAt(
            Optional<String> instancePath, PortEndpoint endpoint) {
        return compilation == null ? Optional.empty()
                : new HierarchyRuntimeContext(compilation, instancePath).resolveSignal(endpoint);
    }

    /**
     * Hierarchy-path-aware counterpart of {@link #signalAt(ElectricalEndpoint)} for a
     * long-lived watch (a logic analyzer trace) that must capture its instance path instead of
     * relying on the editor's ambient current view. A chip pin resolves through the compiler's
     * {@code ChipSourceMap} exactly like the ambient overload — chips inside a nested
     * subcircuit are not addressable this way yet, only chips in the compiled circuit's own
     * root; see {@link #logicalEndpointForChipPin(UUID, int)}.
     */
    public Optional<dev.logicforge.compiler.ResolvedSignal> signalAt(
            Optional<String> instancePath, ElectricalEndpoint endpoint) {
        if (endpoint instanceof ElectricalEndpoint.ComponentEndpoint component) {
            return signalAt(instancePath, component.port());
        }
        if (endpoint instanceof ElectricalEndpoint.ChipPinEndpoint chipPin) {
            return logicalEndpointForChipPin(chipPin.chipInstanceId(), chipPin.physicalPinNumber())
                    .flatMap(logical -> signalAt(instancePath, logical));
        }
        return Optional.empty();
    }

    /** The value on a wire, for drawing it in the colour of its signal. */
    public Optional<LogicVector> valueOfConnection(UUID connectionId) {
        if (simulation == null) {
            return Optional.empty();
        }
        return signalOfConnection(connectionId).map(signal -> signal.read(simulation));
    }

    public OptionalInt netOf(PortReference port) {
        return hierarchyContext().resolveNet(port);
    }

    /**
     * The net carrying a whole or bit endpoint. Kept for the common single-net case (most
     * ports are whole-mode); a RANGE endpoint, or any endpoint on a bit-mode port, may span
     * several nets and is not representable as one — use {@link #signalAt} for those.
     */
    public OptionalInt netOf(PortEndpoint endpoint) {
        return hierarchyContext().resolveNet(endpoint);
    }

    /** Resolves an absolute hierarchy path (e.g. from a stable analyzer watch) directly. */
    public OptionalInt netOfHierarchyPath(String endpointPath) {
        return compilation == null ? OptionalInt.empty()
                : compilation.hierarchySourceMap().netId(endpointPath);
    }

    /**
     * The net a wire belongs to. Root-level connection ids pass straight through the flat
     * source map; a connection local to a nested circuit has no such direct mapping (only
     * root wire ids survive flattening unchanged), so it is resolved via either endpoint
     * through the hierarchy instance instead. Kept for the common single-net case; a wire
     * between two RANGE endpoints may span several nets — see {@link #signalOfConnection}.
     */
    public OptionalInt netOfConnection(UUID connectionId) {
        if (compilation == null) {
            return OptionalInt.empty();
        }
        OptionalInt direct = compilation.sourceMap().netOfConnection(connectionId);
        if (direct.isPresent()) {
            return direct;
        }
        Optional<dev.logicforge.circuit.document.Connection> connection =
                document.connection(connectionId);
        if (connection.isEmpty()) {
            return OptionalInt.empty();
        }
        OptionalInt fromNet = resolveNet(connection.get().from());
        return fromNet.isPresent() ? fromNet : resolveNet(connection.get().to());
    }

    private OptionalInt resolveNet(ElectricalEndpoint endpoint) {
        if (endpoint instanceof ElectricalEndpoint.ComponentEndpoint component) {
            return hierarchyContext().resolveNet(component.port());
        }
        if (endpoint instanceof ElectricalEndpoint.ChipPinEndpoint chipPin) {
            Optional<PortEndpoint> logical = logicalEndpointForChipPin(chipPin.chipInstanceId(),
                    chipPin.physicalPinNumber());
            return logical.map(hierarchyContext()::resolveNet).orElse(OptionalInt.empty());
        }
        return OptionalInt.empty();
    }

    /**
     * The runtime net(s) a wire belongs to, resolved through either of its endpoints (both
     * name the same signal by construction) — a component port or a physical chip pin.
     * Correct for a RANGE connection regardless of whether it spans one net (a slice of a
     * whole-mode bus) or several (a bit-mode port).
     */
    public Optional<dev.logicforge.compiler.ResolvedSignal> signalOfConnection(UUID connectionId) {
        if (compilation == null) {
            return Optional.empty();
        }
        return document.connection(connectionId).flatMap(connection -> {
            Optional<dev.logicforge.compiler.ResolvedSignal> fromSignal = signalAt(connection.from());
            return fromSignal.isPresent() ? fromSignal : signalAt(connection.to());
        });
    }

    public boolean hasDriverConflict(int netId) {
        return simulation != null && simulation.hasDriverConflict(netId);
    }

    /** {@code true} if any net backing this wire currently has conflicting drivers. */
    public boolean hasDriverConflict(dev.logicforge.compiler.ResolvedSignal signal) {
        return simulation != null && signal.hasDriverConflict(simulation);
    }

    /**
     * The width in bits of the wire's signal — correct for a RANGE connection, which may
     * span several nets and so cannot be answered from a single net's width alone.
     */
    public int connectionWidth(UUID connectionId) {
        return signalOfConnection(connectionId).map(dev.logicforge.compiler.ResolvedSignal::width).orElse(0);
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
        OptionalInt runtimeId = hierarchyContext().resolveComponent(componentId);
        if (runtimeId.isEmpty()) {
            return;
        }
        guarded(() -> simulation.setInput(runtimeId.getAsInt(), value));
        publishSimulationChange();
    }

    public Optional<LogicState> inputValueOf(UUID componentId) {
        return inputStateOf(componentId).map(state -> state.value().singleBit());
    }

    private Optional<InputSourceState> inputStateOf(UUID componentId) {
        if (simulation == null || compilation == null) {
            return Optional.empty();
        }
        OptionalInt runtimeId = hierarchyContext().resolveComponent(componentId);
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
        if (projectMutation) {
            return;
        }
        if (change.affectsTopology()) {
            recompilePreservingState();
        }
        notifyChanged();
    }

    private void recompilePreservingState() {
        Map<UUID, LogicVector> previousInputs = captureInputValues();
        Map<UUID, Object> previousStates = captureRuntimeStates();
        recompile(previousInputs, previousStates);
    }

    private void recompile(Map<UUID, LogicVector> previousInputs, Map<UUID, Object> previousStates) {
        try {
            compilation = compiler.compile(project, CircuitProject.MAIN_CIRCUIT);
            compileError = null;
            lastValidationIssues = compilation.issues();
            simulation = new Simulation(compilation.circuit(), false);
            restoreInputValues(previousInputs);
            // Let source outputs and their nets settle before restoring sequential state.
            // Otherwise a restored register can observe a temporary Z on RESET during the
            // all-components re-evaluation and conservatively (but spuriously) latch X.
            simulation.runUntilStableAtCurrentTime();
            restoreRuntimeStates(previousStates);
            simulation.reevaluateAllAtCurrentTime();
            simulation.runUntilStableAtCurrentTime();
            simulation.setRunning(desiredRunning);
            if (simulationSession == null) {
                bindSession(new SimulationSession(simulation));
            } else {
                simulationSession.replaceSimulation(simulation);
            }
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
        return memorySnapshot(view.instancePath(), componentId);
    }

    /**
     * Resolves against an explicitly given hierarchy instance path rather than whatever
     * circuit the editor currently has open. A long-lived widget like {@link
     * dev.logicforge.ui.view.MemoryView} must capture the instance path active when it was
     * opened and keep using it — the component id alone is ambiguous once the user
     * navigates elsewhere, since the same shared child-circuit document (and so the same
     * local UUID) may back a different live instance there.
     */
    public java.util.Optional<dev.logicforge.simulation.MemorySnapshot> memorySnapshot(
            Optional<String> instancePath, java.util.UUID componentId) {
        if (simulation == null || compilation == null) return java.util.Optional.empty();
        java.util.OptionalInt runtimeId = new HierarchyRuntimeContext(compilation, instancePath)
                .resolveComponent(componentId);
        if (runtimeId.isEmpty()) return java.util.Optional.empty();
        return simulation.memorySnapshot(runtimeId.getAsInt());
    }

    /** @see #writeMemoryWord(ComponentViewTarget, int, LogicVector) */
    public java.util.Optional<dev.logicforge.simulation.MemorySnapshot> memorySnapshot(ComponentViewTarget target) {
        return memorySnapshot(target.instancePath(), target.componentId());
    }

    /**
     * Cheap memory metadata (size, content/access revision, last access) with no contents
     * array — the primary signal a viewer like {@link dev.logicforge.ui.view.MemoryView}
     * should poll on every editor change, reserving {@link #memorySnapshot} and {@link
     * #memoryPage} for when the actual words are needed.
     */
    public java.util.Optional<dev.logicforge.simulation.MemoryInfo> memoryInfo(UUID componentId) {
        return memoryInfo(view.instancePath(), componentId);
    }

    /** @see #memorySnapshot(Optional, UUID) */
    public java.util.Optional<dev.logicforge.simulation.MemoryInfo> memoryInfo(
            Optional<String> instancePath, UUID componentId) {
        if (simulation == null || compilation == null) return java.util.Optional.empty();
        OptionalInt runtimeId = new HierarchyRuntimeContext(compilation, instancePath).resolveComponent(componentId);
        return runtimeId.isEmpty() ? java.util.Optional.empty() : simulation.memoryInfo(runtimeId.getAsInt());
    }

    /** @see #writeMemoryWord(ComponentViewTarget, int, LogicVector) */
    public java.util.Optional<dev.logicforge.simulation.MemoryInfo> memoryInfo(ComponentViewTarget target) {
        return memoryInfo(target.instancePath(), target.componentId());
    }

    /**
     * A window of a memory's contents, for paging through a large RAM/ROM without ever
     * cloning more than one page's worth of words.
     */
    public java.util.Optional<dev.logicforge.simulation.MemoryPageSnapshot> memoryPage(
            UUID componentId, int startAddress, int count) {
        return memoryPage(view.instancePath(), componentId, startAddress, count);
    }

    /** @see #memorySnapshot(Optional, UUID) */
    public java.util.Optional<dev.logicforge.simulation.MemoryPageSnapshot> memoryPage(
            Optional<String> instancePath, UUID componentId, int startAddress, int count) {
        if (simulation == null || compilation == null) return java.util.Optional.empty();
        OptionalInt runtimeId = new HierarchyRuntimeContext(compilation, instancePath).resolveComponent(componentId);
        return runtimeId.isEmpty() ? java.util.Optional.empty()
                : simulation.memoryPage(runtimeId.getAsInt(), startAddress, count);
    }

    /** @see #writeMemoryWord(ComponentViewTarget, int, LogicVector) */
    public java.util.Optional<dev.logicforge.simulation.MemoryPageSnapshot> memoryPage(
            ComponentViewTarget target, int startAddress, int count) {
        return memoryPage(target.instancePath(), target.componentId(), startAddress, count);
    }

    /** A stable target capturing the circuit and hierarchy instance a component is opened
     *  in right now, for a widget that must keep addressing it after the editor navigates
     *  elsewhere. See {@link #writeMemoryWord(ComponentViewTarget, int, LogicVector)}. */
    public ComponentViewTarget viewTarget(UUID componentId) {
        return new ComponentViewTarget(view.circuitName(), view.instancePath(), componentId);
    }

    public long memoryRevision(UUID componentId) {
        return memoryRevision(view.instancePath(), componentId);
    }

    /** @see #writeMemoryWord(ComponentViewTarget, int, LogicVector) */
    public long memoryRevision(ComponentViewTarget target) {
        return memoryRevision(target.instancePath(), target.componentId());
    }

    /** @see #writeMemoryWord(ComponentViewTarget, int, LogicVector) */
    public java.util.Optional<dev.logicforge.simulation.ComponentDebugSnapshot> debugSnapshot(
            ComponentViewTarget target) {
        return debugSnapshot(target.instancePath(), target.componentId());
    }

    /** @see #memorySnapshot(Optional, UUID) */
    public long memoryRevision(Optional<String> instancePath, UUID componentId) {
        if (simulation == null || compilation == null) return -1;
        OptionalInt runtimeId = new HierarchyRuntimeContext(compilation, instancePath).resolveComponent(componentId);
        return runtimeId.isEmpty() ? -1 : simulation.memoryRevision(runtimeId.getAsInt());
    }

    public java.util.Optional<dev.logicforge.simulation.ComponentDebugSnapshot> debugSnapshot(UUID componentId) {
        return debugSnapshot(view.instancePath(), componentId);
    }

    /** @see #memorySnapshot(Optional, UUID) */
    public java.util.Optional<dev.logicforge.simulation.ComponentDebugSnapshot> debugSnapshot(
            Optional<String> instancePath, UUID componentId) {
        if (simulation == null || compilation == null) return java.util.Optional.empty();
        OptionalInt runtimeId = new HierarchyRuntimeContext(compilation, instancePath).resolveComponent(componentId);
        if (runtimeId.isEmpty()) return java.util.Optional.empty();
        var snapshot = simulation.debugSnapshot(runtimeId.getAsInt());
        return snapshot.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(snapshot);
    }

    public void writeMemoryWord(java.util.UUID componentId, int address, dev.logicforge.logic.LogicVector value) {
        writeMemoryWord(document, view.instancePath(), componentId, address, value);
    }

    /** @see #memorySnapshot(Optional, UUID) */
    public void writeMemoryWord(Optional<String> instancePath, java.util.UUID componentId, int address,
                               dev.logicforge.logic.LogicVector value) {
        writeMemoryWord(document, instancePath, componentId, address, value);
    }

    /**
     * Resolves against an explicit target rather than the editor's ambient current circuit
     * and instance path — the correct call for a long-lived widget like {@link
     * dev.logicforge.ui.view.MemoryView} that must stay editable after the user navigates
     * elsewhere. A project-backed ROM's contents live on {@code target.circuitName()}'s own
     * definition document, not whichever document the editor currently has open; a runtime
     * RAM write is resolved against {@code target.instancePath()}, not the current view.
     */
    public void writeMemoryWord(ComponentViewTarget target, int address, dev.logicforge.logic.LogicVector value) {
        project.circuit(target.circuitName()).ifPresent(owner ->
                writeMemoryWord(owner, target.instancePath(), target.componentId(), address, value));
    }

    private void writeMemoryWord(CircuitDocument owner, Optional<String> instancePath, java.util.UUID componentId,
                                 int address, dev.logicforge.logic.LogicVector value) {
        owner.component(componentId).ifPresent(instance -> {
            if (instance.parameters().asMap().containsKey(LibraryParameters.ROM_CONTENTS.key())) {
                memorySnapshot(instancePath, componentId).ifPresent(snapshot -> {
                    List<LogicVector> words = new ArrayList<>();
                    for (int i = 0; i < snapshot.size(); i++) {
                        words.add(i == address ? value : snapshot.wordAt(i));
                    }
                    updateRomContents(owner, instance, words);
                });
                return;
            }
            if (simulation == null || compilation == null) return;
            java.util.OptionalInt runtimeId = new HierarchyRuntimeContext(compilation, instancePath)
                    .resolveComponent(componentId);
            if (runtimeId.isEmpty()) return;
            guarded(() -> simulation.writeMemoryWord(runtimeId.getAsInt(), address, value));
            publishSimulationChange();
        });
    }

    /** Loads complete contents into project-backed ROM or live RAM. */
    public void loadMemory(UUID componentId, List<LogicVector> words) {
        loadMemory(document, view.instancePath(), componentId, words);
    }

    /** @see #memorySnapshot(Optional, UUID) */
    public void loadMemory(Optional<String> instancePath, UUID componentId, List<LogicVector> words) {
        loadMemory(document, instancePath, componentId, words);
    }

    /** @see #writeMemoryWord(ComponentViewTarget, int, LogicVector) */
    public void loadMemory(ComponentViewTarget target, List<LogicVector> words) {
        project.circuit(target.circuitName()).ifPresent(owner ->
                loadMemory(owner, target.instancePath(), target.componentId(), words));
    }

    private void loadMemory(CircuitDocument owner, Optional<String> instancePath, UUID componentId,
                            List<LogicVector> words) {
        owner.component(componentId).ifPresent(instance -> {
            if (instance.parameters().asMap().containsKey(LibraryParameters.ROM_CONTENTS.key())) {
                updateRomContents(owner, instance, words);
                return;
            }
            if (simulation == null || compilation == null) return;
            OptionalInt runtimeId = new HierarchyRuntimeContext(compilation, instancePath).resolveComponent(componentId);
            if (runtimeId.isEmpty()) return;
            int id = runtimeId.getAsInt();
            for (int address = 0; address < words.size(); address++) {
                compilation.circuit().component(id).behavior().writeMemoryWord(
                        simulation.stateOf(id), address, words.get(address));
            }
            guarded(() -> simulation.reevaluateComponent(id));
            publishSimulationChange();
        });
    }

    /**
     * Edits a project-backed ROM's contents parameter on its own definition document and
     * undo history — {@code owner} may not be the circuit currently open in the editor, in
     * which case this must not go through {@link #execute}, which would push onto the
     * wrong undo stack and touch a selection that belongs to a different document.
     */
    private void updateRomContents(CircuitDocument owner, ComponentInstance instance, List<LogicVector> words) {
        String csv = words.stream().map(word -> word.toUnsignedLong().isPresent()
                        ? Long.toHexString(word.toUnsignedLong().getAsLong()).toUpperCase(java.util.Locale.ROOT)
                        : "0")
                .collect(java.util.stream.Collectors.joining(","));
        definitionOf(instance).ifPresent(definition -> {
            ChangeParameterCommand command = new ChangeParameterCommand(owner, definition,
                    owner.requireComponent(instance.id()), LibraryParameters.ROM_CONTENTS.key(), csv);
            if (owner == document) {
                execute(command);
            } else {
                undoStacks.computeIfAbsent(owner.metadata().name(), ignored -> new UndoStack()).execute(command);
                notifyChanged();
            }
        });
    }

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    private void bindSession(SimulationSession session) {
        if (simulationSession != null) {
            simulationSession.removeListener(simulationSessionListener);
        }
        simulationSession = java.util.Objects.requireNonNull(session, "session");
        simulationSession.addListener(simulationSessionListener);
        simulation = simulationSession.simulation();
        desiredRunning = simulationSession.isRunning();
    }

    private void onSessionChanged() {
        simulation = simulationSession.simulation();
        desiredRunning = simulationSession.isRunning();
        notifyChanged();
    }

    private void publishSimulationChange() {
        if (simulationSession != null) {
            simulationSession.simulationChanged();
        } else {
            notifyChanged();
        }
    }

    private void notifyChanged() {
        changeListeners.forEach(Runnable::run);
    }

    /**
     * Which circuit definition is open, and — when reached by descending into a concrete
     * instance rather than opened directly from the project tree — the hierarchy instance
     * path identifying which one of its (possibly several) runtime copies is live.
     */
    private record CircuitViewContext(String circuitName, Optional<String> instancePath) {
    }
}
