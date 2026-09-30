# LogicForge architecture

This document describes how LogicForge is put together and, more importantly, why. The
project started from a deliberately small scope — pure combinational logic — and the
structure below is the same one it has grown through buses, sequential logic, memories, a
hierarchical project format, a programmable CPU, physical IC packages and a logic analyzer
with a trigger engine. Nothing described as a future layer in an earlier revision of this
document was bolted on as an afterthought: the boundaries below were chosen so each of
those layers could arrive without disturbing the ones already in place.

## Module boundaries

```
             logic-core
             /        \
   circuit-model      simulation-core
        |     \        /      |    \
        |    component-library |  logic-analyzer
        |          |           |       |
        |    circuit-compiler  |       |
   project-format      |       |       |
             \         |      /       /
                     ui  ------------/
                      |
                     app
```

`component-structures`, `processor-lf8`, `processor-lf8-runtime` and `lf8-tools` sit beside
`component-library` on the same footing — headless modules the compiler and the UI both
depend on, never the other way around.

| Module | Responsibility | Depends on |
| --- | --- | --- |
| `logic-core` | `LogicState`, `LogicVector`, `BitWidth`, and the one definition of the logic semantics | nothing |
| `circuit-model` | The editable document: components, chips, wires, geometry, parameters | `logic-core` |
| `simulation-core` | Nets, events, the engine, the compiled runtime form | `logic-core` |
| `logic-analyzer` | Headless signal recording, waveform segments and the trigger engine | `simulation-core` |
| `component-library` | The built-in components: definitions, behaviours, registry | `circuit-model`, `simulation-core` |
| `component-structures` | Canonical gate-level reference circuits and their registry | `circuit-model`, `component-library` |
| `circuit-compiler` | Validation, net forming, runtime ids, source mapping, chip expansion | the four above |
| `project-format` | Reading and writing `.logic` files | `circuit-model` |
| `processor-lf8` | The LF-8 CPU: microcode, memory map, computer circuit factory | `circuit-compiler`, `component-structures` |
| `processor-lf8-runtime` | Headless probing (registers, flags, microstep) for tests and tools | `processor-lf8` |
| `lf8-tools` | LF-8 assembler and disassembler | `processor-lf8` |
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

`CircuitProject` holds circuits by name, always including one called `main`. A project can
hold several circuits: any of them can be instantiated as a subcircuit inside another
(`SubcircuitSupport` treats a circuit definition as an ordinary `ComponentDefinition`, so a
subcircuit instance is placed, wired and undone exactly like a gate), which is how every
hierarchical structure in the project — the structural adder/register/ALU library, the LF-8
CPU's datapath and control unit, a user's own reusable circuits — is actually built. Nesting
is not artificially bounded; a chip's own logical units are the only thing that never nests
further, since a physical package is expanded before hierarchy flattening ever sees it (see
**Physical chips** below).

### Physical chips

A real 7400-series part is not a behavioral component wearing a chip-shaped icon. `ChipDefinition`
describes a physical package (a `PackageDefinition` with real pin numbers and positions) and
the logical units inside it (`ChipLogicalUnit`, each an ordinary `ComponentType` — a NAND
gate is a NAND gate whether it is standalone or the third gate in a 74HC00). A placed
`ChipInstance` lives in `CircuitDocument.chips()`, a collection parallel to, and independent
of, ordinary components.

The key type is `ElectricalEndpoint`, a sealed interface with two cases: `ComponentEndpoint`
(an ordinary `PortEndpoint`) and `ChipPinEndpoint` (a chip instance id plus a physical pin
number). Every place that used to speak `PortEndpoint` — `Connection.from()`/`to()`, the
router, the hit tester, the renderer, the logic analyzer's watch list — now speaks
`ElectricalEndpoint`, so a wire between two chip pins, or a chip pin and a component port,
is not a special case bolted on top; it is the same code path a component-to-component wire
already used. `ElectricalEndpointGeometry` and `ChipGeometry` are the single places that
resolve an endpoint's world position, the same role `ComponentGeometry` already played for
ordinary ports — the same "one place computes it, everyone else calls it" rule that keeps a
rotated gate's renderer and hit tester from disagreeing applies identically to a rotated
chip package.

Before the ordinary compiler ever runs, `ChipInstanceExpander` turns every `ChipInstance`
into its logical gates and the wires among them, exactly as if the user had placed those
gates by hand — placing a chip never changes what the simulator itself understands, only
what the editor shows. `ChipSourceMap` is the resulting bidirectional bridge: a physical
pin's logical net, and a logical net's physical pin, both resolvable in either direction,
which is what lets the logic analyzer watch a chip's pin 7 the same way it watches an
ordinary port, and what lets the Study window navigate from a watch straight back to the
chip that owns it.

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
the same trace. `SimulationEvent` carries a time field alongside the delta cycle: a clock,
every structural counter/register and the LF-8's own instruction timing all advance
simulation time on the same queue that originally only ever advanced delta cycles within one
moment.

**Stabilisation and loops.** `runUntilStable()` processes delta cycles until the queue is
empty. A circuit that never settles is stopped after `maxDeltaCycles` and reported as
`SimulationOscillationException` — the application shows "Combinational oscillation
detected" and stays responsive. Note that four-state logic usually converges on its own: an
inverter feeding itself settles at `X`, which is the correct answer rather than a hang. The
limit is the safety net for cases that do not converge, stateful components (a badly built
sequential circuit) included.

**Initial state.** After `reset()` every net is `Z` and every component is evaluated once,
so constants and switches drive their values and the gates settle. Switches return to the
value their `Initially On` parameter specifies.

**Component state.** Behaviours are shared between instances and must be stateless
themselves; anything that has to survive between evaluations lives in a
`ComponentRuntimeState` the simulator owns. Gates have none. A toggle switch keeps an
`InputSourceState`, the same mechanism every register, counter and memory in the component
library uses — the engine has never assumed that an output is a pure function of the inputs.

**Observation.** Every net change passes through `SimulationObserver`. There is no global
event bus: the observer list belongs to the simulation, and the document has its own
listener interface. `SignalRecorder` (in `logic-analyzer`) and `TriggerEngine` are both
independent observers on the same simulation, reacting to the exact same push-based event
stream — which is what makes a trigger delta-cycle accurate: it sees every transition the
waveform is built from, including a glitch that settles back within one physical time step,
never a periodic sample that could land between two of them. A headless `BreakpointEngine`
(PC, memory-read and memory-write breakpoints) hooks into the same mechanism; the Study
window's inspector adds, toggles and removes them for an LF-8.

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
  "formatVersion": 5,
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

## What was speculative and is now load-bearing

Every item below was originally written up as scaffolding for a later feature; none of them
turned out to need rework to carry the weight actually put on them:

- `LogicVector` and per-port `BitWidth` — carry every bus in the component library, the LF-8
  datapath and every physical chip's multi-bit pins alike.
- Multi-driver nets with proper resolution — used by the tri-state buffers, and by every
  physical chip's output pin sharing a net with ordinary component drivers.
- An event queue with a time axis — the LF-8's clock, and every clocked structural
  component (registers, counters, the master-slave DFF) run on it directly; delta cycles
  within one moment were the whole story in the combinational-only version, and now
  routinely span dozens of cycles per instruction.
- `ComponentRuntimeState` — flip-flops, registers, memories and the LF-8's own register file
  and RAM/ROM all use it; still stateless behaviors otherwise.
- `SimulationObserver` — `SignalRecorder`, `TriggerEngine` and `BreakpointEngine` are three
  independent observers on the same event stream, none aware of the others.
- `pause`/`step` decoupled from the UI frame rate — `SimulationSession` is the one shared
  run-control both the toolbar and a fired trigger drive, and what `stepEvent`/`stepTime`
  are built on.
- A project holding several circuits — every subcircuit, the structural library's own
  hierarchies (an adder built from adders built from gates) and the LF-8's datapath/control
  split are ordinary instances of this, not a separate mechanism.
- The compiler sitting between the document and the runtime — physical chip expansion
  (`ChipInstanceExpander`) runs entirely on the document side of that boundary, so the
  runtime model never had to learn what a chip is.

## What is still genuinely future work

- The instruction control unit's own microcode sequencing has no gate-level decomposition
  yet, even under LF-8 `GATE_LEVEL` mode.
- Transistor networks and physical propagation timing: a placed 74HC00 simulates as four
  ideal NAND gates on the same delta-cycle model as everything else, not as a timing-accurate
  model of the actual part.
- The logic analyzer only draws the segments inside the viewport, but it still derives them
  from the full recorded trace (at most 100 000 transitions per signal) on every redraw; an
  index into the trace would make very long captures cheaper to scroll.

## Desktop application and packaging

The `app` module is the only place that knows it runs as a desktop program. `Main` answers
`--version`/`--help`, installs the uncaught-exception handler (log file under
`$XDG_STATE_HOME/logicforge`, dialog with folded-away details) and launches `LogicForgeApp`,
which opens a file given on the command line through the same `ProjectController.open(Path)`
that File → Open uses. `AppDirectories` resolves the XDG directories; nothing outside `app`,
`ui` and `packaging/` is aware of Linux.

Persistence stays in `project-format`: `ProjectFormat.save` serialises first and then writes
through `AtomicFileWriter` (temporary file in the same folder, `fsync`, atomic rename with a
plain replacing move as fallback, optional `.bak` of the previous version). Loading turns any
inconsistency in a file into a `ProjectFormatException` with a readable message.

`gradle/linux-packaging.gradle` builds a jpackage app image whose private runtime is linked by
jlink from the JDK and from jmods made out of the JavaFX Maven jars, so JavaFX's native
libraries sit in the runtime's `lib/` instead of being unpacked into the user's home at start.
AWT's native libraries are left out (JavaFX never loads them), which keeps the package free of
distribution-specific dependencies. `stageLinuxRoot` lays out the installed tree once; the
`.deb` (`packaging/linux/build-deb.sh`) and the snap (`snap/snapcraft.yaml`) both package that
same tree, with the desktop entry, MIME type, icons and AppStream metadata from
`packaging/linux/`.
