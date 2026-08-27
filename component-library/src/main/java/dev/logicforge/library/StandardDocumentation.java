package dev.logicforge.library;

import dev.logicforge.circuit.component.ComponentDocumentation;
import dev.logicforge.circuit.component.TruthTableDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Canonical educational tables for foundational components. */
final class StandardDocumentation {
    private StandardDocumentation() {
    }

    static ComponentDocumentation forId(String id, String summary) {
        TruthTableDefinition table = switch (id) {
            case "logic.not" -> table("A|Y", "0|1", "1|0", "X|X", "Z|X");
            case "logic.and" -> binary("AND", "0", "0", "0", "1");
            case "logic.nand" -> binary("NAND", "1", "1", "1", "0");
            case "logic.or" -> binary("OR", "0", "1", "1", "1");
            case "logic.nor" -> binary("NOR", "1", "0", "0", "0");
            case "logic.xor" -> binary("XOR", "0", "1", "1", "0");
            case "logic.xnor" -> binary("XNOR", "1", "0", "0", "1");
            case "arithmetic.half_adder" -> table("A,B|SUM,CARRY",
                    "0,0|0,0", "0,1|1,0", "1,0|1,0", "1,1|0,1");
            case "arithmetic.full_adder" -> table("A,B,CIN|SUM,COUT",
                    "0,0,0|0,0", "0,0,1|1,0", "0,1,0|1,0", "0,1,1|0,1",
                    "1,0,0|1,0", "1,0,1|0,1", "1,1,0|0,1", "1,1,1|1,1");
            case "routing.mux2" -> table("SEL,IN0,IN1|OUT",
                    "0,0,-|0", "0,1,-|1", "1,-,0|0", "1,-,1|1");
            case "routing.decoder" -> table("ENABLE,SELECT|OUTPUT",
                    "0,-|all 0", "1,n|only n is 1");
            case "sequential.sr_latch" -> table("S,R,Q(t)|Q(t+1),Q'(t+1)",
                    "0,0,0|0,1", "0,0,1|1,0", "1,0,-|1,0", "0,1,-|0,1",
                    "1,1,-|invalid,invalid");
            case "sequential.d_ff" -> table("CLK edge,D|Q(t+1)",
                    "rising,0|0", "rising,1|1", "none,-|Q(t)");
            default -> null;
        };
        if (table == null) {
            return ComponentDocumentation.EMPTY;
        }
        String invalid = id.equals("sequential.sr_latch")
                ? "S=1 and R=1 is the forbidden asserted state." : "";
        String timing = id.equals("sequential.d_ff")
                ? "D must satisfy setup and hold around the selected clock edge." : "";
        return new ComponentDocumentation(summary, summary, Optional.of(table), timing, invalid,
                "Four-state simulation treats Z used as a gate input conservatively as X.");
    }

    private static TruthTableDefinition binary(String output, String... values) {
        return table("A,B|" + output, "0,0|" + values[0], "0,1|" + values[1],
                "1,0|" + values[2], "1,1|" + values[3]);
    }

    private static TruthTableDefinition table(String header, String... rows) {
        String[] halves = header.split("\\|", -1);
        List<String> inputs = cells(halves[0]);
        List<String> outputs = cells(halves[1]);
        ArrayList<TruthTableDefinition.Row> result = new ArrayList<>();
        for (String row : rows) {
            String[] values = row.split("\\|", -1);
            result.add(new TruthTableDefinition.Row(cells(values[0]), cells(values[1])));
        }
        return new TruthTableDefinition(inputs, outputs, result);
    }

    private static List<String> cells(String text) {
        return List.of(text.split(",", -1));
    }
}
