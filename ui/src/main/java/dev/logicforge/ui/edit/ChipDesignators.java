package dev.logicforge.ui.edit;

import dev.logicforge.circuit.document.CircuitDocument;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reference designators for physical chips: {@code U1}, {@code U2}, ... — one shared
 * sequence regardless of chip kind, since EDA-style per-category prefixes (memory vs.
 * logic vs. ...) would add complexity the editor has no use for.
 */
public final class ChipDesignators {

    private static final Pattern DESIGNATOR = Pattern.compile("U(\\d+)");

    private ChipDesignators() {
    }

    /** The next unused {@code U<n>} designator, given every chip already in the document. */
    public static String next(CircuitDocument document) {
        int highest = 0;
        for (var chip : document.chips()) {
            Matcher matcher = DESIGNATOR.matcher(chip.referenceDesignator());
            if (matcher.matches()) {
                highest = Math.max(highest, Integer.parseInt(matcher.group(1)));
            }
        }
        return "U" + (highest + 1);
    }
}
