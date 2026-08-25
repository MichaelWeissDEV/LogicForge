# LogicForge architecture

This document describes how LogicForge 0.1 is put together and, more importantly, why. The
scope of this version is deliberately small — combinational logic — but the structure is
the one the project intends to keep as it grows towards buses, sequential logic, memories
and a small CPU.

## Module boundaries

```
             logic-core
             /        \
   circuit-model      simulation-core
        |     \        /      |
        |    component-library |
        |          |           |
        |    circuit-compiler  |
   project-format      |       |
             \         |      /
                     ui
                      |
                     app
```

| Module | Responsibility | Depends on |
| --- | --- | --- |
| `logic-core` | `LogicState`, `LogicVector`, `BitWidth`, and the one definition of the logic semantics | nothing |
| `circuit-model` | The editable document, component definitions, parameters, geometry | `logic-core` |
| `simulation-core` | Nets, events, the engine, the compiled runtime form | `logic-core` |
| `component-library` | The built-in components: definitions, behaviours, registry | `circuit-model`, `simulation-core` |
| `circuit-compiler` | Validation, net forming, runtime ids, source mapping | the four above |
| `project-format` | Reading and writing `.logic` files | `circuit-model` |
| `ui` | Viewport, wire routing, commands, renderers, JavaFX views | everything above |
| `app` | Application entry point, development tools | `ui` |

**No module below `ui` may reference JavaFX.** This is enforced by the dependency graph:
the JavaFX dependency is declared only in `ui` and `app`. The Java module system is not
used — with a multi-module Gradle build and JavaFX it costs more than it gives, and the
dependency graph already provides the guarantee that matters.

The whole simulator therefore runs headless. The test suite builds, compiles and simulates
circuits without ever starting a JavaFX toolkit.

## Four-state logic

`LogicState` has four values: `ZERO`, `ONE`, `UNKNOWN` (`X`) and `HIGH_IMPEDANCE` (`Z`).
`LogicOperations` is the only place where their behaviour is defined; no component
implements its own variant. The rules:

1. **A floating gate input is unknown.** A gate input reading `Z` is not connected to any
   driver, so it is treated as `X`. Unconnected inputs are never implicitly `0`. This is a
   deliberate decision: silently reading `0` hides wiring mistakes, and the whole point of a
   four-state simulator is to make them visible.
2. **Controlling values dominate.** `0 AND X = 0` and `1 OR X = 1`, because those inputs
   decide the result on their own. XOR has no controlling value, so any unknown input makes
   its result `X`.
3. **Inverting gates share the base table.** NAND, NOR and XNOR are the base operation
   followed by `NOT`, with `NOT X = X`.
4. **Gates never output `Z`.** Only a disabled tri-state driver, or a net with no driver at
   all, produces `Z`.
5. **Net resolution** is defined by `LogicOperations.resolve`: undriven contributions are
   ignored, agreeing drivers keep their value, conflicting drivers produce `X`, and a net
   with no drivers is `Z`. The operation is commutative and associative, so a net's value
   never depends on the order its drivers are visited.

`LogicVector` is an immutable vector of states with bit 0 as the least significant bit.
Widths are explicit: combining vectors of different widths throws `WidthMismatchException`
rather than truncating or extending silently. The backing store is hidden behind the
accessors so a more compact representation can replace it later.

## Circuit model

`CircuitDocument` is what the user edits: `ComponentInstance`s (a definition id, a
position, a rotation, parameters and a label), `Connection`s between `PortReference`s, and
a little metadata. Identity is a `UUID` and survives saving and loading. Components are
immutable records; editing produces a new instance with the same id.

A `ComponentDefinition` describes a *kind* of component; the instances on the canvas are
placements of it. The definition declares the parameters and derives the ports and body
size from them, which is why widening an AND gate produces new ports without any special
case: `IN0` and `IN1` keep their names, so their wires stay attached.

Ports carry their own geometry. `PortSpec.anchor` is the connection point in
component-local coordinates, and `ComponentGeometry` is the single place where rotation is
applied. The renderer, the hit testing and the wire router all call it, so a rotated gate
cannot be drawn in one place and wired in another.

Geometry types (`CircuitPoint`, `CircuitSize`, `CircuitBounds`, `Rotation`, `PortSide`) are
plain records in the model, never JavaFX types.

`CircuitProject` holds circuits by name. Version 0.1 always has exactly one, called `main`;
the container exists so subcircuits do not require a format change.

## Compiled circuit

The editing model and the runtime model are deliberately different. The document is
organised for stable identity and cheap editing; the simulator wants flat arrays.

`CircuitCompiler` bridges them:

1. resolves every component against the registry,
2. checks that every wire refers to ports that exist,
3. merges ports joined by wires into nets — every unconnected port gets a net of its own,
   so a floating input is an ordinary undriven net rather than a special case,
4. checks that all ports of a net agree on their width,
5. records which ports drive each net and which read it,
6. hands out compact integer ids and builds the `CompiledCircuit`,
7. produces a `CircuitSourceMap` so the editor can translate back.

Ids are handed out in document order, so compiling the same circuit twice yields exactly
the same runtime structure. Problems are reported as `ValidationIssue`s carrying a severity
and the element they concern, so the editor can point at them; errors abort compilation
with a `CircuitCompileException`.

`CompiledCircuit` holds `CompiledComponent[]`, `CompiledNet[]` and `CompiledDriver[]`, with
each net knowing its drivers and consumers as `int[]`. Nothing looks up a UUID while the
simulation runs.

## Simulation

`Simulation` is an event-driven delta-cycle simulator:

```
an input changes
  → the component drives a new value      (a queued event)
  → the net resolves its drivers
  → the net's consumers are evaluated
  → their outputs schedule the next delta cycle
```

Only components attached to nets that actually changed are evaluated; nothing is recomputed
wholesale, and driving a value a port already has produces no event at all.

**Determinism.** Events carry `(time, deltaCycle, sequence)` and are ordered by all three.
Nets and components are visited in ascending id order. There is no hash iteration order and
no threading anywhere in the core, so the same circuit with the same inputs always produces
the same trace. `SimulationEvent` already carries a time field: version 0.1 only advances
delta cycles within a moment, but propagation delays, clocks and sequential components will
use the same queue.

**Stabilisation and loops.** `runUntilStable()` processes delta cycles until the queue is
empty. A circuit that never settles is stopped after `maxDeltaCycles` and reported as
`SimulationOscillationException` — the application shows "Combinational oscillation
detected" and stays responsive. Note that four-state logic usually converges on its own: an
inverter feeding itself settles at `X`, which is the correct answer rather than a hang. The
limit is the safety net for cases that do not converge, including the stateful components to
come.

**Initial state.** After `reset()` every net is `Z` and every component is evaluated once,
so constants and switches drive their values and the gates settle. Switches return to the
value their `Initially On` parameter specifies.

**Component state.** Behaviours are shared between instances and must be stateless
themselves; anything that has to survive between evaluations lives in a
`ComponentRuntimeState` the simulator owns. Gates have none. A toggle switch keeps an
`InputSourceState`, which is the same mechanism registers and memories will use — the
engine has never assumed that an output is a pure function of the inputs.

**Observation.** Every net change passes through `SimulationObserver`, which is what a logic
analyser, a signal trace or a breakpoint will hook into. There is no global event bus: the
observer list belongs to the simulation, and the document has its own listener interface.

## Component model

Three concerns are kept apart:

- `ComponentDefinition` — what a component *is*: id, name, category, parameters, ports.
- `ComponentBehavior` — what it *does* during simulation.
- `ComponentRenderer` — how it *looks*, registered in the UI module only.

`ComponentRegistry` binds the first two into `ComponentType`s and is the single source of
truth for which components exist. The palette, the inspector, the compiler and the project
loader all ask it; none of them keeps a list of its own.

Gates that share an implementation still have their own identity. `logic.and` and
`logic.nand` are separate definitions saved under separate ids, even though both use
`NaryGateBehavior` with a different operation and inversion flag. Saved projects say what
the user placed, not how the code happens to be factored.

**Pull-up and pull-down resistors are deliberately absent.** They only make sense with
drive strengths, which the net model does not have; a pull-up implemented as an ordinary
driver would conflict with real drivers and produce `X`, which would be wrong. They belong
with a strength model, not before it.

## UI architecture

The views are thin. Every change to a circuit goes through a `CircuitCommand` executed by
`CircuitEditor`, which owns the document, the compilation, the simulation and the undo
history. That is what makes undo uniform and keeps the JavaFX classes free of editing
logic.

**What is recompiled when.** `CircuitChange.Kind` knows whether a change affects the
electrical topology. Adding, removing, wiring and reconfiguring do; moving, rotating and
renaming do not, and leave a running simulation untouched. When a recompile does happen, the
values the user set on switches are carried over, so building on a circuit does not reset
it.

**Coordinates.** `ViewportTransform` is the only place that converts between circuit and
screen coordinates, and it is a plain class with unit tests — which is what keeps drag &
drop landing on the right grid cell at any zoom. Editor state (selection, hover, the wire
being drawn, the viewport) is separate from the document and never saved.

**Rendering.** One `Canvas`, drawn in passes: grid, wires, components, overlay. The
viewport transform is applied to the graphics context once, so the renderers work in
circuit coordinates and only what is inside the visible area is drawn. Symbols come from a
`RendererRegistry` keyed by component id; six gates share four outlines and an inversion
bubble. All colours and measurements come from `Theme` and the matching CSS tokens.

**Wires.** `WireRouter` is an interface with one deterministic Manhattan implementation.
Wires leave a port straight out of its own side and turn in a corridor just past the source,
so all wires leaving one output share a corridor — they are the same net — while unrelated
signals are pushed into different lanes. Routes are computed from the ports' current
positions, so wires follow moves and rotations automatically. A filled dot marks ports where
several wires meet; crossings get nothing, so a junction can never be mistaken for a
crossing.

**Hit testing** works against the model's own geometry, linearly over the document. That is
ample for the circuit sizes this version targets, and the API takes the visible area so a
spatial index can replace the loop later.

## Persistence

Projects are JSON with a mandatory `formatVersion`. A file from a newer version is refused
with a clear message rather than guessed at. Nothing runtime-related is stored: no Java
class names, no serialised objects, no net ids, no simulation state.

```json
{
  "formatVersion": 2,
  "application": "LogicForge",
  "name": "half-adder",
  "circuits": [
    {
      "name": "main",
      "components": [
        { "id": "…", "type": "logic.and", "x": 320, "y": 288, "label": "CARRY_AND" }
      ],
      "connections": [
        { "id": "…", "from": {"component": "…", "port": "OUT"},
                     "to":   {"component": "…", "port": "IN0"} }
      ]
    }
  ]
}
```

Default values are omitted, members are written in a fixed order and whole numbers without
a decimal point, so saving the same project twice produces identical bytes. The JSON reader
and writer are part of the module — the format is small enough that owning it is cheaper
than depending on a document library, and it gives exact control over the output.

**Document state versus simulation state.** Flipping a switch changes the simulation, not
the project, and does not mark the project as modified. What a switch returns to on reset is
a component parameter and therefore *is* part of the project.

## What this design leaves room for

The pieces that later features need are already in place, and none of them are speculative
scaffolding — every one of them is used today:

- `LogicVector` and per-port `BitWidth` for multi-bit buses,
- multi-driver nets with proper resolution, used now by the tri-state buffers,
- `PortDirection.INOUT` for bidirectional buses,
- an event queue with a time axis for clocks and propagation delays,
- `ComponentRuntimeState` for flip-flops, registers and memories,
- `SimulationObserver` for the logic analyser,
- `pause`/`step` decoupled from the UI frame rate for breakpoints,
- a project that holds several circuits, for subcircuits,
- and a compiler between the document and the runtime, so all of the above can change the
  runtime form without touching the editor.
