# Loading a real 6502 transistor extraction

The Java solver does not need polygon geometry. It consumes only:

```text
transdefs.js  -> transistor gate, terminal A, terminal B
segdefs.js    -> per-node pull-up presence
nodenames.js  -> symbolic node ids
```

Obtain these files from the Visual6502 project after checking their file-specific licensing.
Do not execute them as JavaScript. The importer parses their static literal data.

Example from a headless Java caller:

```java
var chip = Mos6502Visual6502Loader.load(
    Path.of("external/visual6502/segdefs.js"),
    Path.of("external/visual6502/transdefs.js"),
    Path.of("external/visual6502/nodenames.js"));

chip.referenceResetSequence();
chip.setClock(false);
int address = chip.addressBus();
boolean read = chip.readCycle();
```

For a memory read, external memory should drive D0..D7 while the CPU is in a read cycle:

```java
if (chip.readCycle()) {
    chip.driveDataBus(memory[chip.addressBus()] & 0xff);
} else {
    chip.releaseDataBus();
    memory[chip.addressBus()] = (byte) chip.dataBus();
}
```

Then advance the clock and settle again. At silicon fidelity the surrounding memory/bus timing
should eventually be handled by LogicForge's virtual-time scheduler rather than a loop like
this.

## Internal probes

Visual6502 names hundreds of internal nodes. The facade already exposes:

```java
chip.nodeHigh("alucin");
chip.nodeHigh("alu0");
chip.nodeHigh("pcl0");
chip.nodeHigh("ir0");
```

This is the basis for a later internal CPU view without any changes to the solver.
