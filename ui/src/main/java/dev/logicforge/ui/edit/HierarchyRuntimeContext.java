package dev.logicforge.ui.edit;

import dev.logicforge.circuit.document.PortEndpoint;
import dev.logicforge.circuit.document.PortReference;
import dev.logicforge.circuit.document.PortSlice;
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
 * <p>Every lookup goes through the hierarchical {@code HierarchySourceMap} first, keyed by
 * the full instance path — this is the one source that can never misresolve, because a
 * component or endpoint genuinely local to the currently-open instance always has an entry
 * there (see {@code CircuitFlattener}). Only if that misses does resolution fall back to the
 * flat, UUID-keyed {@code CircuitSourceMap}: that covers two cases the hierarchical map
 * cannot — a port with no connection (an unwired port still gets a net, but never gets a
 * hierarchical path entry — those exist only for endpoints that appear in a wire), and a
 * caller that already holds a root-scoped UUID while a nested instance happens to be open.
 * Trying the flat map <em>first</em> would be unsafe: a child circuit definition can
 * coincidentally (or adversarially) reuse a UUID that also exists at the root, and a direct
 * hit there before the hierarchical lookup runs would silently resolve to the wrong
 * component. Trying it only as a fallback avoids that — anything truly local to the open
 * instance is already found by the hierarchical lookup and never reaches the flat map.
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
     * instance to point at. Otherwise the hierarchical path is tried first and the flat map
     * only as a fallback; see the class documentation for why the order matters.
     */
    public OptionalInt resolveComponent(UUID localComponentId) {
        if (compilation == null || instancePath.isEmpty()) {
            return OptionalInt.empty();
        }
        OptionalInt hierarchical = compilation.hierarchySourceMap()
                .componentId(instancePath.get() + "/" + localComponentId);
        if (hierarchical.isPresent()) {
            return hierarchical;
        }
        return compilation.sourceMap().componentId(localComponentId);
    }

    /**
     * The net carrying a local port endpoint (whole, bit or range). See the class
     * documentation for why the hierarchical path is tried before the flat map, never after.
     */
    public OptionalInt resolveNet(PortEndpoint localEndpoint) {
        if (compilation == null || instancePath.isEmpty()) {
            return OptionalInt.empty();
        }
        OptionalInt hierarchical = compilation.hierarchySourceMap().netId(
                CircuitFlattener.endpointPath(instancePath.get(), localEndpoint));
        if (hierarchical.isPresent()) {
            return hierarchical;
        }
        return compilation.sourceMap().netOf(localEndpoint);
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

    /** Ports are never mixed whole-port and bit-level; this is a generous, cheap cap. */
    private static final int MAX_PROBE_WIDTH = 256;

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
            case PortSlice.Whole ignored -> resolveBitVector(port, 0, probeWidth(port));
        };
    }

    /** Bit-mode ports have every declared bit as its own atom regardless of wiring, so
     *  probing sequential bit indices finds the true width exactly, not heuristically. */
    private int probeWidth(PortReference port) {
        int width = 0;
        while (width < MAX_PROBE_WIDTH && resolveNet(PortEndpoint.bit(port, width)).isPresent()) {
            width++;
        }
        return width;
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
