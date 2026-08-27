; The LF-8 stack begins at 0xBFFF and grows downward.
    LDI R0, 0x2a
    PUSH R0
    LDI R0, 0
    POP R1
    STORE R1, 0x8000
    HLT
