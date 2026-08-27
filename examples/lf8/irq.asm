; Assemble with IRQ vector 0x0100. Assert the external IRQ input after EI.
    EI
wait:
    NOP
    JMP wait

    .org 0x0100
irq_handler:
    LDI R0, 1
    STORE R0, 0x8000
    IRET
