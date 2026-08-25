# LogicForge

LogicForge is a modern cross-platform digital logic simulator written in Java. It combines
an extensible circuit model, a deterministic simulation core and an interactive graphical
editor. The initial focus is elementary combinational logic, four-state digital signals and
a polished circuit-building workflow, while the architecture is designed to grow toward
buses, sequential logic, logic analysis, memory devices and educational 8-bit processors.

![The LogicForge workbench](docs/screenshot.png)

## Current scope

Version 0.4 adds buses, sequential logic, arithmetic, and memory components.

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
| Outputs | LED, Logic Probe, Output Pin, Bus Probe |

The six multi-input gates take 2 to 16 inputs, configurable per instance in the inspector.

**Editor.** Drag components out of a searchable palette, wire ports together with
orthogonal wires that follow when components move or rotate, select with clicks or a rubber
band, move, rotate, copy, paste, delete, undo and redo everything, zoom around the cursor,
pan, expand bus ports into individual bit pins, and watch signals in the docked logic
analyzer while the circuit runs. RAM and ROM contents are inspectable and support raw
binary load/save.

**Projects.** Circuits are saved as versioned JSON (`.logic`). Loading a saved project
restores the same circuit structurally, wire for wire.

### Not in this version

Initial project-backed subcircuits can declare named Input/Output interfaces and are
flattened into the parent simulation. Opening/editing child internals is still a later UI
milestone. There is no complete CPU yet; the ALU and register file are available as CPU
datapath building blocks.

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
| `component-library` | Component definitions, behaviours and the registry |
| `circuit-compiler` | Validation, net forming, compact runtime ids, source mapping |
| `project-format` | Versioned JSON persistence |
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

## Examples

`examples/` contains circuits generated by the application itself:

- `NOT.logic` — a switch, an inverter and an LED
- `AND.logic`, `XOR.logic` — two switches into a gate
- `Half-Adder.logic` — sum from an XOR, carry from an AND
- `Tri-State-Bus.logic` — two tri-state drivers sharing one net: `Z` when idle, `X` when
  they disagree

## Project status

Version 0.1 — the foundation. The logic core, the compiler, the simulator and the editor
are complete for combinational circuits and covered by tests; the module boundaries are in
place for what comes next.

## Roadmap

| Version | Theme |
| --- | --- |
| 0.1 | Basic logic: four-state signals, elementary gates, circuit editor, persistence |
| 0.2 | Sequential logic: clocks, flip-flops, registers, and the logic analyser |
| 0.3 | Hierarchy: subcircuits, custom chips, multi-bit buses |
| 0.4 | Arithmetic and memory: adders, ALUs, RAM, ROM |
| 0.5 | An educational 8-bit CPU running real machine code |

The CPU is a long-term goal, not a near-term one. Every step above builds on the model this
version establishes.

## License

MIT — see `LICENSE`.
