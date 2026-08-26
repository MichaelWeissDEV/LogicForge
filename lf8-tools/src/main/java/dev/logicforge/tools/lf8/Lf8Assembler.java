package dev.logicforge.tools.lf8;

import dev.logicforge.processor.lf8.Lf8Instruction;
import dev.logicforge.processor.lf8.Lf8Instruction.OperandForm;
import dev.logicforge.processor.lf8.Lf8Isa;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A two-pass assembler for {@link Lf8Isa}. Source is a sequence of lines, each an optional
 * {@code label:}, then an optional instruction or directive, then an optional {@code ;} comment.
 *
 * <p>Registers are written {@code R0}-{@code R7}. Numeric literals are decimal by default, or
 * {@code 0x}/{@code 0b} prefixed for hex/binary. Directives: {@code .org <address>} moves the
 * output cursor, {@code .byte v1, v2, ...} emits raw bytes, {@code .word v1, v2, ...} emits
 * 16-bit little-endian values (each either a numeric literal or a label), and
 * {@code .ascii "text"} emits a string's bytes (supports {@code \\n \\t \\0 \\" \\\\} escapes).
 *
 * <p>Pass one walks every line to resolve label addresses and directive lengths without
 * resolving operands; pass two re-walks the same parsed statements to emit bytes, at which
 * point every label is known. This is why a forward reference to a label (an address used
 * before its {@code label:} definition) works.
 */
public final class Lf8Assembler {

    private static final Map<String, Lf8Instruction> MNEMONICS = buildMnemonicTable();
    private static final Pattern LABEL_DEFINITION =
            Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*):(.*)$", Pattern.DOTALL);
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private Lf8Assembler() {
    }

    /** Assembles {@code source} into a flat memory image starting at address 0. */
    public static int[] assemble(String source) {
        Map<String, Integer> symbols = new LinkedHashMap<>();
        List<Statement> statements = new ArrayList<>();

        int address = 0;
        int lineNumber = 0;
        for (String rawLine : source.split("\n", -1)) {
            lineNumber++;
            String content = stripComment(rawLine).trim();
            if (content.isEmpty()) {
                continue;
            }

            var labelMatch = LABEL_DEFINITION.matcher(content);
            if (labelMatch.matches()) {
                String label = labelMatch.group(1);
                if (symbols.containsKey(label)) {
                    throw new Lf8AssemblyException(lineNumber, rawLine,
                            "duplicate label '" + label + "'");
                }
                symbols.put(label, address);
                content = labelMatch.group(2).trim();
            }
            if (content.isEmpty()) {
                continue;
            }

            if (startsWithWord(content, ".org")) {
                long target = parseNumber(argumentOf(content, ".org"), lineNumber, rawLine);
                if (target < 0 || target > 0xffff) {
                    throw new Lf8AssemblyException(lineNumber, rawLine,
                            "address '" + target + "' out of range (0-65535)");
                }
                address = (int) target;
                continue;
            }

            Statement statement = content.startsWith(".")
                    ? parseDirective(lineNumber, rawLine, content, address)
                    : parseInstruction(lineNumber, rawLine, content, address);
            statements.add(statement);
            address = statement.address() + statement.length();
        }

        int size = 0;
        for (Statement statement : statements) {
            size = Math.max(size, statement.address() + statement.length());
        }
        int[] memory = new int[size];
        for (Statement statement : statements) {
            statement.emit(memory, symbols);
        }
        return memory;
    }

    private static Statement parseInstruction(int lineNumber, String rawLine, String content,
                                              int address) {
        int split = firstWhitespace(content);
        String mnemonicToken = split < 0 ? content : content.substring(0, split);
        String rest = split < 0 ? "" : content.substring(split + 1).trim();
        Lf8Instruction instruction = MNEMONICS.get(mnemonicToken.toUpperCase(Locale.ROOT));
        if (instruction == null) {
            throw new Lf8AssemblyException(lineNumber, rawLine,
                    "unknown mnemonic '" + mnemonicToken + "'");
        }
        List<String> operands = splitOperands(rest, lineNumber, rawLine);
        if (operands.size() != instruction.operands().size()) {
            throw new Lf8AssemblyException(lineNumber, rawLine,
                    "expected " + instruction.operands().size() + " operand(s) for "
                            + instruction.mnemonic() + ", found " + operands.size());
        }
        return new InstructionStatement(lineNumber, rawLine, address, instruction, operands);
    }

    private static Statement parseDirective(int lineNumber, String rawLine, String content,
                                            int address) {
        int split = firstWhitespace(content);
        String name = (split < 0 ? content : content.substring(0, split)).toLowerCase(Locale.ROOT);
        String rest = split < 0 ? "" : content.substring(split + 1).trim();
        return switch (name) {
            case ".byte" -> new ByteDirective(lineNumber, rawLine, address,
                    splitOperands(rest, lineNumber, rawLine));
            case ".word" -> new WordDirective(lineNumber, rawLine, address,
                    splitOperands(rest, lineNumber, rawLine));
            case ".ascii" -> new AsciiDirective(lineNumber, rawLine, address,
                    parseStringLiteral(rest, lineNumber, rawLine));
            default -> throw new Lf8AssemblyException(lineNumber, rawLine,
                    "unknown directive '" + name + "'");
        };
    }

    // ---- statements -------------------------------------------------------------------

    private interface Statement {
        int address();

        int length();

        void emit(int[] memory, Map<String, Integer> symbols);
    }

    private record InstructionStatement(int lineNumber, String rawLine, int address,
                                        Lf8Instruction instruction, List<String> operands)
            implements Statement {
        @Override
        public int length() {
            return instruction.length();
        }

        @Override
        public void emit(int[] memory, Map<String, Integer> symbols) {
            int pos = address;
            memory[pos++] = instruction.opcode();
            List<OperandForm> forms = instruction.operands();
            for (int i = 0; i < forms.size(); i++) {
                String token = operands.get(i);
                switch (forms.get(i)) {
                    case REGISTER -> memory[pos++] = parseRegister(token, lineNumber, rawLine);
                    case IMMEDIATE8 -> memory[pos++] = parseImmediate8(token, lineNumber, rawLine);
                    case ADDRESS16 -> {
                        int value = resolveAddress16(token, lineNumber, rawLine, symbols);
                        memory[pos++] = value & 0xff;
                        memory[pos++] = (value >>> 8) & 0xff;
                    }
                }
            }
        }
    }

    private record ByteDirective(int lineNumber, String rawLine, int address,
                                 List<String> tokens) implements Statement {
        @Override
        public int length() {
            return tokens.size();
        }

        @Override
        public void emit(int[] memory, Map<String, Integer> symbols) {
            int pos = address;
            for (String token : tokens) {
                long value = parseNumber(token, lineNumber, rawLine);
                if (value < 0 || value > 0xff) {
                    throw new Lf8AssemblyException(lineNumber, rawLine,
                            "byte value '" + token + "' out of range (0-255)");
                }
                memory[pos++] = (int) value;
            }
        }
    }

    private record WordDirective(int lineNumber, String rawLine, int address,
                                 List<String> tokens) implements Statement {
        @Override
        public int length() {
            return tokens.size() * 2;
        }

        @Override
        public void emit(int[] memory, Map<String, Integer> symbols) {
            int pos = address;
            for (String token : tokens) {
                int value = resolveAddress16(token, lineNumber, rawLine, symbols);
                memory[pos++] = value & 0xff;
                memory[pos++] = (value >>> 8) & 0xff;
            }
        }
    }

    private record AsciiDirective(int lineNumber, String rawLine, int address, String text)
            implements Statement {
        @Override
        public int length() {
            return text.length();
        }

        @Override
        public void emit(int[] memory, Map<String, Integer> symbols) {
            int pos = address;
            for (int i = 0; i < text.length(); i++) {
                memory[pos++] = text.charAt(i) & 0xff;
            }
        }
    }

    // ---- lexical helpers ----------------------------------------------------------------

    private static String stripComment(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inString = !inString;
            } else if (c == ';' && !inString) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static int firstWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean startsWithWord(String content, String word) {
        if (!content.regionMatches(true, 0, word, 0, word.length())) {
            return false;
        }
        return content.length() == word.length()
                || Character.isWhitespace(content.charAt(word.length()));
    }

    private static String argumentOf(String content, String directive) {
        return content.length() == directive.length()
                ? "" : content.substring(directive.length()).trim();
    }

    private static List<String> splitOperands(String rest, int lineNumber, String rawLine) {
        if (rest.isEmpty()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String part : rest.split(",", -1)) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                throw new Lf8AssemblyException(lineNumber, rawLine, "invalid syntax: empty operand");
            }
            tokens.add(trimmed);
        }
        return tokens;
    }

    private static String parseStringLiteral(String rest, int lineNumber, String rawLine) {
        if (rest.length() < 2 || rest.charAt(0) != '"' || rest.charAt(rest.length() - 1) != '"') {
            throw new Lf8AssemblyException(lineNumber, rawLine, "expected a quoted string for .ascii");
        }
        String inner = rest.substring(1, rest.length() - 1);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '\\' && i + 1 < inner.length()) {
                char next = inner.charAt(++i);
                result.append(switch (next) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case '0' -> '\0';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> next;
                });
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static int parseRegister(String token, int lineNumber, String rawLine) {
        if (token.length() >= 2 && (token.charAt(0) == 'R' || token.charAt(0) == 'r')) {
            String digits = token.substring(1);
            if (!digits.isEmpty() && digits.chars().allMatch(Character::isDigit)) {
                int n = Integer.parseInt(digits);
                if (n <= 7) {
                    return n;
                }
            }
        }
        throw new Lf8AssemblyException(lineNumber, rawLine,
                "invalid register '" + token + "' (must be R0-R7)");
    }

    private static int parseImmediate8(String token, int lineNumber, String rawLine) {
        long value = parseNumber(token, lineNumber, rawLine);
        if (value < 0 || value > 0xff) {
            throw new Lf8AssemblyException(lineNumber, rawLine,
                    "immediate '" + token + "' out of range (0-255)");
        }
        return (int) value;
    }

    private static int resolveAddress16(String token, int lineNumber, String rawLine,
                                        Map<String, Integer> symbols) {
        if (IDENTIFIER.matcher(token).matches()) {
            Integer resolved = symbols.get(token);
            if (resolved == null) {
                throw new Lf8AssemblyException(lineNumber, rawLine, "undefined label '" + token + "'");
            }
            return resolved;
        }
        long value = parseNumber(token, lineNumber, rawLine);
        if (value < 0 || value > 0xffff) {
            throw new Lf8AssemblyException(lineNumber, rawLine,
                    "address '" + token + "' out of range (0-65535)");
        }
        return (int) value;
    }

    private static long parseNumber(String token, int lineNumber, String rawLine) {
        try {
            if (token.length() > 2 && token.charAt(0) == '0'
                    && (token.charAt(1) == 'x' || token.charAt(1) == 'X')) {
                return Long.parseLong(token.substring(2), 16);
            }
            if (token.length() > 2 && token.charAt(0) == '0'
                    && (token.charAt(1) == 'b' || token.charAt(1) == 'B')) {
                return Long.parseLong(token.substring(2), 2);
            }
            return Long.parseLong(token, 10);
        } catch (NumberFormatException failure) {
            throw new Lf8AssemblyException(lineNumber, rawLine,
                    "invalid numeric literal '" + token + "'");
        }
    }

    private static Map<String, Lf8Instruction> buildMnemonicTable() {
        Map<String, Lf8Instruction> table = new LinkedHashMap<>();
        for (Lf8Instruction instruction : Lf8Isa.instructions()) {
            table.put(instruction.mnemonic().toUpperCase(Locale.ROOT), instruction);
        }
        return Map.copyOf(table);
    }
}
