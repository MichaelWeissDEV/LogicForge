package dev.logicforge.processor.lf8.runtime;

import dev.logicforge.compiler.RuntimeInstancePath;
import java.util.Objects;

public record PcBreakpoint(RuntimeInstancePath cpuPath, int address, boolean enabled)
        implements Breakpoint {
    public PcBreakpoint {
        Objects.requireNonNull(cpuPath, "cpuPath");
        requireAddress(address);
    }

    public PcBreakpoint(RuntimeInstancePath cpuPath, int address) {
        this(cpuPath, address, true);
    }

    private static void requireAddress(int address) {
        if (address < 0 || address > 0xffff) {
            throw new IllegalArgumentException("PC breakpoint address must be in 0..65535");
        }
    }
}
