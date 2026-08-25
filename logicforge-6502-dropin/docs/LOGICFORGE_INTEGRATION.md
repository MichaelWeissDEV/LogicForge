# LogicForge integration

## Why this does not require a UI rewrite

The current LogicForge compiler separates readable and drivable ports. An INOUT port is placed
in both runtime lists. `Mos6502TransistorBehavior` deliberately follows that contract and keeps
its compact input/output indexes in `Mos6502PortContract`.

The current core already has X/Z logic and INOUT semantics, so no new signal enum is needed.
The CPU's per-instance silicon state lives in `ComponentRuntimeState`, matching the existing
simulation architecture.

## First integration stage: grouped buses

For headless integration, declare ports in this exact order:

```text
CLK       INPUT   1
RESET_N   INPUT   1
IRQ_N     INPUT   1
NMI_N     INPUT   1
RDY       INPUT   1
SO_N      INPUT   1
DATA      INOUT   8
ADDRESS   OUTPUT 16
RW        OUTPUT  1
SYNC      OUTPUT  1
PHI1      OUTPUT  1
PHI2      OUTPUT  1
```

This yields the compact runtime order documented by `Mos6502PortContract`.

Do not add a JavaFX renderer yet. A unit/integration test can instantiate the behavior directly
with an imported transistor netlist.

## Physical DIP stage

`Mos6502Package.dip40()` contains all 40 physical pin numbers. Later, add a package-aware
component definition/renderer that presents individual A0..A15 and D0..D7 pins. Keep bus
packing/unpacking in an adapter. The silicon model must remain unchanged.

Power pins should become real simulator ports only when LogicForge has a defined electrical
power-domain model. Until then, VCC/VSS are metadata and the transistor solver owns its rails.
This avoids pretending that ordinary digital HIGH/LOW is already a voltage simulation.

## Future virtual-time work

The transistor behavior currently reacts when LogicForge reevaluates it after external pin
changes. Once a clock component and non-zero simulation time are added, no processor API needs
to change: clock transitions simply become scheduled input events.
