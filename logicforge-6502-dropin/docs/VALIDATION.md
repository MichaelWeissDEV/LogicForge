# Validation gates

## Gate A - local source quality (done in this bundle)

- headless modules compile with `javac -Xlint:all -Xlint:-serial`
- 151 documented opcode cases are present
- representative execution smoke test passes
- all 151 documented opcodes enter a valid implementation path
- exhaustive valid-BCD ADC/SBC accumulator+carry sweep passes
- switch-level inverter test passes
- integration adapter syntax-checks against the currently inspected LogicForge interfaces

## Gate B - functional 6502 reference tests (must be run after integration)

Run a separately obtained Klaus Dormann test image through `KlausFunctionalTestHarness`.
Do not mark the functional model verified until the full suite passes. Also run the dedicated
interrupt and decimal suites where practical.

The success trap varies across assembled versions/configurations; the harness therefore takes
it as an argument instead of hard-coding a claim about one binary.

## Gate C - Visual6502 import

Load the actual external `segdefs.js`, `transdefs.js`, `nodenames.js` and verify:

- >3000 transistors (the loader already rejects obviously incomplete extracts)
- required pad names exist
- reset network settles
- address/data/RW/SYNC traces are plausible
- A/X/Y/SP/PC/P probe reads are stable at instruction boundaries

## Gate D - differential transistor verification

For a deterministic ROM and input sequence:

1. run Visual6502 reference
2. record half-cycle: CLK, AB, DB, RW, SYNC and selected internal nodes
3. run the Java transistor solver
4. compare half-cycle by half-cycle

Only after this gate should the Java switch solver be called transistor-accurate for the 6502.

## Gate E - LogicForge circuit integration

Build a tiny system:

```text
6502 + clock + ROM/RAM + reset + logic analyzer
```

Verify that DATA is Z from the CPU during reads, driven during writes, and that address/RW/SYNC
can be observed as normal LogicForge nets.
