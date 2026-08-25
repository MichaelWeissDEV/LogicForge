package dev.logicforge.processor.mos6502.transistor;

import dev.logicforge.transistor.visual6502.Visual6502Importer;
import java.io.IOException;
import java.nio.file.Path;

/** Convenience loader plus 6502-specific sanity checks for externally supplied Visual6502 data. */
public final class Mos6502Visual6502Loader {
    private Mos6502Visual6502Loader() { }

    public static Mos6502TransistorChip load(Path segdefs, Path transdefs, Path nodenames) throws IOException {
        var imported = Visual6502Importer.importFiles(segdefs, transdefs, nodenames);
        if (imported.transistorCount() < 3_000) {
            throw new IllegalArgumentException("Expected a full 6502 transistor extraction; found only "
                    + imported.transistorCount() + " transistors");
        }
        var netlist = imported.netlist();
        // Force validation of the external pad names before constructing the solver.
        for (String name : new String[]{"vcc", "vss", "clk0", "res", "rdy", "irq", "nmi",
                "so", "rw", "sync", "ab0", "ab15", "db0", "db7"}) {
            netlist.requireNamedNode(name);
        }
        return new Mos6502TransistorChip(netlist);
    }
}
