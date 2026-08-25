package dev.logicforge.transistor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.logicforge.transistor.visual6502.Visual6502Importer;
import org.junit.jupiter.api.Test;

final class Visual6502ImporterTest {
    @Test
    void parsesStaticVisual6502FormatsWithoutExecutingJavascript() {
        String names = "var nodenames = { vss: 0, vcc: 1, clk0: 2, \\\"#foo\\\": 3 };".replace("\\\"", "\"");
        String trans = "var transdefs = [['t0', 2, 0, 3, [1,2,3,4], [1,2,3,4,5]]];";
        String segs = "var segdefs = [[0,'-',0,0],[1,'-',1,1],[3,'+',2,2]];";

        var result = Visual6502Importer.importStrings(segs, trans, names);
        assertEquals(4, result.netlist().nodeCount());
        assertEquals(1, result.transistorCount());
        assertEquals(1, result.pullUpNodeCount());
        assertTrue(result.netlist().hasPullUp(3));
        assertEquals(3, result.netlist().requireNamedNode("#foo"));
    }
}
