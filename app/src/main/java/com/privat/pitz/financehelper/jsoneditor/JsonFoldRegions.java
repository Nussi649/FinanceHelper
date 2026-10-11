package com.privat.pitz.financehelper.jsoneditor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finds the {@code {...}} / {@code [...]} blocks of a JSON text, for code folding.
 *
 * <p>Works on the raw characters rather than a parsed tree: the editor must keep working while the
 * user's text is half-edited and not valid JSON. Brackets inside string literals are ignored
 * (honouring backslash escapes). A string never spans a line break, so a stray quote typed by the
 * user only derails the rest of that line, not the whole document. Unmatched or mismatched
 * brackets simply yield no region.
 *
 * <p>Besides the offsets each region records the JSON key it is the value of and, for objects,
 * its direct {@code "name"} string value; that is what lets the per-account entry point find an
 * account block without a second parse.
 */
public final class JsonFoldRegions {

    private JsonFoldRegions() { }

    public static final class Region {
        public final int openOffset;
        /** Offset of the matching closing bracket. */
        public int closeOffset = -1;
        public final int startLine;
        public int endLine;
        /** Number of enclosing blocks; the top-level value has depth 0. */
        public final int depth;
        public final boolean array;
        /** Key of the property this block is the value of; null for array elements and the root. */
        public final String key;
        /** Direct "name" string property of an object block, else null. */
        public String name;
        public final Region parent;

        Region(int openOffset, int startLine, int depth, boolean array, String key, Region parent) {
            this.openOffset = openOffset;
            this.startLine = startLine;
            this.depth = depth;
            this.array = array;
            this.key = key;
            this.parent = parent;
        }

        public boolean isMultiline() {
            return endLine != startLine;
        }
    }

    public static final class Scan {
        /** Closed regions in order of their opening bracket (outer before inner). */
        public final List<Region> regions;
        /** Offset of the first character of every line; always has at least one entry. */
        public final int[] lineStarts;

        Scan(List<Region> regions, int[] lineStarts) {
            this.regions = regions;
            this.lineStarts = lineStarts;
        }

        /** The zero-based line containing {@code offset}. */
        public int lineOf(int offset) {
            int lo = 0, hi = lineStarts.length - 1;
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (lineStarts[mid] <= offset)
                    lo = mid;
                else
                    hi = mid - 1;
            }
            return lo;
        }
    }

    public static Scan scan(CharSequence text) {
        int n = text.length();
        List<Region> all = new ArrayList<>();
        List<Region> stack = new ArrayList<>();
        // current property key per open block, parallel to stack
        List<String> keys = new ArrayList<>();
        int[] lineStarts = new int[64];
        int lineCount = 1;
        int line = 0;

        boolean inString = false;
        boolean escaped = false;
        int stringStart = 0;

        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                line++;
                if (lineCount == lineStarts.length)
                    lineStarts = Arrays.copyOf(lineStarts, lineCount * 2);
                lineStarts[lineCount++] = i + 1;
                inString = false;
                escaped = false;
                continue;
            }
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                    handleString(text, stringStart + 1, i, stack, keys);
                }
                continue;
            }
            switch (c) {
                case '"':
                    inString = true;
                    stringStart = i;
                    break;
                case '{':
                case '[': {
                    Region parent = stack.isEmpty() ? null : stack.get(stack.size() - 1);
                    String key = (parent != null && !parent.array) ? keys.get(keys.size() - 1) : null;
                    Region r = new Region(i, line, stack.size(), c == '[', key, parent);
                    all.add(r);
                    stack.add(r);
                    keys.add(null);
                    break;
                }
                case '}':
                case ']': {
                    if (stack.isEmpty())
                        break;
                    Region top = stack.get(stack.size() - 1);
                    if (top.array != (c == ']'))
                        break; // mismatched closer: ignore it rather than guess
                    top.closeOffset = i;
                    top.endLine = line;
                    stack.remove(stack.size() - 1);
                    keys.remove(keys.size() - 1);
                    break;
                }
                case ',':
                    if (!stack.isEmpty())
                        keys.set(keys.size() - 1, null);
                    break;
                default:
                    break;
            }
        }

        List<Region> closed = new ArrayList<>(all.size());
        for (Region r : all) {
            if (r.closeOffset >= 0)
                closed.add(r);
        }
        return new Scan(closed, Arrays.copyOf(lineStarts, lineCount));
    }

    /** A finished string literal [from, to): is it a key (followed by ':') or a value? */
    private static void handleString(CharSequence text, int from, int to,
                                     List<Region> stack, List<String> keys) {
        if (stack.isEmpty())
            return;
        int top = stack.size() - 1;
        if (stack.get(top).array)
            return;
        int j = to + 1;
        while (j < text.length() && (text.charAt(j) == ' ' || text.charAt(j) == '\t' || text.charAt(j) == '\r'))
            j++;
        boolean isKey = j < text.length() && text.charAt(j) == ':';
        if (isKey)
            keys.set(top, unescape(text, from, to));
        else if ("name".equals(keys.get(top)))
            stack.get(top).name = unescape(text, from, to);
    }

    static String unescape(CharSequence text, int from, int to) {
        StringBuilder sb = new StringBuilder(to - from);
        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (c != '\\' || i + 1 >= to) {
                sb.append(c);
                continue;
            }
            char e = text.charAt(++i);
            switch (e) {
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case 'r': sb.append('\r'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'u':
                    if (i + 4 < to) {
                        try {
                            sb.append((char) Integer.parseInt(text.subSequence(i + 1, i + 5).toString(), 16));
                            i += 4;
                            break;
                        } catch (NumberFormatException ignored) {
                            // not a valid escape: keep the 'u' literally
                        }
                    }
                    sb.append('u');
                    break;
                default: sb.append(e); break; // \" \\ \/
            }
        }
        return sb.toString();
    }
}
