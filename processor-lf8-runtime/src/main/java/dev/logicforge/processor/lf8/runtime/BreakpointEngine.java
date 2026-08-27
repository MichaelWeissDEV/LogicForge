package dev.logicforge.processor.lf8.runtime;

import dev.logicforge.logic.LogicState;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Evaluates breakpoints only from concrete runtime identity and settled CPU signals. */
public final class BreakpointEngine {
    private List<Breakpoint> breakpoints = List.of();
    private final Set<Breakpoint> activeMatches = new HashSet<>();

    public void setBreakpoints(List<? extends Breakpoint> newBreakpoints) {
        breakpoints = List.copyOf(newBreakpoints);
        activeMatches.retainAll(breakpoints);
    }

    public List<Breakpoint> breakpoints() {
        return breakpoints;
    }

    /** Returns a hit once when a condition becomes true, not on every UI refresh. */
    public Optional<BreakpointHit> evaluate(Lf8RuntimeProbe probe) {
        Set<Breakpoint> matchingNow = new HashSet<>();
        for (Breakpoint breakpoint : breakpoints) {
            if (!breakpoint.enabled() || !breakpoint.cpuPath().equals(probe.cpuPath())) {
                continue;
            }
            Optional<Integer> address = addressIfMatched(breakpoint, probe);
            if (address.isPresent()) {
                matchingNow.add(breakpoint);
                if (!activeMatches.contains(breakpoint)) {
                    activeMatches.clear();
                    activeMatches.addAll(matchingNow);
                    return Optional.of(new BreakpointHit(breakpoint, address.orElseThrow()));
                }
            }
        }
        activeMatches.clear();
        activeMatches.addAll(matchingNow);
        return Optional.empty();
    }

    private static Optional<Integer> addressIfMatched(Breakpoint breakpoint,
                                                       Lf8RuntimeProbe probe) {
        if (breakpoint instanceof PcBreakpoint pc) {
            if (unsigned(probe.microstep()).orElse(-1) != 0) {
                return Optional.empty();
            }
            int address = unsigned(probe.pc()).orElse(-1);
            return address == pc.address() ? Optional.of(address) : Optional.empty();
        }
        int address = unsigned(probe.cpuPort("ADDRESS")).orElse(-1);
        if (breakpoint instanceof MemoryReadBreakpoint read) {
            return asserted(probe, "MEMORY_READ") && inRange(address,
                    read.firstAddress(), read.lastAddress()) ? Optional.of(address) : Optional.empty();
        }
        MemoryWriteBreakpoint write = (MemoryWriteBreakpoint) breakpoint;
        return asserted(probe, "MEMORY_WRITE") && inRange(address,
                write.firstAddress(), write.lastAddress()) ? Optional.of(address) : Optional.empty();
    }

    private static boolean asserted(Lf8RuntimeProbe probe, String port) {
        return probe.cpuPort(port).map(value -> value.singleBit() == LogicState.ONE).orElse(false);
    }

    private static boolean inRange(int address, int first, int last) {
        return address >= first && address <= last;
    }

    private static Optional<Integer> unsigned(Optional<dev.logicforge.logic.LogicVector> value) {
        return value.flatMap(vector -> vector.toUnsignedLong().stream().boxed().findFirst())
                .map(Long::intValue);
    }

    public record BreakpointHit(Breakpoint breakpoint, int address) {
    }
}
