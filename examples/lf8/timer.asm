; Configure the MMIO timer, acknowledge its IRQ, then return.
    LDI R0, 100
    STORE R0, 0xc010
    LDI R0, 0
    STORE R0, 0xc011
    LDI R0, 7
    STORE R0, 0xc012
    EI
wait:
    NOP
    JMP wait

    .org 0x0100
timer_handler:
    LDI R0, 1
    STORE R0, 0xc013
    IRET
