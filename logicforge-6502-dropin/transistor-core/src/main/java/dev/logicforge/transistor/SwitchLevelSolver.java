package dev.logicforge.transistor;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;

/**
 * Deterministic event-driven switch-level NMOS solver.
 *
 * <p>A HIGH gate closes a transistor. Closed transistors form connected node groups. A
 * group's value is resolved with the same broad priorities used by historical switch-level
 * simulators: ground, power, external drive, pull-up/pull-down, then retained charge. Only
 * groups made dirty by a changed node/drive are recalculated.</p>
 *
 * <p>This is intentionally not SPICE: no continuous voltage/current, capacitance or MOSFET
 * transfer curves are solved. It is the right fidelity for imported Visual6502 topology.</p>
 */
public final class SwitchLevelSolver {
    private final CompiledTransistorNetlist netlist;
    private final NodeState[] state;
    private final DriveState[] externalDrive;
    private final boolean[] transistorOn;

    private final Deque<Integer> dirty = new ArrayDeque<>();
    private final BitSet queued;
    private final int[] bfsQueue;
    private final int[] group;
    private final int[] visitMark;
    private int visitGeneration = 1;
    private int maxGroupRecalculations = 200_000;

    public SwitchLevelSolver(CompiledTransistorNetlist netlist) {
        this.netlist = netlist;
        this.state = new NodeState[netlist.nodeCount];
        this.externalDrive = new DriveState[netlist.nodeCount];
        this.transistorOn = new boolean[netlist.gates.length];
        this.queued = new BitSet(netlist.nodeCount);
        this.bfsQueue = new int[netlist.nodeCount];
        this.group = new int[netlist.nodeCount];
        this.visitMark = new int[netlist.nodeCount];
        reset();
    }

    public void reset() {
        Arrays.fill(state, NodeState.LOW);
        Arrays.fill(externalDrive, DriveState.FLOATING);
        Arrays.fill(transistorOn, false);
        state[netlist.vccNode] = NodeState.HIGH;
        state[netlist.vssNode] = NodeState.LOW;

        for (int t = 0; t < transistorOn.length; t++) {
            transistorOn[t] = state[netlist.gates[t]] == NodeState.HIGH;
        }
        dirty.clear();
        queued.clear();
        for (int node = 0; node < netlist.nodeCount; node++) {
            enqueue(node);
        }
        settle();
    }

    public void drive(int node, DriveState drive) {
        requireNode(node);
        if (node == netlist.vccNode || node == netlist.vssNode) {
            throw new IllegalArgumentException("Power rails are solver-owned");
        }
        if (externalDrive[node] != drive) {
            externalDrive[node] = drive;
            enqueue(node);
        }
    }

    public void drive(String nodeName, DriveState drive) {
        drive(netlist.requireNamedNode(nodeName), drive);
    }

    public void release(int node) {
        drive(node, DriveState.FLOATING);
    }

    public NodeState state(int node) {
        requireNode(node);
        return state[node];
    }

    public NodeState state(String nodeName) {
        return state(netlist.requireNamedNode(nodeName));
    }

    public boolean high(int node) {
        return state(node) == NodeState.HIGH;
    }

    public boolean high(String nodeName) {
        return state(nodeName) == NodeState.HIGH;
    }

    public void settle() {
        int operations = 0;
        while (!dirty.isEmpty()) {
            if (++operations > maxGroupRecalculations) {
                throw new SimulationDidNotSettleException(operations);
            }
            int seed = dirty.removeFirst();
            queued.clear(seed);
            recalculateGroup(seed);
        }
    }

    public void setMaxGroupRecalculations(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        maxGroupRecalculations = limit;
    }

    private void recalculateGroup(int seed) {
        int generation = nextGeneration();
        int head = 0;
        int tail = 0;
        int groupSize = 0;
        bfsQueue[tail++] = seed;
        visitMark[seed] = generation;

        boolean containsGround = false;
        boolean containsPower = false;
        boolean drivenLow = false;
        boolean drivenHigh = false;
        boolean pullUp = false;
        boolean pullDown = false;
        boolean retainedHigh = false;

        while (head < tail) {
            int node = bfsQueue[head++];
            group[groupSize++] = node;
            containsGround |= node == netlist.vssNode;
            containsPower |= node == netlist.vccNode;
            drivenLow |= externalDrive[node] == DriveState.LOW;
            drivenHigh |= externalDrive[node] == DriveState.HIGH;
            pullUp |= netlist.pullUps[node];
            pullDown |= netlist.pullDowns[node];
            retainedHigh |= state[node] == NodeState.HIGH;

            for (int transistor : netlist.transistorsByTerminal[node]) {
                if (!transistorOn[transistor]) {
                    continue;
                }
                int other = netlist.terminalA[transistor] == node
                        ? netlist.terminalB[transistor]
                        : netlist.terminalA[transistor];
                if (visitMark[other] != generation) {
                    visitMark[other] = generation;
                    bfsQueue[tail++] = other;
                }
            }
        }

        NodeState resolved;
        // Correct transistor data should not directly short rails. Ground-first behavior is
        // deterministic and matches the conservative convention used by classic visual6502.
        if (containsGround || drivenLow) {
            resolved = NodeState.LOW;
        } else if (containsPower || drivenHigh) {
            resolved = NodeState.HIGH;
        } else if (pullUp) {
            resolved = NodeState.HIGH;
        } else if (pullDown) {
            resolved = NodeState.LOW;
        } else {
            // Dynamic NMOS nodes retain charge. The boolean model approximates that by
            // preserving HIGH if any node in the isolated group was previously HIGH.
            resolved = retainedHigh ? NodeState.HIGH : NodeState.LOW;
        }

        for (int i = 0; i < groupSize; i++) {
            int node = group[i];
            NodeState target = node == netlist.vccNode ? NodeState.HIGH
                    : node == netlist.vssNode ? NodeState.LOW
                    : resolved;
            if (state[node] != target) {
                state[node] = target;
                gateChanged(node);
            }
        }
    }

    private void gateChanged(int node) {
        for (int transistor : netlist.transistorsByGate[node]) {
            boolean next = state[node] == NodeState.HIGH;
            if (transistorOn[transistor] != next) {
                transistorOn[transistor] = next;
                enqueue(netlist.terminalA[transistor]);
                enqueue(netlist.terminalB[transistor]);
            }
        }
    }

    private void enqueue(int node) {
        if (!queued.get(node)) {
            queued.set(node);
            dirty.addLast(node);
        }
    }

    private int nextGeneration() {
        if (visitGeneration == Integer.MAX_VALUE) {
            Arrays.fill(visitMark, 0);
            visitGeneration = 1;
        }
        return visitGeneration++;
    }

    private void requireNode(int node) {
        if (node < 0 || node >= netlist.nodeCount) {
            throw new IndexOutOfBoundsException("Node " + node);
        }
    }
}
