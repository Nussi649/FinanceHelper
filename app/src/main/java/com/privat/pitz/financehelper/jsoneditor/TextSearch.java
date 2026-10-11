package com.privat.pitz.financehelper.jsoneditor;

/** Case-insensitive substring search helpers for the editor's search bar. */
public final class TextSearch {

    private TextSearch() { }

    /**
     * Start offsets of all non-overlapping matches of {@code query} in {@code text}.
     *
     * <p>Compares with {@code regionMatches(ignoreCase)} instead of lower-casing both strings:
     * lower-casing can change a string's length (e.g. "İ"), which would shift every offset.
     */
    public static int[] findAll(String text, String query) {
        int qlen = query.length();
        if (qlen == 0)
            return new int[0];
        int[] found = new int[16];
        int count = 0;
        int last = text.length() - qlen;
        for (int i = 0; i <= last; ) {
            if (text.regionMatches(true, i, query, 0, qlen)) {
                if (count == found.length)
                    found = java.util.Arrays.copyOf(found, count * 2);
                found[count++] = i;
                i += qlen;
            } else {
                i++;
            }
        }
        return java.util.Arrays.copyOf(found, count);
    }

    /**
     * Picks the match to jump to, wrapping around at either end.
     *
     * @param inclusive for forward search, whether a match starting exactly at {@code ref} counts
     * @return an index into {@code starts}, or -1 if there are no matches
     */
    public static int pick(int[] starts, int ref, boolean forward, boolean inclusive) {
        if (starts.length == 0)
            return -1;
        if (forward) {
            for (int i = 0; i < starts.length; i++) {
                if (starts[i] > ref || (inclusive && starts[i] == ref))
                    return i;
            }
            return 0;
        }
        for (int i = starts.length - 1; i >= 0; i--) {
            if (starts[i] < ref)
                return i;
        }
        return starts.length - 1;
    }
}
