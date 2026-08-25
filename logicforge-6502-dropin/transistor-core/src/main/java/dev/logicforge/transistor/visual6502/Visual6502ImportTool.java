package dev.logicforge.transistor.visual6502;

import dev.logicforge.transistor.TransistorNetlist;
import java.nio.file.Path;

/** Minimal headless sanity-check CLI for local Visual6502 data. */
public final class Visual6502ImportTool {
    private Visual6502ImportTool() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("usage: Visual6502ImportTool <segdefs.js> <transdefs.js> <nodenames.js>");
            System.exit(2);
        }
        var result = Visual6502Importer.importFiles(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        TransistorNetlist netlist = result.netlist();
        System.out.printf("nodes=%d transistors=%d pullups=%d names=%d vcc=%d vss=%d%n",
                netlist.nodeCount(), result.transistorCount(), result.pullUpNodeCount(),
                result.namedNodeCount(), netlist.vccNode(), netlist.vssNode());
    }
}
