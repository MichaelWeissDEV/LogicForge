package dev.logicforge.processor.lf8;

/** Selects how far the LF-8 datapath is decomposed while preserving one ISA and microcode. */
public enum Lf8ImplementationMode {
    /** Existing high-performance behavioral datapath blocks. */
    FAST,
    /** Hierarchical CPU with structural ALU and register-file state decomposed to gates. */
    STRUCTURAL,
    /** Reserved for replacing the remaining datapath registers/counters with gate-level forms. */
    GATE_LEVEL
}
