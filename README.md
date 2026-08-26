# LogicForge

LogicForge is a modern cross-platform digital logic simulator written in Java. It combines
an extensible circuit model, a deterministic simulation core, an interactive graphical
editor and a programmable educational 8-bit computer. Circuits use four-state digital
signals and can be simulated as fast behavioral blocks or descended through reusable
structural implementations built from ordinary gates and subcircuits.

![The LogicForge workbench](docs/screenshot.png)

## Current scope

**Signals.** Every signal is four-state — `0`, `1`, `X` (unknown) and `Z` (not driven) — and
the rules for combining them live in exactly one place. An unconnected gate input reads as
`X`, never as an implicit `0`. Nets support any number of drivers, so tri-state buffers and
driver conflicts behave the way they do in hardware.

**Components.**

| Category | Components |
| --- | --- |
| Sources | Logic 0, Logic 1, Logic X, Logic Z, Toggle Switch, Push Button, Clock |
| Logic | Buffer, NOT, Tri-State Buffer, Inverting Tri-State Buffer, AND, NAND, OR, NOR, XOR, XNOR |
| Sequential | Latches, flip-flops, registers, shift registers, counters |
| Routing | MUX/DEMUX, encoders/decoders, splitter/joiner, bus constants/probes and wide tri-state buffers |
| Arithmetic | Half/full adders, adder/subtractor, ALU, comparator, shifts, parity and increment/decrement |
| Memory | Register File, RAM, ROM |
| System | Width-configurable input/output ports and a memory-mapped 16-bit timer |
| Outputs | LED, Logic Probe, Output Pin, Bus Probe |

The six multi-input gates take 2 to 16 inputs, configurable per instance in the inspector.

**Editor and hierarchy.** Drag components out of a searchable palette, wire ports together with
orthogonal wires that follow when components move or rotate, select with clicks or a rubber
band, move, rotate, copy, paste, delete, undo and redo everything, zoom around the cursor,
pan, expand bus ports into individual bit pins, and watch signals in the docked logic
analyzer while the circuit runs. Subcircuits can be opened and edited in context. RAM and
ROM contents are inspectable and support raw binary load/save.

**LF-8 computer.** The hierarchical LF-8 executes real machine code with eight registers,
flags, stack operations, branches, `CALL`/`RET`, `EI`/`DI`, IRQ, NMI and `IRET`. IRQ, NMI and
startup addresses are little-endian vectors read from external ROM through the normal bus.
Interrupt entry saves a status byte containing Z/C/N/V and interrupt-enable state, and
`IRET` restores it. The centralized memory map provides ROM, RAM, MMIO and vector regions;
the included input port, output port and timer are wired as ordinary memory-mapped devices,
and the timer can raise a real CPU interrupt. A separate `lf8-tools` module supplies the
assembler and disassembler.

**Structural implementations.** The headless `component-structures` module supplies a
registry of canonical reference circuits: gate-level muxes and decoders, half/full/ripple
adders, SR and D latches, a master-slave DFF, Register8, an 8x8 dual-read register file and
ALU8. These are real hierarchies, not behavioral components hidden inside subcircuits. LF-8
`STRUCTURAL` mode replaces its ALU and register file with these circuits, exposing both
deep paths in a running processor:

```text
CPU -> Datapath -> ALU -> RippleAdder8 -> FullAdder -> HalfAdder -> gates
CPU -> Datapath -> RegisterFile -> Register8 -> DFF -> D Latch -> SR Latch -> gates
```

**Study window.** The workbench's Study action opens a read-only view in a separate window.
It can attach to the exact live simulation, descend through concrete component instances,
navigate backward and forward, and show current `0`/`1`/`X`/`Z` port values and debug state.
LF-8 inspection includes registers, PC, SP, IR, flags, IE, interrupt state, the current
instruction, the raw microcode word, decoded ALU operation and active control signals.
Controls pause/run the shared simulation and step one event, clock edge or instruction
boundary without mutating CPU state directly.

**Projects.** Circuits are saved as versioned JSON (`.logic`). Loading a saved project
restores the same circuit structurally, wire for wire.

### Current limits

`FAST` remains the default LF-8 implementation. `STRUCTURAL` currently decomposes the ALU
and register file; the PC, stack pointer, instruction registers, flags and control unit still
use normal higher-level components. `GATE_LEVEL` is deliberately rejected until those
remaining state elements have structural replacements. Physical chip packages, transistor
networks and physical propagation timing are future layers. LogicForge does not claim to
be a cycle-accurate 6502 or a transistor-level CPU simulator.

## Architecture

The simulation core has no dependency on JavaFX, and never will. Circuits can be built,
compiled and simulated headlessly:

```
UI  →  Circuit Document  →  Circuit Compiler  →  Simulation Core  →  Signal State
```

| Module | Contents |
| --- | --- |
| `logic-core` | Four-state logic, `LogicVector`, the central logic semantics |
| `circuit-model` | The document the user edits: components, wires, geometry, parameters |
| `simulation-core` | Nets, the delta-cycle event engine, compiled runtime structures |
| `logic-analyzer` | Headless sampling and trace data |
| `component-library` | Component definitions, behaviours and the registry |
| `component-structures` | Canonical gate-level and hierarchical reference circuits |
| `circuit-compiler` | Validation, net forming, compact runtime ids, source mapping |
| `project-format` | Versioned JSON persistence |
| `processor-lf8` | LF-8 CPU, microcode, memory map and computer circuit factory |
| `lf8-tools` | LF-8 assembler and disassembler |
| `ui` | Viewport, wire router, commands, renderers and the JavaFX views |
| `app` | The application entry point and development tools |

`docs/architecture.md` describes the design and the decisions behind it in detail.

## Build and run

LogicForge needs a JDK 25. Everything else — Gradle and JavaFX — is fetched by the wrapper.

```bash
./gradlew :app:run          # start the application
./gradlew test              # run the whole test suite (headless, no JavaFX needed)
```

The JavaFX dependencies are resolved for the platform you build on, so build on the
platform you want to run on. The code itself contains nothing platform-specific.

Development helpers:

```bash
./gradlew :app:screenshot   # render the workbench into app/build/screenshot.png
./gradlew :app:uiCheck      # replay editor gestures against the real UI
./gradlew :app:examples     # regenerate examples/ through the real save path
```

## Controls

| Action | Gesture |
| --- | --- |
| Place a component | Drag it from the palette, or double-click it and click on the canvas |
| Draw a wire | Drag from one port to another |
| Toggle an input | Click a switch or a button |
| Select | Click; Shift-click or ⌘/Ctrl-click to add; drag on empty canvas for a rubber band |
| Move | Drag the selection |
| Rotate | `R` |
| Delete | `Delete` or `Backspace` |
| Cancel the current gesture | `Escape` |
| Zoom | Mouse wheel, trackpad scroll or pinch — always around the cursor |
| Pan | Middle-drag, or Alt-drag |
| New / Open / Save / Save As | ⌘/Ctrl `N` `O` `S`, ⇧⌘/Ctrl-Shift `S` |
| Undo / Redo | ⌘/Ctrl `Z`, ⇧⌘/Ctrl-Shift `Z` (or Ctrl `Y`) |
| Copy / Paste / Duplicate | ⌘/Ctrl `C` `V` `D` |
| Search the palette | ⌘/Ctrl `F` |
| Zoom in / out / reset | ⌘/Ctrl `+` `-` `0` |
| Open live Study view | Click **Study** in the toolbar |

## Examples

`examples/` contains circuits generated by the application itself:

- `NOT.logic` — a switch, an inverter and an LED
- `AND.logic`, `XOR.logic` — two switches into a gate
- `Half-Adder.logic` — sum from an XOR, carry from an AND
- `Tri-State-Bus.logic` — two tri-state drivers sharing one net: `Z` when idle, `X` when
  they disagree

## Project status and next layers

The simulator, editor, hierarchy compiler, programmable LF-8, vector interrupts, MMIO,
timer, structural library and Study workflow are implemented and covered by headless tests.
The next intended layers are full LF-8 gate-level state/control, reusable component
metadata and truth tables, breakpoints and analyzer cross-probing, then physical package
models such as 74HC and memory ICs.

## License

MIT — see `LICENSE`.
