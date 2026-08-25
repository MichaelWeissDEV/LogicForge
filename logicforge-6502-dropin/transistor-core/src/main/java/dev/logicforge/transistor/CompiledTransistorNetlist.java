package dev.logicforge.transistor;

import java.util.List;
import java.util.Map;

/** Flat adjacency-indexed representation consumed by {@link SwitchLevelSolver}. */
public final class CompiledTransistorNetlist {
    final int nodeCount;
    final int vccNode;
    final int vssNode;
    final boolean[] pullUps;
    final boolean[] pullDowns;
    final int[] gates;
    final int[] terminalA;
    final int[] terminalB;
    final int[][] transistorsByGate;
    final int[][] transistorsByTerminal;
    private final Map<String, Integer> nodeNames;

    private CompiledTransistorNetlist(TransistorNetlist source) {
        nodeCount = source.nodeCount();
        vccNode = source.vccNode();
        vssNode = source.vssNode();
        pullUps = source.pullUps();
        pullDowns = source.pullDowns();
        nodeNames = source.nodeNames();

        List<SwitchTransistor> sourceTransistors = source.transistors();
        int count = sourceTransistors.size();
        gates = new int[count];
        terminalA = new int[count];
        terminalB = new int[count];
        int[] gateCounts = new int[nodeCount];
        int[] terminalCounts = new int[nodeCount];

        for (int i = 0; i < count; i++) {
            SwitchTransistor t = sourceTransistors.get(i);
            gates[i] = t.gate();
            terminalA[i] = t.terminalA();
            terminalB[i] = t.terminalB();
            gateCounts[gates[i]]++;
            terminalCounts[terminalA[i]]++;
            terminalCounts[terminalB[i]]++;
        }

        transistorsByGate = allocateRows(gateCounts);
        transistorsByTerminal = allocateRows(terminalCounts);
        int[] gateCursor = new int[nodeCount];
        int[] terminalCursor = new int[nodeCount];
        for (int transistor = 0; transistor < count; transistor++) {
            int gate = gates[transistor];
            int a = terminalA[transistor];
            int b = terminalB[transistor];
            transistorsByGate[gate][gateCursor[gate]++] = transistor;
            transistorsByTerminal[a][terminalCursor[a]++] = transistor;
            transistorsByTerminal[b][terminalCursor[b]++] = transistor;
        }
    }

    public static CompiledTransistorNetlist compile(TransistorNetlist source) {
        return new CompiledTransistorNetlist(source);
    }

    public int nodeCount() { return nodeCount; }
    public int transistorCount() { return gates.length; }
    public int vccNode() { return vccNode; }
    public int vssNode() { return vssNode; }
    public Map<String, Integer> nodeNames() { return nodeNames; }

    public int requireNamedNode(String name) {
        Integer node = nodeNames.get(name);
        if (node == null || node < 0) {
            throw new IllegalArgumentException("Netlist has no usable node named '" + name + "'");
        }
        return node;
    }

    private static int[][] allocateRows(int[] counts) {
        int[][] result = new int[counts.length][];
        for (int i = 0; i < counts.length; i++) {
            result[i] = new int[counts[i]];
        }
        return result;
    }
}
