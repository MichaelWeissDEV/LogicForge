; Load, add, store, halt. Result 8 is written to RAM address 0x8000.
    LDI R0, 5
    LDI R1, 3
    ADD R0, R1
    STORE R0, 0x8000
    HLT
