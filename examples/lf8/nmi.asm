; Assemble with NMI vector 0x0120. NMI is accepted independently of IE.
wait:
    NOP
    JMP wait

    .org 0x0120
nmi_handler:
    LDI R0, 0x4e
    STORE R0, 0x8000
    IRET
