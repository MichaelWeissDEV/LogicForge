package dev.logicforge.processor.lf8.runtime;

final class BreakpointRanges {
    private BreakpointRanges() {
    }

    static void require(int first, int last) {
        if (first < 0 || last > 0xffff || first > last) {
            throw new IllegalArgumentException("Memory breakpoint range must be within 0..65535");
        }
    }
}
