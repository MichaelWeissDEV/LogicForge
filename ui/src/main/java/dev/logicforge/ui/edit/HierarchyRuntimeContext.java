package dev.logicforge.ui.edit;

import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.PortSlice;
import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.compiler.CircuitFlattener;
import dev.logicforge.compiler.CompilationResult;
import dev.logicforge.compiler.ResolvedSignal;
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
 * <p>Nested lookups never use a document-local child UUID as a key in the flat source map.
 * Connected endpoints resolve by their full hierarchy path. For an unconnected port, the
 * local component first resolves through that same path to a runtime component id, which is
 * translated to the generated flattened UUID before its port net is looked up. Root
 * {@code main} is the only context where document UUIDs are already flat UUIDs and may be
 * looked up directly.
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

    /**
     * The runtime id of a component local to the open circuit. Definition mode
     * ({@code instancePath} empty) never resolves anything — there is no single live
     * instance to point at. Root component UUIDs are already flat; nested UUIDs must resolve
     * exclusively through their full hierarchy path.
     */
    public OptionalInt resolveComponent(UUID localComponentId) {
        if (compilation == null || instancePath.isEmpty()) {
            return OptionalInt.empty();
        }
        if (isRoot()) {
            return compilation.sourceMap().componentId(localComponentId);
        }
        return compilation.hierarchySourceMap()
                .componentId(instancePath.get() + "/" + localComponentId);
    }

    /**
     * The net carrying a local port endpoint (whole, bit or range). See the class
     * documentation for the nested unconnected-port translation.
     */
    public OptionalInt resolveNet(PortEndpoint localEndpoint) {
        if (compilation == null || instancePath.isEmpty()) {
            return OptionalInt.empty();
        }
        if (isRoot()) {
            return compilation.sourceMap().netOf(localEndpoint);
        }
        OptionalInt hierarchical = compilation.hierarchySourceMap().netId(
                CircuitFlattener.endpointPath(instancePath.get(), localEndpoint));
        if (hierarchical.isPresent()) {
            return hierarchical;
        }
        return flattenedEndpoint(localEndpoint).stream()
                .mapToInt(endpoint -> {
                    OptionalInt exact = compilation.sourceMap().netOf(endpoint);
                    if (exact.isPresent()) {
                        return exact.getAsInt();
                    }
                    if (!compilation.sourceMap().isBitMode(endpoint.port())) {
                        return compilation.sourceMap().netOf(endpoint.port()).orElse(-1);
                    }
                    return -1;
                })
                .filter(net -> net >= 0)
                .findFirst();
    }

    /** The net a whole port is attached to. */
    public OptionalInt resolveNet(PortReference localPort) {
        return resolveNet(PortEndpoint.whole(localPort));
    }

    /**
     * Canonical, hierarchy-stable path for a local endpoint — the identity a watch or a
     * debugger should key on so it survives recompilation instead of drifting onto whatever
     * happens to occupy the same local UUID afterwards. Empty in definition mode.
     */
    public Optional<String> canonicalPath(PortEndpoint localEndpoint) {
        return instancePath.map(path -> CircuitFlattener.endpointPath(path, localEndpoint));
    }

    /**
     * Resolves a local port endpoint to the runtime net(s) that actually carry its value.
     *
     * <p>A port is either entirely "whole-net" (one net for the full port, because every
     * connection to it is whole-port) or entirely "bit-mode" (every bit its own net,
     * because at least one connection touches a bit or a range) — the compiler rejects a
     * port with both kinds of connection, so this is exhaustive, not a heuristic.
     *
     * <ul>
     *   <li>Whole-net port: any endpoint (whole, bit or range) is a slice of that one net
     *       — {@link ResolvedSignal.VectorNet}.
     *   <li>Bit-mode port, a single bit: that bit's own dedicated net —
     *       {@link ResolvedSignal.ScalarNet}.
     *   <li>Bit-mode port, a range or whole read: reconstructed from each bit's own net —
     *       {@link ResolvedSignal.BitVector}.
     * </ul>
     */
    public Optional<ResolvedSignal> resolveSignal(PortEndpoint localEndpoint) {
        if (compilation == null) {
            return Optional.empty();
        }
        PortReference port = localEndpoint.port();
        OptionalInt wholeNet = resolveNet(PortEndpoint.whole(port));
        if (wholeNet.isPresent()) {
            int netId = wholeNet.getAsInt();
            int netWidth = compilation.circuit().net(netId).width().bits();
            int offset;
            int width;
            switch (localEndpoint.slice()) {
                case PortSlice.Whole ignored -> {
                    offset = 0;
                    width = netWidth;
                }
                case PortSlice.Bit bit -> {
                    offset = bit.index();
                    width = 1;
                }
                case PortSlice.Range range -> {
                    offset = range.lsb();
                    width = range.width();
                }
                default -> throw new IllegalStateException("Unreachable: " + localEndpoint.slice());
            }
            if (offset < 0 || offset + width > netWidth) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedSignal.VectorNet(netId, netWidth, offset, width));
        }
        return switch (localEndpoint.slice()) {
            case PortSlice.Bit bit -> resolveNet(PortEndpoint.bit(port, bit.index()))
                    .stream().mapToObj(net -> (ResolvedSignal) new ResolvedSignal.ScalarNet(net)).findFirst();
            case PortSlice.Range range -> resolveBitVector(port, range.lsb(), range.width());
            case PortSlice.Whole ignored -> resolveBitVector(port, 0, declaredWidth(port));
        };
    }

    /**
     * The port's declared bit width, straight from the compiler's {@code CircuitSourceMap} —
     * no probing. The map is keyed by the flattened document's own {@code PortReference}
     * (root components keep their original UUID there; nested ones get a deterministic
     * derived one — see {@code CircuitFlattener}), so a local port is first resolved to its
     * runtime component id and then translated back to that flattened UUID before the width
     * lookup.
     */
    private int declaredWidth(PortReference localPort) {
        OptionalInt runtimeComponentId = resolveComponent(localPort.componentId());
        if (runtimeComponentId.isEmpty()) {
            return 0;
        }
        return compilation.sourceMap().componentUuid(runtimeComponentId.getAsInt())
                .flatMap(flatId -> {
                    OptionalInt width = compilation.sourceMap()
                            .widthOf(new PortReference(flatId, localPort.portName()));
                    return width.isPresent() ? Optional.of(width.getAsInt()) : Optional.empty();
                })
                .orElse(0);
    }

    private Optional<PortEndpoint> flattenedEndpoint(PortEndpoint localEndpoint) {
        OptionalInt runtimeComponentId = resolveComponent(localEndpoint.componentId());
        if (runtimeComponentId.isEmpty()) {
            return Optional.empty();
        }
        return compilation.sourceMap().componentUuid(runtimeComponentId.getAsInt())
                .map(flatId -> new PortEndpoint(
                        new PortReference(flatId, localEndpoint.portName()), localEndpoint.slice()));
    }

    private boolean isRoot() {
        return instancePath.filter(CircuitProject.MAIN_CIRCUIT::equals).isPresent();
    }

    private Optional<ResolvedSignal> resolveBitVector(PortReference port, int lsb, int width) {
        if (width <= 0) {
            return Optional.empty();
        }
        int[] nets = new int[width];
        for (int i = 0; i < width; i++) {
            OptionalInt net = resolveNet(PortEndpoint.bit(port, lsb + i));
            if (net.isEmpty()) {
                return Optional.empty();
            }
            nets[i] = net.getAsInt();
        }
        return Optional.of(new ResolvedSignal.BitVector(nets));
    }
}
