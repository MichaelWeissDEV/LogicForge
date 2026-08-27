; Nested calls exercise return-address storage and balanced stack movement.
    LDI R0, 1
    CALL first
    STORE R0, 0x8000
    HLT
first:
    INC R0
    CALL second
    RET
second:
    INC R0
    RET
