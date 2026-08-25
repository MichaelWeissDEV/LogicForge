package dev.logicforge.transistor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Source-level transistor netlist. Geometry is deliberately absent: this object contains
 * only what the electrical switch solver requires plus optional symbolic node names.
 */
public final class TransistorNetlist {
    private final int nodeCount;
    private final int vccNode;
    private final int vssNode;
    private final boolean[] pullUps;
    private final boolean[] pullDowns;
    private final List<SwitchTransistor> transistors;
    private final Map<String, Integer> nodeNames;

    public TransistorNetlist(int nodeCount, int vccNode, int vssNode,
                             boolean[] pullUps, boolean[] pullDowns,
                             List<SwitchTransistor> transistors,
                             Map<String, Integer> nodeNames) {
        if (nodeCount <= 0) {
            throw new IllegalArgumentException("nodeCount must be positive");
        }
        if (vccNode < 0 || vccNode >= nodeCount || vssNode < 0 || vssNode >= nodeCount) {
            throw new IllegalArgumentException("Power nodes outside netlist");
        }
        if (pullUps.length != nodeCount || pullDowns.length != nodeCount) {
            throw new IllegalArgumentException("Pull arrays must match nodeCount");
        }
        this.nodeCount = nodeCount;
        this.vccNode = vccNode;
        this.vssNode = vssNode;
        this.pullUps = pullUps.clone();
        this.pullDowns = pullDowns.clone();
        this.transistors = List.copyOf(transistors);
        this.nodeNames = Map.copyOf(new LinkedHashMap<>(nodeNames));
        validate();
    }

    private void validate() {
        for (SwitchTransistor transistor : transistors) {
            requireNode(transistor.gate());
            requireNode(transistor.terminalA());
            requireNode(transistor.terminalB());
        }
        for (Map.Entry<String, Integer> entry : nodeNames.entrySet()) {
            if (entry.getValue() >= 0) {
                requireNode(entry.getValue());
            }
        }
    }

    private void requireNode(int node) {
        if (node < 0 || node >= nodeCount) {
            throw new IllegalArgumentException("Node " + node + " outside 0.." + (nodeCount - 1));
        }
    }

    public int nodeCount() { return nodeCount; }
    public int vccNode() { return vccNode; }
    public int vssNode() { return vssNode; }
    public boolean hasPullUp(int node) { return pullUps[node]; }
    public boolean hasPullDown(int node) { return pullDowns[node]; }
    public boolean[] pullUps() { return pullUps.clone(); }
    public boolean[] pullDowns() { return pullDowns.clone(); }
    public List<SwitchTransistor> transistors() { return transistors; }
    public Map<String, Integer> nodeNames() { return nodeNames; }

    public int requireNamedNode(String name) {
        Integer node = nodeNames.get(name);
        if (node == null || node < 0) {
            throw new IllegalArgumentException("Netlist has no usable node named '" + name + "'");
        }
        return node;
    }

    public static Builder builder(int nodeCount, int vccNode, int vssNode) {
        return new Builder(nodeCount, vccNode, vssNode);
    }

    public static final class Builder {
        private final int nodeCount;
        private final int vccNode;
        private final int vssNode;
        private final boolean[] pullUps;
        private final boolean[] pullDowns;
        private final List<SwitchTransistor> transistors = new ArrayList<>();
        private final Map<String, Integer> names = new LinkedHashMap<>();

        private Builder(int nodeCount, int vccNode, int vssNode) {
            this.nodeCount = nodeCount;
            this.vccNode = vccNode;
            this.vssNode = vssNode;
            this.pullUps = new boolean[nodeCount];
            this.pullDowns = new boolean[nodeCount];
        }

        public Builder pullUp(int node) { pullUps[node] = true; return this; }
        public Builder pullDown(int node) { pullDowns[node] = true; return this; }
        public Builder transistor(SwitchTransistor transistor) { transistors.add(transistor); return this; }
        public Builder name(String name, int node) { names.put(name, node); return this; }

        public TransistorNetlist build() {
            return new TransistorNetlist(nodeCount, vccNode, vssNode,
                    Arrays.copyOf(pullUps, pullUps.length), Arrays.copyOf(pullDowns, pullDowns.length),
                    transistors, names);
        }
    }
}
