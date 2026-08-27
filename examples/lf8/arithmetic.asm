; Exercise arithmetic and logical operations, leaving the result in RAM.
    LDI R0, 0x55
    LDI R1, 0x0f
    ADD R0, R1
    XOR R0, R1
    SHL R0
    NEG R0
    STORE R0, 0x8000
    HLT
