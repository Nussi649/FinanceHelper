package com.privat.pitz.financehelper.jsoneditor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Type-specific labels for folded blocks, e.g. {@code " Bargeld · 8 Einträge }"} for an account
 * or {@code " … 10 Konten ]"} for the assets array.
 *
 * <p>Pure Java so it can be unit-tested on the JVM; that is why the German words live here as
 * constants (the app UI is German only) instead of in Android resources. The hidden text is
 * parsed with a tiny strict parser (the user may have typed invalid JSON before folding); on any
 * problem {@link #label} returns the generic fallback and never throws.
 *
 * <p>A label is only display text. {@link FoldMath#extract} locates placeholders by their span
 * object and restores their stored hidden text, never by looking at the label text, so a label
 * can never be confused with document content.
 */
public final class FoldLabels {

    private FoldLabels() { }

    // ---- German UI words: the one place to change them -------------------------------------
    private static final String[] ENTRY = {"Eintrag", "Einträge"};
    private static final String[] ACCOUNT = {"Konto", "Konten"};
    private static final String[] BUDGET = {"Budget", "Budgets"};
    private static final String[] SUB_BUDGET = {"Unterbudget", "Unterbudgets"};
    private static final String[] RECURRING = {"Dauerauftrag", "Daueraufträge"};
    // -----------------------------------------------------------------------------------------

    private static final int MAX_NAME = 40;

    /**
     * The text shown in place of {@code hidden} (everything after the opener up to and including
     * the closer). {@code key} is the JSON key the block is the value of, or null.
     */
    public static String label(String hidden, String key) {
        try {
            String s = specific(hidden, key);
            if (s != null)
                return s;
        } catch (RuntimeException | StackOverflowError ignored) {
            // unparsable or absurdly nested: use the generic label
        }
        return generic(hidden);
    }

    /** The generic label, e.g. {@code " … 42 ]"}; the count is the block's direct entries. */
    public static String generic(String hidden) {
        char closer = hidden.isEmpty() ? '?' : hidden.charAt(hidden.length() - 1);
        int entries = FoldMath.countEntries(hidden.isEmpty() ? hidden : hidden.substring(0, hidden.length() - 1));
        return " … " + entries + " " + closer;
    }

    private static String specific(String hidden, String key) {
        if (hidden.isEmpty())
            return null;
        char closer = hidden.charAt(hidden.length() - 1);
        if (closer == ']') {
            String[] words = key == null ? null : arrayWords(key);
            if (words == null)
                return null;
            Object v = new Parser('[' + hidden).parseAll();
            if (!(v instanceof List))
                return null;
            int n = ((List<?>) v).size();
            return " … " + n + " " + plural(n, words) + " ]";
        }
        if (closer != '}')
            return null;
        Object v = new Parser('{' + hidden).parseAll();
        if (!(v instanceof Map))
            return null;
        Map<?, ?> o = (Map<?, ?>) v;
        if (o.containsKey("name")) {
            if (!(o.get("name") instanceof String))
                return null;
            StringBuilder sb = new StringBuilder(" ").append(clean((String) o.get("name")));
            int tx = size(o.get("tx"));
            sb.append(" · ").append(tx).append(' ').append(plural(tx, ENTRY));
            int sub = size(o.get("sub_budgets"));
            if (sub > 0)
                sb.append(" · ").append(sub).append(' ').append(plural(sub, SUB_BUDGET));
            return sb.append(" }").toString();
        }
        Object a = o.get("a");
        if (o.get("d") instanceof String && (a instanceof String || a instanceof Raw))
            return " " + clean((String) o.get("d")) + " · " + clean(a.toString()) + " }";
        return null;
    }

    private static String[] arrayWords(String key) {
        switch (key) {
            case "assets": return ACCOUNT;
            case "budgets": return BUDGET;
            case "sub_budgets": return SUB_BUDGET;
            case "tx":
            case "income": return ENTRY;
            case "recur_tx": return RECURRING;
            default: return null;
        }
    }

    private static int size(Object o) {
        return o instanceof List ? ((List<?>) o).size() : 0;
    }

    private static String plural(int n, String[] words) {
        return n == 1 ? words[0] : words[1];
    }

    /** One line only (a line break would change the editor's line structure), and not endless. */
    private static String clean(String s) {
        StringBuilder sb = new StringBuilder(Math.min(s.length(), MAX_NAME + 1));
        for (int i = 0; i < s.length() && sb.length() < MAX_NAME; i++) {
            char c = s.charAt(i);
            sb.append(c < 0x20 || c == 0x2028 || c == 0x2029 || c == 0x85 ? ' ' : c);
        }
        if (s.length() > MAX_NAME)
            sb.append('…');
        return sb.toString();
    }

    /** A number or literal as written, kept apart from JSON strings. */
    private static final class Raw {
        private final String token;

        Raw(String token) {
            this.token = token;
        }

        @Override
        public String toString() {
            return token;
        }
    }

    /**
     * Strict minimal JSON parser: objects become LinkedHashMap, arrays ArrayList, strings String,
     * numbers/literals a Raw token. Throws RuntimeException on malformed input.
     */
    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        Object parseAll() {
            Object v = value();
            ws();
            if (i != s.length())
                throw new IllegalStateException("trailing text");
            return v;
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i)))
                i++;
        }

        private char next() {
            ws();
            if (i >= s.length())
                throw new IllegalStateException("unexpected end");
            return s.charAt(i);
        }

        private void expect(char c) {
            if (next() != c)
                throw new IllegalStateException("expected " + c);
            i++;
        }

        private Object value() {
            char c = next();
            if (c == '{') {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                if (next() == '}') {
                    i++;
                    return m;
                }
                while (true) {
                    if (next() != '"')
                        throw new IllegalStateException("key expected");
                    String k = string();
                    expect(':');
                    m.put(k, value());
                    char d = next();
                    i++;
                    if (d == '}')
                        return m;
                    if (d != ',')
                        throw new IllegalStateException("',' expected");
                }
            }
            if (c == '[') {
                i++;
                List<Object> l = new ArrayList<>();
                if (next() == ']') {
                    i++;
                    return l;
                }
                while (true) {
                    l.add(value());
                    char d = next();
                    i++;
                    if (d == ']')
                        return l;
                    if (d != ',')
                        throw new IllegalStateException("',' expected");
                }
            }
            if (c == '"')
                return string();
            int start = i;
            while (i < s.length() && ",}] \t\r\n".indexOf(s.charAt(i)) < 0)
                i++;
            if (i == start)
                throw new IllegalStateException("value expected");
            return new Raw(s.substring(start, i));
        }

        private String string() {
            int start = ++i; // after the opening quote
            boolean escaped = false;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '\n')
                    break;
                if (escaped)
                    escaped = false;
                else if (c == '\\')
                    escaped = true;
                else if (c == '"') {
                    String raw = JsonFoldRegions.unescape(s, start, i);
                    i++;
                    return raw;
                }
                i++;
            }
            throw new IllegalStateException("unterminated string");
        }
    }
}
