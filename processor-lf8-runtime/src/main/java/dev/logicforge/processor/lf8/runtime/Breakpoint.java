package dev.logicforge.processor.lf8.runtime;

import dev.logicforge.compiler.RuntimeInstancePath;

/** Immutable headless breakpoint rooted at one concrete LF-8 CPU instance. */
public sealed interface Breakpoint permits PcBreakpoint, MemoryReadBreakpoint,
        MemoryWriteBreakpoint {
    RuntimeInstancePath cpuPath();
    boolean enabled();
}
