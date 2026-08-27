; Write a byte to the shared output/character-output MMIO address.
    LDI R0, 0x41
    STORE R0, 0xc000
    LOAD R1, 0xc001
    STORE R1, 0x8000
    HLT
