package dev.logicforge.processor.lf8.runtime;

import dev.logicforge.compiler.RuntimeInstancePath;
import java.util.Objects;

public record MemoryWriteBreakpoint(RuntimeInstancePath cpuPath, int firstAddress,
                                    int lastAddress, boolean enabled) implements Breakpoint {
    public MemoryWriteBreakpoint {
        Objects.requireNonNull(cpuPath, "cpuPath");
        BreakpointRanges.require(firstAddress, lastAddress);
    }

    public MemoryWriteBreakpoint(RuntimeInstancePath cpuPath, int address) {
        this(cpuPath, address, address, true);
    }
}
