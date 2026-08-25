# LogicForge MOS 6502 headless prototype

This source tree is a drop-in development branch for LogicForge. It intentionally does not
modify the editor, renderer, JavaFX UI, wire router, project format, or the existing gate
library.

## What is implemented

### `processor-core`

A standalone NMOS MOS 6502 functional model:

- all **151 documented NMOS 6502 opcodes**
- all documented addressing modes
- 8-bit A/X/Y/SP and 16-bit PC
- C/Z/I/D/V/N status behavior; B is synthesized only for stack pushes
- RESET, IRQ, edge-latched NMI, and SO edge handling
- stack semantics, BRK/RTI/JSR/RTS
- zero-page wrap behavior
- indexed page-cross cycle penalties
- branch taken/page-cross penalties
- original NMOS `JMP ($xxFF)` page-wrap quirk
- binary ADC/SBC
- NMOS decimal ADC/SBC, including exhaustive local checking of accumulator/carry for all valid BCD operands
- deterministic snapshots and optional bus-access observation
- documented cycle count returned by every instruction

This model is **instruction functional / cycle-count accurate**, not a claim of exact
cycle-by-cycle bus pin activity. It deliberately rejects undocumented opcodes. The imported
transistor model is the path for actual silicon behavior, including undocumented behavior.

### `transistor-core`

A generic, chip-independent switch-level NMOS engine:

- flat transistor netlist IR
- compiled integer-array adjacency structure
- event-driven dirty-node settlement
- NMOS switch behavior (HIGH gate connects the two channel nodes)
- power rails, external drives, pull-ups/pull-downs
- retained-charge approximation for isolated dynamic nodes
- parser for the static Visual6502 `transdefs.js`, `segdefs.js`, and `nodenames.js` formats
- no JavaScript execution
- no geometry dependency

### `processor-6502`

6502-specific transistor facade:

- original MOS 6502 DIP-40 pin metadata
- canonical Visual6502 node-name validation
- external RESET/RDY/IRQ/NMI/SO/clock control
- external D0..D7 drive/release
- address/data/RW/SYNC/PHI1/PHI2 reads
- architectural A/X/Y/SP/PC/P reconstructed directly from the transistor-network nodes
- arbitrary named internal-node probing for a later silicon explorer / logic analyzer
- reference-style reset warm-up helper

### `processor-integration`

A thin adapter to LogicForge's existing headless `ComponentBehavior` / `ComponentRuntimeState`
contracts. It has no JavaFX dependency and does not register anything in the palette.
Therefore simply developing these modules does not alter the current UI.

## Module dependency direction

```text
processor-core          transistor-core
      \                    /
       \                  /
        +--- processor-6502
                  |
                  v
         processor-integration
                  |
       LogicForge logic/simulation core

UI is not a dependency of any module above.
```

## Add to the existing LogicForge checkout

Copy the four directories into the repository root and add these lines to `settings.gradle`:

```gradle
include 'processor-core'
include 'transistor-core'
include 'processor-6502'
include 'processor-integration'
```

Do not register a processor component in `StandardLibrary` yet. The modules can be developed
and tested headlessly until the CPU work is ready to surface in the application.

## Verification status of this bundle

Locally performed while producing this bundle:

- all main sources in `processor-core`, `transistor-core`, and `processor-6502` compile cleanly with `javac -Xlint:all -Xlint:-serial` on Java 21; LogicForge targets Java 25, so the source language is compatible
- the LogicForge integration adapter was syntax-compiled against stubs matching the currently inspected LogicForge interfaces
- a smoke program verified RESET, LDA, ADC, STA, JSR/RTS, INX, and the JMP-indirect page-wrap quirk
- every one of the 151 documented opcode switch entries was executed in a neutral one-instruction smoke harness without falling into the undocumented-opcode path
- valid packed-BCD ADC/SBC accumulator and carry results were exhaustively checked for operands 00..99 and both carry-in states
- the transistor solver was checked with an NMOS inverter (pull-up + transistor to ground)

Not claimed yet:

- a full Klaus Dormann functional-test pass. A harness is included, but the GPL test image is intentionally not bundled.
- bit-for-bit comparison of the Java transistor solver against a full Visual6502 run. The external Visual6502 data is intentionally not bundled and must be supplied separately after checking its file-specific terms.
- exact external bus microcycles from the fast model. Use the transistor model for silicon-level bus behavior; a separate faster microcycle model can be added later.

See `docs/` for integration, source provenance, and validation details.
