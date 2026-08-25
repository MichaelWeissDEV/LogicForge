# Processor architecture for LogicForge

## Boundary rule

Processor simulation must remain headless. CPU code may depend downward on generic simulation
contracts; it must never import JavaFX, editor, canvas, renderer, workbench, or palette code.

The existing LogicForge architecture already supports the important prerequisites:

- `LogicState`: ZERO / ONE / UNKNOWN / HIGH_IMPEDANCE
- multi-bit `LogicVector`
- `PortDirection.INOUT`
- multi-driver net resolution
- headless `ComponentBehavior`
- per-instance `ComponentRuntimeState`
- event-driven simulation

The 6502 work therefore belongs beside the existing logic core, not inside the UI and not as
a rewrite of the current simulator.

## Two deliberately independent CPU models

### Functional model

`processor-core/.../Mos6502.java`

Purpose: fast program execution, debugger state, assembler/software tests. It accesses a
headless `ByteBus`. It implements the documented ISA and instruction cycle counts.

### Silicon model

`transistor-core` + `processor-6502/.../Mos6502TransistorChip.java`

Purpose: real pin timing, internal-node inspection, transistor/gate learning mode, independent
verification. It has no accumulator/register variables: those values are reconstructed from
named nodes in the actual transistor network.

Keeping the models independent is intentional. One model must not call the other, otherwise
differential testing would only prove that two views share the same bug.

## Later fidelity levels

```text
FAST          Mos6502 instruction model
MICROCYCLE    future state machine with exact documented bus cycles
SILICON       imported transistor network
```

All three should eventually expose a common chip/debug descriptor, but they should retain
independent execution engines.
