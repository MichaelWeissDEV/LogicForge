package dev.logicforge.transistor.visual6502;

import dev.logicforge.transistor.SwitchTransistor;
import dev.logicforge.transistor.TransistorNetlist;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports the simulation-relevant subset of Visual6502's JavaScript data files.
 *
 * <p>No JavaScript engine is used and no source code is executed. The parser accepts only
 * the static literal formats needed for {@code transdefs.js}, {@code segdefs.js} and
 * {@code nodenames.js}. Geometry in the first two files is deliberately ignored by the
 * headless solver.</p>
 */
public final class Visual6502Importer {
    private static final Pattern TRANSISTOR = Pattern.compile(
            "\\[\\s*'([^']+)'\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)");
    private static final Pattern SEGMENT = Pattern.compile(
            "\\[\\s*(-?\\d+)\\s*,\\s*'([+-])'");
    private static final Pattern NODE_NAME = Pattern.compile(
            "(?m)^\\s*(?:\\\"([^\\\"]+)\\\"|'([^']+)'|([A-Za-z0-9_.$#~/-]+))\\s*:\\s*(-?\\d+)\\s*,?");

    private Visual6502Importer() { }

    public static ImportResult importFiles(Path segdefs, Path transdefs, Path nodenames) throws IOException {
        return importStrings(
                Files.readString(segdefs, StandardCharsets.UTF_8),
                Files.readString(transdefs, StandardCharsets.UTF_8),
                Files.readString(nodenames, StandardCharsets.UTF_8));
    }

    public static ImportResult importStrings(String segdefs, String transdefs, String nodenames) {
        Map<String, Integer> names = parseNodeNames(nodenames);
        List<SwitchTransistor> transistors = parseTransistors(transdefs);
        Map<Integer, Boolean> pullUpsByNode = parsePullUps(segdefs);

        int maxNode = -1;
        for (int node : names.values()) {
            maxNode = Math.max(maxNode, node);
        }
        for (SwitchTransistor t : transistors) {
            maxNode = Math.max(maxNode, Math.max(t.gate(), Math.max(t.terminalA(), t.terminalB())));
        }
        for (int node : pullUpsByNode.keySet()) {
            maxNode = Math.max(maxNode, node);
        }
        if (maxNode < 0) {
            throw new IllegalArgumentException("Visual6502 data contains no nodes");
        }

        Integer vcc = names.get("vcc");
        Integer vss = names.get("vss");
        if (vcc == null || vcc < 0 || vss == null || vss < 0) {
            throw new IllegalArgumentException("nodenames.js must define usable vcc and vss nodes");
        }

        int nodeCount = maxNode + 1;
        boolean[] pullUps = new boolean[nodeCount];
        boolean[] pullDowns = new boolean[nodeCount];
        pullUpsByNode.forEach((node, present) -> pullUps[node] |= present);

        TransistorNetlist netlist = new TransistorNetlist(nodeCount, vcc, vss,
                pullUps, pullDowns, transistors, names);
        return new ImportResult(netlist, transistors.size(), countTrue(pullUps), names.size());
    }

    public static Map<String, Integer> parseNodeNames(String source) {
        Map<String, Integer> names = new LinkedHashMap<>();
        Matcher matcher = NODE_NAME.matcher(source);
        while (matcher.find()) {
            String name = matcher.group(1) != null ? matcher.group(1)
                    : matcher.group(2) != null ? matcher.group(2)
                    : matcher.group(3);
            names.put(name, Integer.parseInt(matcher.group(4)));
        }
        if (names.isEmpty()) {
            throw new IllegalArgumentException("No Visual6502 node names found");
        }
        return names;
    }

    public static List<SwitchTransistor> parseTransistors(String source) {
        List<SwitchTransistor> result = new ArrayList<>();
        Matcher matcher = TRANSISTOR.matcher(source);
        while (matcher.find()) {
            int gate = Integer.parseInt(matcher.group(2));
            int a = Integer.parseInt(matcher.group(3));
            int b = Integer.parseInt(matcher.group(4));
            if (gate >= 0 && a >= 0 && b >= 0) {
                result.add(new SwitchTransistor(matcher.group(1), gate, a, b));
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("No Visual6502 transistor definitions found");
        }
        return result;
    }

    /** Returns one entry for every node encountered in segdefs; value says whether it has a pull-up. */
    public static Map<Integer, Boolean> parsePullUps(String source) {
        Map<Integer, Boolean> result = new LinkedHashMap<>();
        Matcher matcher = SEGMENT.matcher(source);
        while (matcher.find()) {
            int node = Integer.parseInt(matcher.group(1));
            if (node >= 0) {
                boolean hasPullUp = matcher.group(2).equals("+");
                result.merge(node, hasPullUp, Boolean::logicalOr);
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("No Visual6502 segment definitions found");
        }
        return result;
    }

    private static int countTrue(boolean[] values) {
        int count = 0;
        for (boolean value : values) {
            if (value) count++;
        }
        return count;
    }

    public record ImportResult(
            TransistorNetlist netlist,
            int transistorCount,
            int pullUpNodeCount,
            int namedNodeCount) {
    }
}
