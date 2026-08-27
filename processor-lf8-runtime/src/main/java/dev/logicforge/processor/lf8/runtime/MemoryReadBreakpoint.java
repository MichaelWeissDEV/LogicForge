package dev.logicforge.processor.lf8.runtime;

import dev.logicforge.compiler.RuntimeInstancePath;
import java.util.Objects;

public record MemoryReadBreakpoint(RuntimeInstancePath cpuPath, int firstAddress,
                                   int lastAddress, boolean enabled) implements Breakpoint {
    public MemoryReadBreakpoint {
        Objects.requireNonNull(cpuPath, "cpuPath");
        BreakpointRanges.require(firstAddress, lastAddress);
    }

    public MemoryReadBreakpoint(RuntimeInstancePath cpuPath, int address) {
        this(cpuPath, address, address, true);
    }
}
