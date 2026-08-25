package dev.logicforge.ui.edit;

import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.compiler.CircuitFlattener;
import dev.logicforge.compiler.CompilationResult;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Resolves a component or port endpoint local to whichever circuit definition is open
 * into the runtime id/net of one concrete hierarchy instance.
 *
 * <p>The same child circuit document can be instantiated more than once (e.g. two CPUs in
 * one main circuit), so a local component UUID alone is ambiguous: it must be combined with
 * the hierarchy instance path of the parent the user actually navigated through before it
 * can be resolved to one specific runtime id. A definition opened directly from the project
 * tree has no such path — it may back zero, one or many live instances — so every
 * resolution correctly comes back empty there rather than guessing which instance to show.
 *
 * <p>Root-level lookups go straight through the flat {@code CircuitSourceMap} first, since
 * that also covers ports with no connection (an unwired port still gets a net, but never
 * gets a hierarchical path entry — those exist only for endpoints that appear in a wire).
 * The hierarchical path is the fallback, used for anything nested inside a subcircuit
 * instance.
 */
public final class HierarchyRuntimeContext {

    private static final HierarchyRuntimeContext NONE =
            new HierarchyRuntimeContext(null, Optional.empty());

    private final CompilationResult compilation;
    private final Optional<String> instancePath;

    public HierarchyRuntimeContext(CompilationResult compilation, Optional<String> instancePath) {
        this.compilation = compilation;
        this.instancePath = instancePath == null ? Optional.empty() : instancePath;
    }

    public static HierarchyRuntimeContext none() {
        return NONE;
    }

    /** {@code false} while viewing a definition with no concrete running instance selected. */
    public boolean hasRuntimeInstance() {
        return compilation != null && instancePath.isPresent();
    }

    public Optional<String> instancePath() {
        return instancePath;
    }

    /** The runtime id of a component local to the open circuit. */
    public OptionalInt resolveComponent(UUID localComponentId) {
        if (compilation == null) {
            return OptionalInt.empty();
        }
        OptionalInt direct = compilation.sourceMap().componentId(localComponentId);
        if (direct.isPresent()) {
            return direct;
        }
        return instancePath.isEmpty() ? OptionalInt.empty()
                : compilation.hierarchySourceMap().componentId(
                        instancePath.get() + "/" + localComponentId);
    }

    /** The net carrying a local port endpoint (whole, bit or range). */
    public OptionalInt resolveNet(PortEndpoint localEndpoint) {
        if (compilation == null) {
            return OptionalInt.empty();
        }
        OptionalInt direct = compilation.sourceMap().netOf(localEndpoint);
        if (direct.isPresent()) {
            return direct;
        }
        return instancePath.isEmpty() ? OptionalInt.empty()
                : compilation.hierarchySourceMap().netId(
                        CircuitFlattener.endpointPath(instancePath.get(), localEndpoint));
    }

    /** The net a whole port is attached to. */
    public OptionalInt resolveNet(PortReference localPort) {
        if (compilation == null) {
            return OptionalInt.empty();
        }
        OptionalInt direct = compilation.sourceMap().netOf(localPort);
        return direct.isPresent() ? direct : resolveNet(PortEndpoint.whole(localPort));
    }

    /**
     * Canonical, hierarchy-stable path for a local endpoint — the identity a watch or a
     * debugger should key on so it survives recompilation instead of drifting onto whatever
     * happens to occupy the same local UUID afterwards. Empty in definition mode.
     */
    public Optional<String> canonicalPath(PortEndpoint localEndpoint) {
        return instancePath.map(path -> CircuitFlattener.endpointPath(path, localEndpoint));
    }
}
