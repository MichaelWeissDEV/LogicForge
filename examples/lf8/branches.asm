; Count down to zero and take the final zero branch.
    LDI R0, 5
loop:
    DEC R0
    JNZ loop
    JZ done
    LDI R0, 0xff
done:
    STORE R0, 0x8000
    HLT
